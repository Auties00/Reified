package it.auties.reified.processor;

import com.google.auto.service.AutoService;
import com.sun.source.tree.Scope;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Todo;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import it.auties.reified.annotation.Reified;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedParameter;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@SupportedAnnotationTypes(Reified.PATH)
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Reified.class)
public class ReifiedProcessor extends AbstractProcessor {
    private SimpleTrees simpleTrees;
    private SimpleTypes simpleTypes;
    private SimpleClasses simpleClasses;
    private SimpleMethods simpleMethods;
    private Enter enter;
    private Todo todo;
    private TreeMaker treeMaker;
    private Set<ReifiedParameter> reifiedParameters;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        init();
        var ext = processingEnv.getElementUtils().getTypeElement(Reified.PATH);
        lookup(roundEnv, ext);
        processing();
        return true;
    }


    private void init() {
        var context = SimpleContext.resolveContext(processingEnv);
        this.treeMaker = TreeMaker.instance(context);
        this.enter = Enter.instance(context);
        this.todo = Todo.instance(context);

        var javacTrees = JavacTrees.instance(context);
        this.simpleTrees = new SimpleTrees(javacTrees);

        var attr = Attr.instance(context);
        var types = Types.instance(context);
        this.simpleTypes = new SimpleTypes(processingEnv, types, attr, enter);
        this.simpleClasses = new SimpleClasses(simpleTrees, simpleTypes);

        var names = Names.instance(context);
        this.simpleMethods = new SimpleMethods(simpleTypes, names, enter, attr);
        this.reifiedParameters = new HashSet<>();
    }

    private void lookup(RoundEnvironment roundEnv, TypeElement ext) {
        roundEnv.getElementsAnnotatedWith(ext).forEach(this::lookUpTypeParameter);
    }

    private void lookUpTypeParameter(Element typeParameter) {
        var typePath = simpleTrees.toPath(typeParameter);
        var typeScope = simpleTrees.findScope(typePath);

        var typeParent = simpleTrees.findParent(typeScope);
        var typeParentTree = simpleTrees.toTree(typeParent);
        var typeParentPath = simpleTrees.toPath(typeParent);
        var typeParentScope = simpleTrees.findScope(typeParentPath);

        var classScoped = typeParentScope.getEnclosingMethod() == null;
        var members = classScoped ? simpleClasses.findConstructors(typeParentTree) : List.of((JCTree.JCMethodDecl) typeParentTree);
        members.forEach(method -> addParameter(typeParameter, method));

        var enclosingClass = classScoped ? typeParentTree : simpleTrees.toTree(typeParentScope.getEnclosingClass());
        var enclosingClassElement = classScoped ? typeParent : typeParentScope.getEnclosingClass();
        var reifiedParameter = createReifiedParam(typeParentTree, (JCTree.JCClassDecl) enclosingClass, enclosingClassElement, typeParentScope, classScoped, members);
        reifiedParameters.add(reifiedParameter);
    }

    private void addParameter(Element typeParameter, JCTree.JCMethodDecl method) {
        var param = treeMaker.at(method.pos).Param((Name) typeParameter.getSimpleName(), simpleTypes.createTypeWithParameter(Class.class, typeParameter), method.sym);
        param.sym.adr = 0;
        method.params = method.params.prepend(param);
    }

    private ReifiedParameter createReifiedParam(JCTree typeParentTree, JCTree.JCClassDecl enclosingClass, Element enclosingClassElement, Scope typeParentScope, boolean classScoped, List<JCTree.JCMethodDecl> members) {
        return ReifiedParameter.builder()
                .enclosingClassTree(enclosingClass)
                .enclosingClassElement(enclosingClassElement)
                .methods(members)
                .isClass(classScoped)
                .modifier(simpleClasses.findRealAccess(typeParentTree, typeParentScope, classScoped))
                .build();
    }

    private void processing() {
        reifiedParameters.forEach(this::processTypeParameter);
    }

    private void processTypeParameter(ReifiedParameter reifiedParameter) {
        if (reifiedParameter.isClass()) {
            processClassParameter(reifiedParameter);
            return;
        }

       processMethodParameter(reifiedParameter);
    }

    private void processMethodParameter(ReifiedParameter reifiedParameter) {
        var methodScanner = new MethodInvocationScanner(reifiedParameter, simpleMethods);
        determineScanScope(reifiedParameter)
                .stream()
                .map(methodScanner::scan)
                .flatMap(Collection::stream)
                .forEach(this::processMethodParameter);
    }

    private void processMethodParameter(ReifiedCall<Symbol.MethodSymbol> methodInvocation) {
        var caller = (JCTree.JCMethodInvocation) methodInvocation.callerExpression();
        var callerSymbol = methodInvocation.caller();
        var callerEnclosingSymbol = methodInvocation.callerEnclosingClass().sym;
        var callerTopEnv = enter.getClassEnv(callerEnclosingSymbol.asType().asElement());
        var type = simpleMethods.resolveMethodType(caller, callerSymbol, callerTopEnv);
        caller.args = caller.args.prepend(treeMaker.ClassLiteral(type));
    }

    private void processClassParameter(ReifiedParameter reifiedParameter) {
        var classScanner = new ClassInitializationScanner(reifiedParameter, simpleMethods, simpleTypes);
        determineScanScope(reifiedParameter)
                .stream()
                .map(classScanner::scan)
                .flatMap(Collection::stream)
                .forEach(this::processClassParameter);
    }

    private void processClassParameter(ReifiedCall<Type.ClassType> classInitialization) {
        var caller = (JCTree.JCNewClass) classInitialization.callerExpression();
        var callerType = (Type.ClassType) classInitialization.caller();
        var type = simpleClasses.resolveClassType(callerType);
        caller.args = caller.args.prepend(treeMaker.ClassLiteral(type));
    }

    private List<JCTree.JCCompilationUnit> determineScanScope(ReifiedParameter reifiedParameter) {
        return todo.stream()
                .map(todo -> todo.toplevel)
                .filter(unit -> checkClassScope(reifiedParameter, unit))
                .collect(List.collector());
    }

    private boolean checkClassScope(ReifiedParameter reifiedParameter, JCTree.JCCompilationUnit unit) {
        var paramPath = simpleTrees.toPath(reifiedParameter.enclosingClassElement());
        var paramUnit = (JCTree.JCCompilationUnit) paramPath.getCompilationUnit();
        switch (reifiedParameter.modifier()) {
            case PUBLIC:
                return true;
            case PRIVATE:
                return Objects.equals(unit, paramUnit);
            case PROTECTED:
            case PACKAGE_PRIVATE:
                return Objects.equals(unit.packge, paramUnit.packge);
            default:
                throw new IllegalArgumentException("Unknown modifier: " + reifiedParameter.modifier());
        }
    }
}
