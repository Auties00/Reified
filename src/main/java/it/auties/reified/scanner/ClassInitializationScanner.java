package it.auties.reified.scanner;

import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedParameter;
import it.auties.reified.simplified.SimpleMethods;
import lombok.SneakyThrows;

import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import java.util.Objects;

public class ClassInitializationScanner extends ReifiedScanner {
    private final Elements elements;

    public ClassInitializationScanner(ReifiedParameter parameter, SimpleMethods simpleMethods, Log logger, Elements elements) {
        super(parameter, simpleMethods, logger);
        this.elements = elements;
    }


    @Override
    public Void visitMethod(MethodTree node, Void unused) {
        enclosingMethod((JCTree.JCMethodDecl) node);
        return super.visitMethod(node, unused);
    }

    @Override
    public Void visitVariable(VariableTree node, Void unused) {
        callerStatement((JCTree.JCVariableDecl) node);
        return super.visitVariable(node, unused);
    }

    @Override
    public Void visitReturn(ReturnTree node, Void unused) {
        callerStatement((JCTree.JCReturn) node);
        return super.visitReturn(node, unused);
    }

    @SneakyThrows
    @Override
    public Void visitNewClass(NewClassTree node, Void unused) {
        logger().printRawLines(Log.WriterKind.WARNING, "Class candidate: " + node);
        var rawTree = (JCTree.JCNewClass) node;
        if (!checkClassEquality(parameter().enclosingClassTree(), parameter().enclosingClassElement(), rawTree)) {
            return super.visitNewClass(node, unused);
        }

        logger().printRawLines(Log.WriterKind.WARNING, "Class found: " + node);
        results().add(buildResultCall(rawTree));
        return super.visitNewClass(node, unused);
    }

    private ReifiedCall buildResultCall(JCTree.JCNewClass rawTree) {
        return ReifiedCall.builder()
                .caller(rawTree)
                .callerStatement(callerStatement())
                .enclosingMethod(Objects.requireNonNull(enclosingMethod(), "Class initialization outside of method"))
                .build();
    }

    private boolean checkClassEquality(JCTree.JCClassDecl clazz, Element clazzAsElement, JCTree.JCNewClass invocation){
        var identifier = invocation.getIdentifier();
        var identifierName = findQualifiedName(identifier);
        var clazzName = clazz.getSimpleName();
        if(identifierName.toString().split("\\.").length == 1){
            return clazzName.contentEquals(identifierName);
        }

        var clazzPackage = elements.getPackageOf(clazzAsElement);
        var packageName = (Name) clazzPackage.getQualifiedName();
        return identifierName.contentEquals(packageName.append('.', clazzName));
    }

    private Name findQualifiedName(JCTree.JCExpression identifier) {
        switch (identifier.getTag()) {
            case SELECT:
                var select = (JCTree.JCFieldAccess) identifier;
                var selected = findQualifiedName(select.selected);
                return selected.append('.', select.name);
            case IDENT:
                return ((JCTree.JCIdent) identifier).name;
            case TYPEAPPLY:
                return findQualifiedName(((JCTree.JCTypeApply) identifier).clazz);
            default:
                throw new RuntimeException("Unexpected identifier: " + identifier.getClass().getName());
        }
    }
}
