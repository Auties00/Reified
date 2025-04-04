package it.auties.reified.annotation;

import com.google.auto.service.AutoService;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedCandidate;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.scanner.ArrayInitializationScanner;
import it.auties.reified.scanner.ClassInitializationScanner;
import it.auties.reified.scanner.ExtendedClassesScanner;
import it.auties.reified.scanner.MethodInvocationScanner;
import it.auties.reified.simplified.SimpleClasses;
import it.auties.reified.simplified.SimpleContext;
import it.auties.reified.simplified.SimpleMaker;
import it.auties.reified.simplified.SimpleTypes;
import it.auties.reified.util.DiagnosticHandlerWorker;
import it.auties.reified.util.IllegalReflection;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.lang.Boolean.parseBoolean;

@SupportedAnnotationTypes(Reified.PATH)
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@SupportedOptions("reified.debug")
@AutoService(Processor.class)
public class ReifiedProcessor extends AbstractProcessor {
    static {
        IllegalReflection.openJavac();
    }

    private SimpleTypes simpleTypes;
    private SimpleClasses simpleClasses;
    private SimpleMaker simpleMaker;
    private Trees trees;
    private RoundEnvironment environment;
    private DiagnosticHandlerWorker diagnosticHandlerWorker;
    private List<ReifiedDeclaration> reifiedDeclarations;
    private ListBuffer<ReifiedCall> reifiedResults;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            init(roundEnv);
            lookup();
            processing();
            return true;
        } catch (Throwable ex) {
            throw new RuntimeException("An exception occurred while compiling using reified", ex);
        }
    }

    private void init(RoundEnvironment environment) {
        var context = SimpleContext.resolveContext(processingEnv);
        var attr = Attr.instance(context);
        var enter = Enter.instance(context);
        var types = Types.instance(context);
        var treeMaker = TreeMaker.instance(context);
        var memberEnter = MemberEnter.instance(context);

        this.environment = environment;
        this.trees = JavacTrees.instance(context);
        this.simpleTypes = new SimpleTypes(processingEnv, types, attr, enter, memberEnter);
        this.simpleClasses = new SimpleClasses(simpleTypes);
        this.simpleMaker = new SimpleMaker(treeMaker, simpleTypes);
        this.diagnosticHandlerWorker = new DiagnosticHandlerWorker(attr);
    }

    private void lookup() {
        var candidates = findAnnotatedTrees();
        this.reifiedDeclarations = parseCandidates(candidates);
    }

    private List<ReifiedCandidate> findAnnotatedTrees() {
        return environment.getElementsAnnotatedWith(Reified.class)
                .stream()
                .map(this::findAnnotatedTree)
                .collect(List.collector())
                .reverse();
    }

    private ReifiedCandidate findAnnotatedTree(Element element) {
        var typeVariable = (Symbol.TypeVariableSymbol) element;
        var owner = typeVariable.getEnclosingElement();
        if(owner instanceof Symbol.ClassSymbol){
            var classSymbol = (Symbol.ClassSymbol) owner;
            var classPath = trees.getPath(classSymbol);
            return new ReifiedCandidate(typeVariable, (JCTree.JCClassDecl) classPath.getLeaf(), null);
        }

        if(owner instanceof Symbol.MethodSymbol){
            var methodSymbol = (Symbol.MethodSymbol) owner;
            var enclosingClassSymbol = (Symbol.ClassSymbol) methodSymbol.getEnclosingElement();
            var enclosingClassPath = trees.getPath(enclosingClassSymbol);
            var methodPath = trees.getPath(methodSymbol);
            return new ReifiedCandidate(typeVariable, (JCTree.JCClassDecl) enclosingClassPath.getLeaf(), (JCTree.JCMethodDecl) methodPath.getLeaf());
        }

        throw new IllegalArgumentException("Cannot find annotated tree, unknown owner: " + owner.getClass().getName());
    }

    private List<ReifiedDeclaration> parseCandidates(List<ReifiedCandidate> candidates) {
        return candidates.stream()
                .map(this::parseCandidate)
                .collect(List.collector());
    }

    private ReifiedDeclaration parseCandidate(ReifiedCandidate candidate) {
        return new ReifiedDeclaration(
                candidate.typeVariable(),
                candidate.enclosingClass(),
                simpleClasses.findRealAccess(candidate.enclosingClass(), candidate.enclosingMethod()),
                findMembers(candidate),
                candidate.hasClass()
        );
    }

    private List<JCTree.JCMethodDecl> findMembers(ReifiedCandidate candidate) {
        if (!candidate.hasClass()) {
            return List.of(candidate.enclosingMethod());
        }

        return simpleClasses.findConstructors(candidate.enclosingClass());
    }

    private void processing() {
        diagnosticHandlerWorker.useCachedHandler();
        this.reifiedResults = new ListBuffer<>();
        reifiedDeclarations.forEach(this::processTypeParameter);
        reifiedDeclarations.forEach(simpleMaker::processMembers);
        reifiedResults.forEach(this::applyParameter);
        reifiedDeclarations.forEach(this::processArrayInitializations);
        debug();
        diagnosticHandlerWorker.useJavacHandler();
        diagnosticHandlerWorker.reportErrors();
    }

    private void applyParameter(ReifiedCall call) {
        var literal = createClassLiteral(call.reifiedType(), call.enclosingClass(), call.enclosingMethod());
        switch (call.invocation().getTag()) {
            case NEWCLASS:
                var newClass = (JCTree.JCNewClass) call.invocation();
                newClass.args = newClass.args.prepend(literal);
                break;
            case APPLY:
                var methodInv = (JCTree.JCMethodInvocation) call.invocation();
                methodInv.args = methodInv.args.prepend(literal);
                break;
            default:
                throw new IllegalArgumentException("Cannot apply parameter to unknown tag: " + call.invocation().getTag().name());
        }
    }
    private void processChildClass(ReifiedDeclaration reifiedDeclaration) {
        var enclosingClass = reifiedDeclaration.enclosingClass();
        var childClasses = findChildClasses(enclosingClass);
        childClasses.forEach(childClass -> processChildClass(reifiedDeclaration, enclosingClass, childClass));
    }

    private void processChildClass(ReifiedDeclaration reifiedDeclaration, JCTree.JCClassDecl enclosingClass, JCTree.JCClassDecl childClass) {
        var type = findChildClassType(reifiedDeclaration, enclosingClass, childClass);
        var literal = createClassLiteral(type, childClass, null);
        addSuperParam(childClass, literal);
    }

    private void addSuperParam(JCTree.JCClassDecl childClass, JCTree.JCExpression literal) {
        simpleClasses.findConstructors(childClass)
                .stream()
                .map(constructor -> constructor.getBody().getStatements())
                .filter(stats -> !stats.isEmpty())
                .map(stats -> stats.head)
                .filter(TreeInfo::isSuperCall)
                .forEach(superCall -> simpleMaker.addSuperParam(literal, (JCTree.JCExpressionStatement) superCall));
    }

    private Type findChildClassType(ReifiedDeclaration reifiedDeclaration, JCTree.JCClassDecl enclosingClass, JCTree.JCClassDecl childClass) {
        var extendClause = childClass.getExtendsClause();
        switch (extendClause.getTag()) {
            case IDENT:
                return simpleTypes.erase(reifiedDeclaration.typeParameter());
            case TYPEAPPLY:
                var typeApply = (JCTree.JCTypeApply) extendClause;
                var types = simpleTypes.eraseTypeVariableFromTypeParameters(reifiedDeclaration.typeParameter(), enclosingClass.sym.getTypeParameters(), typeApply.getTypeArguments(), childClass);
                return Objects.requireNonNullElse(simpleTypes.commonType(types), simpleTypes.erase(reifiedDeclaration.typeParameter()));
            default:
                throw new IllegalArgumentException("Unsupported tag for child class type: " + extendClause.getTag().name());
        }
    }

    private List<JCTree.JCClassDecl> findChildClasses(JCTree.JCClassDecl superClass) {
        var scanner = new ExtendedClassesScanner(superClass, simpleTypes);
        return environment.getRootElements()
                .stream()
                .map(element -> simpleTypes.findClassEnv(trees.getTree(element)))
                .flatMap(Optional::stream)
                .map(env -> env.tree)
                .map(scanner::scan)
                .flatMap(Collection::stream)
                .collect(List.collector());
    }

    private void processTypeParameter(ReifiedDeclaration reifiedDeclaration) {
        if (!reifiedDeclaration.isClass()) {
            processMethodParameter(reifiedDeclaration);
            return;
        }

        processClassParameter(reifiedDeclaration);
        processChildClass(reifiedDeclaration);
    }

    private void processArrayInitializations(ReifiedDeclaration reifiedDeclaration) {
        var arrayInitializationScanner = new ArrayInitializationScanner(reifiedDeclaration, simpleClasses, simpleTypes);
        findCompilationUnits(reifiedDeclaration)
                .stream()
                .map(arrayInitializationScanner::scan)
                .flatMap(Collection::stream)
                .forEach(simpleMaker::processArrayInitialization);
    }

    private void processMethodParameter(ReifiedDeclaration reifiedDeclaration) {
        var methodScanner = new MethodInvocationScanner(reifiedDeclaration, simpleClasses, simpleTypes);
        findCompilationUnits(reifiedDeclaration)
                .stream()
                .map(methodScanner::scan)
                .flatMap(Collection::stream)
                .peek(methodInvocation -> methodInvocation.setReifiedType(simpleTypes.inferReifiedType(methodInvocation)))
                .forEach(reifiedResults::add);
    }

    private void processClassParameter(ReifiedDeclaration reifiedDeclaration) {
        var classScanner = new ClassInitializationScanner(reifiedDeclaration, simpleClasses, simpleTypes);
        findCompilationUnits(reifiedDeclaration)
                .stream()
                .map(classScanner::scan)
                .flatMap(Collection::stream)
                .peek(classInit -> classInit.setReifiedType(simpleTypes.inferReifiedType(classInit)))
                .forEach(reifiedResults::add);
    }

    public JCTree.JCExpression createClassLiteral(Type type, JCTree.JCClassDecl clazz, JCTree.JCMethodDecl method) {
        if (!simpleTypes.generic(type)) {
            return simpleMaker.classLiteral(type);
        }

        var typeSymbol = (Symbol.TypeVariableSymbol) type.asElement().baseSymbol();
        if (!simpleTypes.reified(typeSymbol)) {
            processTypeParameter(typeSymbol, clazz, method);
        }

        var name = type.asElement().getSimpleName();
        var enclosing = typeSymbol.getEnclosingElement();
        if(enclosing instanceof Symbol.ClassSymbol){
            return simpleMaker.createGenericClassLiteral(clazz, name);
        }

        if(enclosing instanceof Symbol.MethodSymbol){
            return simpleMaker.createGenericMethodLiteral(method, name);
        }

        throw new IllegalArgumentException("Cannot create class literal, unknown type symbol owner tag: " + enclosing.getClass().getName());
    }

    private void processTypeParameter(Symbol.TypeVariableSymbol typeSymbol, JCTree.JCClassDecl clazz, JCTree.JCMethodDecl method) {
        var enclosingMethod = typeSymbol.getEnclosingElement() instanceof Symbol.ClassSymbol ? null : method;
        var candidate = new ReifiedCandidate(typeSymbol, clazz, enclosingMethod);
        var declaration = parseCandidate(candidate);
        processTypeParameter(declaration);
        simpleMaker.processMembers(declaration);
    }

    private List<JCTree> findCompilationUnits() {
        return environment.getRootElements()
                .stream()
                .map(element -> simpleTypes.findClassEnv(trees.getTree(element)))
                .flatMap(Optional::stream)
                .map(env -> env.tree)
                .collect(List.collector());
    }

    private List<JCTree> findCompilationUnits(ReifiedDeclaration reifiedDeclaration) {
        return environment.getRootElements()
                .stream()
                .map(element -> simpleTypes.findClassEnv(trees.getTree(element)))
                .flatMap(Optional::stream)
                .filter(unit -> checkClassScope(reifiedDeclaration, unit))
                .map(env -> env.tree)
                .collect(List.collector());
    }

    private boolean checkClassScope(ReifiedDeclaration reifiedDeclaration, Env<AttrContext> unit) {
        var paramEnv = simpleTypes.findClassEnv(reifiedDeclaration.enclosingClass());
        switch (reifiedDeclaration.modifier()) {
            case PUBLIC:
                return true;
            case PRIVATE:
                return Objects.equals(unit.toplevel, paramEnv.toplevel);
            case PROTECTED:
            case PACKAGE_PRIVATE:
                return Objects.equals(unit.toplevel.packge, paramEnv.toplevel.packge);
            default:
                throw new IllegalArgumentException("Cannot check class scope, unknown modifier: " + reifiedDeclaration.modifier());
        }
    }

    private void debug(){
        if(!parseBoolean(processingEnv.getOptions().get("reified.debug"))){
            return;
        }

        System.err.println("Reified declarations:");
        reifiedDeclarations.forEach(System.err::println);
    }
}
