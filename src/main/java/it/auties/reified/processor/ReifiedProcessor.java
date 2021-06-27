package it.auties.reified.processor;

import com.google.auto.service.AutoService;
import com.sun.source.tree.Scope;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import it.auties.reified.annotation.Reified;
import it.auties.reified.model.ReifiedParameter;
import it.auties.reified.scanner.ClassInitializationScanner;
import it.auties.reified.scanner.MethodInvocationScanner;
import it.auties.reified.scanner.ReifiedScanner;
import it.auties.reified.simplified.SimpleClasses;
import it.auties.reified.simplified.SimpleTrees;
import it.auties.reified.simplified.SimpleTypes;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.sun.tools.javac.code.Flags.*;

@SupportedAnnotationTypes(Reified.PATH)
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Reified.class)
public class ReifiedProcessor extends AbstractProcessor {
    private SimpleTrees simpleTrees;
    private SimpleTypes simpleTypes;
    private SimpleClasses simpleClasses;
    private Names names;
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
        var trees = Trees.instance(processingEnv);
        this.simpleTrees = new SimpleTrees(trees);
        this.simpleTypes = new SimpleTypes(processingEnv, logger);
        this.simpleClasses = new SimpleClasses(simpleTrees);
        this.names = Names.instance(task.getContext());
        this.reifiedParameters = new HashSet<>();
    }

    private void processTypeParameter(ReifiedParameter reifiedParameter) {
        if(reifiedParameter.isClass()){
            var classScanner = new ClassInitializationScanner(reifiedParameter, logger, processingEnv.getElementUtils());
            for(var classInitialization : classScanner.scan()) {
                var type = classInitialization.clazz;
                var realType = simpleTypes.resolveGenericType(type);
                if(realType.isEmpty()){
                    //TODO: Raw class -> Deduce base type
                    throw new RuntimeException();
                }

                var symbol = new Symbol.ClassSymbol(STATIC | PUBLIC | FINAL, realType.get(), reifiedParameter.enclosingClassTree().sym);
                logger.printRawLines(Log.WriterKind.WARNING, "Symbol: " + symbol);
                classInitialization.args = classInitialization.args.prepend(treeMaker.ClassLiteral(symbol));
            }
        }

        var methodScanner = new MethodInvocationScanner(reifiedParameter, logger);
        var results = methodScanner.scan();
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
        members.forEach(method -> method.params = method.params.prepend(treeMaker.Param((Name) typeParameter.getSimpleName(), simpleTypes.createTypeWithParameter(Class.class, typeParameter), method.sym)));

        var enclosingClass = classScoped ? typeParentTree : simpleTrees.toTree(typeParentScope.getEnclosingClass());
        var enclosingClassElement = classScoped ? typeParent : typeParentScope.getEnclosingClass();
        var reifiedParameter = createReifiedParam(typeParentTree, (JCTree.JCClassDecl) enclosingClass, enclosingClassElement, typeParentScope, classScoped, members);
        reifiedParameters.add(reifiedParameter);
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

/*
    private JCTree.JCVariableDecl createTypeParameterField(Element typeParameter, JCTree typeParentTree, boolean classScoped) {
        var modifiers = maker.Modifiers(classScoped ? Modifier.PRIVATE : 0);
        var type = maker.Type(createTypeWithParameter(Class.class, typeParameter));
        return maker.at(typeParentTree.pos).VarDef(modifiers, names.fromString(typeParameter.getSimpleName().toString()), type, createTypeParameterInitializer(typeParameter, typeParentTree));
    }

    private JCTree.JCExpression createTypeParameterInitializer(Element typeParameter, JCTree typeParentTree) {
        var anonymousClassType = createTypeWithParameter(ReifiedParameter.class, typeParameter);
        var anonymousClassInstance = maker.NewClass(null, List.nil(), maker.Type(anonymousClassType), List.nil(), maker.AnonymousClassDef(maker.Modifiers(0), List.nil()));
        return maker.App(maker.Select(anonymousClassInstance, resolve.resolveInternalMethod(typeParentTree.pos(), findEnvironment(typeParameter), anonymousClassType, names.fromString("erase"), List.nil(), List.nil())));
    }

    public Env<AttrContext> findEnvironment(Element element) {
        return enter.getTopLevelEnv((JCTree.JCCompilationUnit) trees.getPath(element).getCompilationUnit());
    }
 */
}
