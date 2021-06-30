package it.auties.reified.processor;

import com.google.auto.service.AutoService;
import com.sun.source.tree.Scope;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import it.auties.reified.annotation.Reified;
import it.auties.reified.model.ReifiedParameter;
import it.auties.reified.scanner.ClassInitializationScanner;
import it.auties.reified.scanner.MethodInvocationScanner;
import it.auties.reified.simplified.SimpleClasses;
import it.auties.reified.simplified.SimpleMethods;
import it.auties.reified.simplified.SimpleTrees;
import it.auties.reified.simplified.SimpleTypes;
import lombok.SneakyThrows;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.*;

@SupportedAnnotationTypes(Reified.PATH)
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Reified.class)
public class ReifiedProcessor extends AbstractProcessor {
    private SimpleTrees simpleTrees;
    private SimpleTypes simpleTypes;
    private SimpleClasses simpleClasses;
    private SimpleMethods simpleMethods;
    private Enter enter;
    private MemberEnter memberEnter;
    private TreeMaker treeMaker;
    private Log logger;
    private Set<ReifiedParameter> reifiedParameters;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try{
            init();
            var ext = processingEnv.getElementUtils().getTypeElement(Reified.PATH);
            for (var element : roundEnv.getElementsAnnotatedWith(ext)) {
                lookUpTypeParameter(element);
            }

            for (var reifiedParameter : reifiedParameters) {
                processTypeParameter(reifiedParameter);
            }

            return true;
        }catch (Exception exception){
            logger.printRawLines(Log.WriterKind.ERROR, "Processing error: " +  exception.getMessage());
            exception.printStackTrace();
            throw new RuntimeException("Cannot process classes", exception);
        }
    }

    private void init(){
        var task = (JavacTaskImpl) JavacTask.instance(processingEnv);

        this.logger = Log.instance(task.getContext());
        this.treeMaker = TreeMaker.instance(task.getContext());
        this.enter = Enter.instance(task.getContext());
        this.memberEnter = MemberEnter.instance(task.getContext());

        var resolve = Resolve.instance(task.getContext());
        var attr = Attr.instance(task.getContext());
        var trees = Trees.instance(processingEnv);
        this.simpleTrees = new SimpleTrees(trees);
        this.simpleTypes = new SimpleTypes(processingEnv, attr, resolve);
        this.simpleClasses = new SimpleClasses(simpleTrees, simpleTypes);

        var memberEnter = MemberEnter.instance(task.getContext());
        this.simpleMethods = new SimpleMethods(simpleTypes, resolve, enter, memberEnter, attr);
        this.reifiedParameters = new HashSet<>();
    }

    private void processTypeParameter(ReifiedParameter reifiedParameter) {
        if(reifiedParameter.isClass()){
            processClassParameter(reifiedParameter);
            return;
        }

        processMethodParameter(reifiedParameter);
    }

    private void processMethodParameter(ReifiedParameter reifiedParameter) {
        var methodScanner = new MethodInvocationScanner(reifiedParameter, simpleMethods, logger);
        for(var methodInvocation : methodScanner.scan()) {
            var caller = (JCTree.JCMethodInvocation) methodInvocation.caller();
            var classType = (Type.ClassType) reifiedParameter.enclosingClassTree().sym.asType();
            var classEnv = enter.getClassEnv(classType.tsym);
            var methodEnv = memberEnter.getMethodEnv(methodInvocation.enclosingMethod(), classEnv);
            var type = simpleMethods.resolveMethodType(caller, methodEnv);
            logger.printRawLines(Log.WriterKind.WARNING, "Method type: " + type.toString());
            caller.args = caller.args.prepend(treeMaker.ClassLiteral(type));
        }
    }

    @SneakyThrows
    private void processClassParameter(ReifiedParameter reifiedParameter) {
        var classScanner = new ClassInitializationScanner(reifiedParameter, simpleMethods, logger, processingEnv.getElementUtils());
        for(var classInitialization : classScanner.scan()) {
            var caller = (JCTree.JCNewClass) classInitialization.caller();
            var enclosingClass = (Type.ClassType) reifiedParameter.enclosingClassTree().sym.asType();
            var enclosingClassEnv = enter.getClassEnv(enclosingClass.tsym);
            var methodEnv = memberEnter.getMethodEnv(classInitialization.enclosingMethod(), enclosingClassEnv);
            var type = simpleClasses.resolveClassType(caller, methodEnv);
            logger.printRawLines(Log.WriterKind.WARNING, "Method type: " + type.toString());
            caller.args = caller.args.prepend(treeMaker.ClassLiteral(type));
        }
    }

    private void lookUpTypeParameter(Element typeParameter) {
        logger.printRawLines(Log.WriterKind.WARNING, "Look up: " +  typeParameter.getSimpleName());
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
}
