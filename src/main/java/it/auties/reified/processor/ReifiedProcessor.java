package it.auties.reified.processor;

import com.google.auto.service.AutoService;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Todo;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import it.auties.reified.annotation.Reified;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedCandidate;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.scanner.AnnotationScanner;
import it.auties.reified.scanner.ClassInitializationScanner;
import it.auties.reified.scanner.MethodInvocationScanner;
import it.auties.reified.simplified.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

@SupportedAnnotationTypes(Reified.PATH)
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Reified.class)
public class ReifiedProcessor extends AbstractProcessor {
    private SimpleTypes simpleTypes;
    private SimpleClasses simpleClasses;
    private SimpleMethods simpleMethods;
    private Enter enter;
    private Todo todo;
    private TreeMaker treeMaker;
    private Set<ReifiedDeclaration> reifiedDeclarations;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if(roundEnv.processingOver()){
            return true;
        }

        init();
        lookup();
        processing();
        return true;
    }

    private void init() {
        var context = SimpleContext.resolveContext(processingEnv);
        this.treeMaker = TreeMaker.instance(context);
        this.enter = Enter.instance(context);
        this.todo = Todo.instance(context);

        var javacTrees = JavacTrees.instance(context);
        var simpleTrees = new SimpleTrees(javacTrees);
        var attr = Attr.instance(context);
        var types = Types.instance(context);

        this.simpleTypes = new SimpleTypes(processingEnv, types, attr, enter);
        this.simpleClasses = new SimpleClasses(simpleTrees, simpleTypes);

        var names = Names.instance(context);
        this.simpleMethods = new SimpleMethods(simpleTypes, names, enter, attr);
        this.reifiedDeclarations = new HashSet<>();
    }

    private void lookup() {
        var annotationScanner = new AnnotationScanner();
        var candidates = findAnnotatedTrees(annotationScanner);
        this.reifiedDeclarations = parseCandidates(candidates);
    }

    private Set<ReifiedCandidate> findAnnotatedTrees(AnnotationScanner annotationScanner) {
        return todo.stream()
                .map(todo -> todo.toplevel)
                .map(annotationScanner::scan)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    private Set<ReifiedDeclaration> parseCandidates(Set<ReifiedCandidate> candidates) {
        return candidates.stream()
                .map(this::parseCandidate)
                .collect(Collectors.toSet());
    }

    private ReifiedDeclaration parseCandidate(ReifiedCandidate candidate) {
        return ReifiedDeclaration.builder()
                .typeParameter(candidate.typeVariableSymbol())
                .enclosingClassTree(candidate.enclosingClass())
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
        newStats.add(1, localVariableStatement);
        constructor.body.stats = List.from(newStats);
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
        var param = treeMaker.at(method.pos).Param((Name) typeParameter.getSimpleName(), simpleTypes.createTypeWithParameter(Class.class, typeParameter), method.sym);
        param.sym.adr = 0;
        method.params = method.params.prepend(param);
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
        var methodScanner = new MethodInvocationScanner(reifiedDeclaration, simpleMethods);
        determineScanScope(reifiedDeclaration)
                .stream()
                .map(methodScanner::scan)
                .flatMap(Collection::stream)
                .forEach(methodInvocation -> processMethodParameter(reifiedDeclaration.typeParameter(), methodInvocation));
    }

    private void processMethodParameter(Symbol.TypeVariableSymbol typeVariable, ReifiedCall<Symbol.MethodSymbol> methodInvocation) {
        var caller = (JCTree.JCMethodInvocation) methodInvocation.callerExpression();
        var callerSymbol = methodInvocation.caller();
        var callerEnclosingSymbol = methodInvocation.callerEnclosingClass().sym;
        var callerTopEnv = enter.getClassEnv(callerEnclosingSymbol.asType().asElement());
        var type = simpleMethods.resolveMethodType(typeVariable, caller, callerSymbol, callerTopEnv);
        caller.args = caller.args.prepend(treeMaker.ClassLiteral(type));
    }

    private void processClassParameter(ReifiedDeclaration reifiedDeclaration) {
        var classScanner = new ClassInitializationScanner(reifiedDeclaration, simpleMethods, simpleTypes);
        determineScanScope(reifiedDeclaration)
                .stream()
                .map(classScanner::scan)
                .flatMap(Collection::stream)
                .forEach(classInit -> processClassParameter(reifiedDeclaration.typeParameter(), classInit));
    }

    private void processClassParameter(Symbol.TypeVariableSymbol typeVariable, ReifiedCall<Type.ClassType> classInitialization) {
        var caller = (JCTree.JCNewClass) classInitialization.callerExpression();
        var callerType = (Type.ClassType) classInitialization.caller();
        var type = simpleClasses.resolveClassType(typeVariable, callerType);
        caller.args = caller.args.prepend(treeMaker.ClassLiteral(type));
    }

    private List<JCTree.JCCompilationUnit> determineScanScope(ReifiedDeclaration reifiedDeclaration) {
        return todo.stream()
                .map(todo -> todo.toplevel)
                .filter(unit -> checkClassScope(reifiedDeclaration, unit))
                .collect(List.collector());
    }

    private boolean checkClassScope(ReifiedDeclaration reifiedDeclaration, JCTree.JCCompilationUnit unit) {
        var paramEnv = enter.getEnv(reifiedDeclaration.enclosingClassTree().sym);
        var paramUnit = paramEnv.toplevel;
        switch (reifiedDeclaration.modifier()) {
            case PUBLIC:
                return true;
            case PRIVATE:
                return Objects.equals(unit, paramUnit);
            case PROTECTED:
            case PACKAGE_PRIVATE:
                return Objects.equals(unit.packge, paramUnit.packge);
            default:
                throw new IllegalArgumentException("Unknown modifier: " + reifiedDeclaration.modifier());
        }
    }
}
