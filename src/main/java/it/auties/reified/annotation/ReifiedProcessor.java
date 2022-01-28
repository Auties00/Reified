package it.auties.reified.annotation;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedCandidate;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.scanner.*;
import it.auties.reified.simplified.SimpleClasses;
import it.auties.reified.simplified.SimpleMaker;
import it.auties.reified.simplified.SimpleTypes;
import it.auties.reified.util.DiagnosticHandlerWorker;
import it.auties.reified.util.IllegalReflection;

import java.util.Objects;

public class ReifiedProcessor implements Plugin, TaskListener {
    private BasicJavacTask task;
    private SimpleTypes simpleTypes;
    private SimpleClasses simpleClasses;
    private SimpleMaker simpleMaker;
    private Symtab symtab;
    private DiagnosticHandlerWorker diagnosticHandlerWorker;
    private List<ReifiedDeclaration> reifiedDeclarations;
    private ListBuffer<ReifiedCall> reifiedResults;

    static {
        IllegalReflection.openJavac();
    }

    @Override
    public void init(JavacTask basicTask, String... args) {
        this.task = (BasicJavacTask) basicTask;
        var context = task.getContext();
        var attr = Attr.instance(context);
        var types = Types.instance(context);
        var treeMaker = TreeMaker.instance(context);
        var processingEnv = JavacProcessingEnvironment.instance(context);
        this.simpleTypes = new SimpleTypes(processingEnv, types);
        this.simpleClasses = new SimpleClasses(simpleTypes);
        this.simpleMaker = new SimpleMaker(treeMaker, simpleTypes);
        this.diagnosticHandlerWorker = new DiagnosticHandlerWorker(attr);
        this.symtab = Symtab.instance(context);
        basicTask.addTaskListener(this);
    }

    @Override
    public String getName() {
        return "reified";
    }

    @Override
    public boolean autoStart() {
        return true;
    }

    @Override
    public void started(TaskEvent event) {
        if(event.getKind() != TaskEvent.Kind.ANALYZE){
            return;
        }
        
        var unit = (JCCompilationUnit) event.getCompilationUnit();
        fixJavacBug(unit);
        diagnosticHandlerWorker.useCachedHandler();
    }

    private void fixJavacBug(JCCompilationUnit unit) {
        new PatchScanner(symtab)
                .scan(unit);
    }

    @Override
    public void finished(TaskEvent event) {
        if (event.getKind() != TaskEvent.Kind.ANALYZE) {
            return;
        }

        var unit = (JCCompilationUnit) event.getCompilationUnit();
        lookup(unit);
        processing(unit);
        fixSymbols(unit);
    }

    private void fixSymbols(JCCompilationUnit unit) {
        new CompleterScanner(simpleTypes, task.getContext())
                .scan(unit);
    }

    private void lookup(JCCompilationUnit unit) {
        var candidates = new AnnotationScanner(simpleClasses, simpleTypes)
                .scan(unit);
        this.reifiedDeclarations = parseCandidates(candidates);
    }

    private List<ReifiedDeclaration> parseCandidates(List<ReifiedCandidate> candidates) {
        return candidates.stream()
                .map(this::parseCandidate)
                .collect(List.collector());
    }

    private ReifiedDeclaration parseCandidate(ReifiedCandidate candidate) {
        return new ReifiedDeclaration(candidate.typeVariable(), candidate.enclosingClass(),
                findMembers(candidate), candidate.hasClass());
    }

    private List<JCTree.JCMethodDecl> findMembers(ReifiedCandidate candidate) {
        if (!candidate.hasClass()) {
            return List.of(candidate.enclosingMethod());
        }

        return simpleClasses.findConstructors(candidate.enclosingClass());
    }

    private void processing(JCCompilationUnit unit) {
        this.reifiedResults = new ListBuffer<>();
        reifiedDeclarations.forEach(declaration -> processTypeParameter(declaration, unit));
        reifiedDeclarations.forEach(simpleMaker::processMembers);
        reifiedResults.forEach(result -> applyParameter(result, unit));
        debug();
    }

    private void applyParameter(ReifiedCall call, JCCompilationUnit unit) {
        var literal = createClassLiteral(unit, call.reifiedType(), call.enclosingClass(), call.enclosingMethod());
        var symbol = (Symbol.MethodSymbol) TreeInfo.symbolFor(call.invocation());
        var argumentSymbol = (Symbol.VarSymbol) TreeInfo.symbolFor(literal);
        symbol.params = symbol.params.prepend(argumentSymbol);
        if(call.invocation() instanceof JCTree.JCNewClass newClass) {
            newClass.args = newClass.args.prepend(literal);
            simpleTypes.asMethodType(newClass.constructorType)
                    .ifPresent(methodType -> methodType.argtypes = methodType.argtypes.prepend(literal.type));
            return;
        }

        if(call.invocation() instanceof JCTree.JCMethodInvocation methodInvocation) {
            methodInvocation.args = methodInvocation.args.prepend(literal);
            simpleTypes.asMethodType(methodInvocation.getMethodSelect().type)
                    .ifPresent(methodType -> methodType.argtypes = methodType.argtypes.prepend(literal.type));
            return;
        }

        throw new IllegalArgumentException("Cannot apply parameter to unknown type: " + call.invocation().getTag().name());
    }

    private void processChildClass(ReifiedDeclaration reifiedDeclaration, JCCompilationUnit unit) {
        var enclosingClass = reifiedDeclaration.enclosingClass();
        var childClasses = findChildClasses(enclosingClass);
        childClasses.forEach(childClass -> processChildClass(reifiedDeclaration, unit, enclosingClass, childClass));
    }

    private void processChildClass(ReifiedDeclaration reifiedDeclaration, JCCompilationUnit unit, JCTree.JCClassDecl enclosingClass, JCTree.JCClassDecl childClass) {
        var type = findChildClassType(reifiedDeclaration, enclosingClass, childClass);
        var literal = createClassLiteral(unit, type, childClass, null);
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
                var types = simpleTypes.eraseTypeVariableFromTypeParameters(reifiedDeclaration.typeParameter(), enclosingClass.sym.getTypeParameters(), typeApply.getTypeArguments());
                return Objects.requireNonNullElse(simpleTypes.commonType(types), simpleTypes.erase(reifiedDeclaration.typeParameter()));
            default:
                throw new IllegalArgumentException("Unsupported tag for child class type: " + extendClause.getTag().name());
        }
    }

    private List<JCTree.JCClassDecl> findChildClasses(JCTree.JCClassDecl superClass) {
        return List.nil(); // For now
    }

    private void processTypeParameter(ReifiedDeclaration reifiedDeclaration, JCCompilationUnit unit) {
        if (!reifiedDeclaration.isClass()) {
            processMethodParameter(reifiedDeclaration, unit);
            return;
        }

        processClassParameter(reifiedDeclaration, unit);
        processChildClass(reifiedDeclaration, unit);
    }

    private void processMethodParameter(ReifiedDeclaration reifiedDeclaration, JCCompilationUnit unit) {
        new MethodInvocationScanner(reifiedDeclaration, simpleClasses, simpleTypes)
                .scan(unit)
                .stream()
                .peek(methodInvocation -> methodInvocation.reifiedType(simpleTypes.infer(methodInvocation)))
                .forEach(reifiedResults::add);
    }

    private void processClassParameter(ReifiedDeclaration reifiedDeclaration, JCCompilationUnit unit) {
        new ClassInitializationScanner(reifiedDeclaration, simpleClasses, simpleTypes)
                .scan(unit)
                .stream()
                .peek(classInit -> classInit.reifiedType(simpleTypes.infer(classInit)))
                .forEach(reifiedResults::add);
    }

    public JCTree.JCExpression createClassLiteral(JCCompilationUnit unit, Type type, JCTree.JCClassDecl clazz, JCTree.JCMethodDecl method) {
        if (type.getTag() != TypeTag.TYPEVAR) {
            return simpleMaker.classLiteral(type);
        }

        var typeSymbol = (Symbol.TypeVariableSymbol) type.asElement().baseSymbol();
        if (!simpleTypes.isReified(typeSymbol)) {
            processTypeParameter(unit, typeSymbol, clazz, method);
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

    private void processTypeParameter(JCCompilationUnit unit, Symbol.TypeVariableSymbol typeSymbol, JCTree.JCClassDecl clazz, JCTree.JCMethodDecl method) {
        var enclosingMethod = typeSymbol.getEnclosingElement() instanceof Symbol.ClassSymbol ? null : method;
        var candidate = new ReifiedCandidate(typeSymbol, clazz, enclosingMethod);
        var declaration = parseCandidate(candidate);
        processTypeParameter(declaration, unit);
        simpleMaker.processMembers(declaration);
    }

    private void debug(){
        System.err.println("Reified declarations:");
        reifiedDeclarations.forEach(System.err::println);
    }
}
