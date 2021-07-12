package it.auties.reified.processor;

import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import it.auties.reified.annotation.Reified;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedCandidate;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.scanner.ClassInitializationScanner;
import it.auties.reified.scanner.MethodInvocationScanner;
import it.auties.reified.simplified.SimpleClasses;
import it.auties.reified.simplified.SimpleContext;
import it.auties.reified.simplified.SimpleMethods;
import it.auties.reified.simplified.SimpleTypes;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes(Reified.PATH)
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class ReifiedProcessor extends AbstractProcessor {
    private SimpleTypes simpleTypes;
    private SimpleClasses simpleClasses;
    private SimpleMethods simpleMethods;
    private Trees trees;
    private TreeMaker treeMaker;
    private RoundEnvironment environment;
    private List<ReifiedDeclaration> reifiedDeclarations;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        init(roundEnv);
        lookup();
        processing();
        return true;
    }

    private void init(RoundEnvironment environment) {
        var context = SimpleContext.resolveContext(processingEnv);
        var enter = Enter.instance(context);
        var attr = Attr.instance(context);
        var types = Types.instance(context);
        var names = Names.instance(context);
        var resolve = Resolve.instance(context);

        this.trees = JavacTrees.instance(context);
        this.treeMaker = TreeMaker.instance(context);
        this.simpleTypes = new SimpleTypes(processingEnv, types, attr, enter);
        this.simpleClasses = new SimpleClasses(simpleTypes, resolve);
        this.simpleMethods = new SimpleMethods(simpleTypes, names, enter, attr);
        this.environment = environment;
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
            var env = simpleTypes.findClassEnv((Type.ClassType) classSymbol.asType());
            return new ReifiedCandidate(typeVariable, (JCTree.JCClassDecl) env.tree, null);
        }

        if(owner instanceof Symbol.MethodSymbol){
            var methodSymbol = (Symbol.MethodSymbol) owner;
            var classSymbol = (Symbol.ClassSymbol) methodSymbol.getEnclosingElement();
            var classEnv = simpleTypes.findClassEnv((Type.ClassType) classSymbol.asType());
            var methodEnv = trees.getPath(methodSymbol);
            return new ReifiedCandidate(typeVariable, (JCTree.JCClassDecl) classEnv.tree, (JCTree.JCMethodDecl) methodEnv.getLeaf());
        }

        throw new IllegalArgumentException("Cannot find annotated tree, unknown owner: " + owner.getClass().getName());
    }

    private List<ReifiedDeclaration> parseCandidates(List<ReifiedCandidate> candidates) {
        return candidates.stream()
                .map(this::parseCandidate)
                .collect(List.collector());
    }

    private ReifiedDeclaration parseCandidate(ReifiedCandidate candidate) {
        return ReifiedDeclaration.builder()
                .typeParameter(candidate.typeVariableSymbol())
                .enclosingClass(candidate.enclosingClass())
                .methods(findAndProcessMembers(candidate))
                .isClass(isClassScoped(candidate))
                .modifier(simpleClasses.findRealAccess(candidate.enclosingClass(), candidate.enclosingMethod()))
                .build();
    }

    private boolean isClassScoped(ReifiedCandidate candidate){
        return candidate.enclosingMethod() == null;
    }

    private List<JCTree.JCMethodDecl> findAndProcessMembers(ReifiedCandidate candidate) {
        if(isClassScoped(candidate)){
            var localVariable = createLocalVariable(candidate.typeVariableSymbol(), candidate.enclosingClass());
            var constructors = simpleClasses.findConstructors(candidate.enclosingClass());
            constructors.forEach(constructor -> addParameterAndAssign(candidate.typeVariableSymbol(), candidate.enclosingClass(), localVariable, constructor));
            return constructors;
        }

        addParameter(candidate.typeVariableSymbol(), candidate.enclosingMethod());
        return List.of(candidate.enclosingMethod());
    }

    private void addParameterAndAssign(Element typeParameter, JCTree.JCClassDecl typeParentTree, JCTree.JCVariableDecl localVariable, JCTree.JCMethodDecl constructor) {
        var parameter = addParameter(typeParameter, constructor);
        var thisCall = treeMaker.This(typeParentTree.sym.asType());
        var localVariableSelection = treeMaker.Select(thisCall, localVariable.getName());
        var localVariableAssignment = treeMaker.Assign(localVariableSelection, treeMaker.Ident(parameter));

        var localVariableStatement = treeMaker.at(constructor.pos).Exec(localVariableAssignment);
        var newStats = new ArrayList<>(constructor.body.stats);
        addStatement(localVariableStatement, newStats);
        constructor.body.stats = List.from(newStats);
    }

    private void addStatement(JCTree.JCExpressionStatement localVariableStatement, java.util.List<JCTree.JCStatement> newStats) {
        if(newStats.isEmpty()){
            newStats.add(localVariableStatement);
            return;
        }

        newStats.add(1, localVariableStatement);
    }

    private JCTree.JCVariableDecl createLocalVariable(Element typeParameter, JCTree.JCClassDecl enclosingClass) {
        var localVariableName = (Name) typeParameter.getSimpleName();
        var localVariableModifiers = treeMaker.Modifiers(Modifier.PRIVATE | Modifier.FINAL);
        var localVariableType = treeMaker.Type(simpleTypes.createTypeWithParameter(Class.class, typeParameter));
        var localVariable = treeMaker.at(enclosingClass.pos).VarDef(localVariableModifiers, localVariableName, localVariableType, null);
        enclosingClass.defs = enclosingClass.defs.prepend(localVariable);
        return localVariable;
    }

    private JCTree.JCVariableDecl addParameter(Element typeParameter, JCTree.JCMethodDecl method) {
        var paramType = simpleTypes.createTypeWithParameter(Class.class, typeParameter);
        var param = treeMaker.at(method.pos).Param((Name) typeParameter.getSimpleName(), paramType, method.sym);
        param.sym.adr = 0;

        method.params = method.params.prepend(param);

        var methodSymbol = (Symbol.MethodSymbol) method.sym;
        methodSymbol.params = methodSymbol.params.prepend(param.sym);

        var methodType = methodSymbol.type.asMethodType();
        methodType.argtypes = methodType.argtypes.prepend(paramType);

        return param;
    }

    private void processing() {
        reifiedDeclarations.forEach(this::processTypeParameter);
    }

    private void processTypeParameter(ReifiedDeclaration reifiedDeclaration) {
        if (reifiedDeclaration.isClass()) {
            processClassParameter(reifiedDeclaration);
            return;
        }

       processMethodParameter(reifiedDeclaration);
    }

    private void processMethodParameter(ReifiedDeclaration reifiedDeclaration) {
        var methodScanner = new MethodInvocationScanner(reifiedDeclaration, simpleMethods, simpleTypes);
        findCompilationUnits(reifiedDeclaration)
                .stream()
                .map(methodScanner::scan)
                .flatMap(Collection::stream)
                .forEach(methodInvocation -> processMethodParameter(reifiedDeclaration.typeParameter(), methodInvocation));
    }

    private void processMethodParameter(Symbol.TypeVariableSymbol typeVariable, ReifiedCall<Symbol.MethodSymbol> inv) {
        var invocation = (JCTree.JCMethodInvocation) inv.invocation();
        var clazz = inv.enclosingClass();
        var method = inv.enclosingMethod();

        var type = simpleTypes.resolveWildCard(simpleMethods.resolveMethodType(
                typeVariable,
                invocation,
                inv.invoked(),
                clazz,
                method,
                inv.enclosingStatement()
        ));

        invocation.args = invocation.args.prepend(createClassLiteral(type, clazz, method));
    }

    private void processClassParameter(ReifiedDeclaration reifiedDeclaration) {
        var classScanner = new ClassInitializationScanner(reifiedDeclaration, simpleMethods, simpleTypes);
        findCompilationUnits(reifiedDeclaration)
                .stream()
                .map(classScanner::scan)
                .flatMap(Collection::stream)
                .forEach(classInit -> processClassParameter(reifiedDeclaration.typeParameter(), classInit));
    }

    private void processClassParameter(Symbol.TypeVariableSymbol typeVariable, ReifiedCall<Type.ClassType> init) {
        var invocation = (JCTree.JCNewClass) init.invocation();
        var clazz = init.enclosingClass();
        var method = init.enclosingMethod();

        var type = simpleClasses.resolveClassType(
                typeVariable,
                invocation,
                init.invoked(),
                clazz,
                method,
                init.enclosingStatement()
        );

        invocation.args = invocation.args.prepend(createClassLiteral(type, clazz, method));
    }

    public JCTree.JCExpression createClassLiteral(Type type, JCTree.JCClassDecl clazz, JCTree.JCMethodDecl method){
        if(!simpleTypes.isGeneric(type)){
            return treeMaker.ClassLiteral(simpleTypes.resolveWildCard(type));
        }

        var typeSymbol = (Symbol.TypeVariableSymbol) type.asElement().baseSymbol();
        if (!simpleTypes.isReified(typeSymbol)) {
            processTypeParameter(typeSymbol, clazz, method);
        }

        var name = type.asElement().getSimpleName();
        var owner = typeSymbol.owner;
        if(owner instanceof Symbol.ClassSymbol){
            var member = owner.members().findFirst(name, (sym) -> sym instanceof Symbol.VarSymbol);
            Assert.checkNonNull(member, "Nested reified parameter cannot be processed if enclosing parameters have not been processed yet");
            return treeMaker.Ident(member);
        }

        if(owner instanceof Symbol.MethodSymbol){
            var variable = ((Symbol.MethodSymbol) owner).getParameters()
                    .stream()
                    .filter(param -> param.getSimpleName().contentEquals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Nested reified parameter cannot be processed if enclosing parameters have not been processed yet"));
            return treeMaker.Ident(variable);
        }

        throw new IllegalArgumentException("Unknown owner: " + owner.getClass().getName());
    }

    private void processTypeParameter(Symbol.TypeVariableSymbol typeSymbol, JCTree.JCClassDecl clazz, JCTree.JCMethodDecl method) {
        var candidate = new ReifiedCandidate(typeSymbol, clazz, findEnclosingMethod(typeSymbol, method));
        processTypeParameter(parseCandidate(candidate));
    }

    private JCTree.JCMethodDecl findEnclosingMethod(Symbol.TypeVariableSymbol typeSymbol, JCTree.JCMethodDecl method) {
        return typeSymbol.owner instanceof Symbol.ClassSymbol ? null : method;
    }

    private Set<JCTree> findCompilationUnits(ReifiedDeclaration reifiedDeclaration) {
        return environment.getRootElements()
                .stream()
                .map(element -> simpleTypes.findClassEnv((JCTree.JCClassDecl) trees.getTree(element)))
                .filter(unit -> checkClassScope(reifiedDeclaration, unit))
                .map(env -> env.tree)
                .collect(Collectors.toUnmodifiableSet());
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
                throw new IllegalArgumentException("Unknown modifier: " + reifiedDeclaration.modifier());
        }
    }
}
