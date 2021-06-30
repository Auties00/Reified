package it.auties.reified.scanner;

import com.sun.source.tree.*;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Pair;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedParameter;
import it.auties.reified.simplified.SimpleMethods;

import java.util.Objects;

public class MethodInvocationScanner extends ReifiedScanner {
    public MethodInvocationScanner(ReifiedParameter parameter, SimpleMethods simpleMethods, Log logger) {
        super(parameter, simpleMethods, logger);
    }

    @Override
    public Void visitClass(ClassTree node, Void unused) {
        enclosingClass((JCTree.JCClassDecl) node);
        return super.visitClass(node, unused);
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

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        var rawNode = (JCTree.JCMethodInvocation) node;
        var calling = simpleMethods().resolveMethod(enclosingClass(), enclosingMethod(), rawNode);
        logger().printRawLines(Log.WriterKind.WARNING, "Method candidate(" + calling.getClass().getName() + "): " + calling);
        if (calling.isEmpty() || parameter().methods().stream().noneMatch(method -> method.sym.equals(calling.get()))) {
            return super.visitMethodInvocation(node, unused);
        }

        logger().printRawLines(Log.WriterKind.WARNING, "Method invocation found: " + node);
        results().add(buildResultCall((JCTree.JCMethodInvocation) node));
        return super.visitMethodInvocation(node, unused);
    }

    private ReifiedCall buildResultCall(JCTree.JCMethodInvocation rawTree) {
        return ReifiedCall.builder()
                .caller(rawTree)
                .callerStatement(callerStatement())
                .enclosingMethod(Objects.requireNonNull(enclosingMethod(), "Method call outside of method"))
                .build();
    }

    private boolean checkMethodEquality(JCTree.JCMethodDecl method, MethodInvocationTree invocation){
        var methodName = method.getName();
        var invocationName = TreeInfo.name((JCTree) invocation.getMethodSelect());
        if(!methodName.contentEquals(invocationName)){
            return false;
        }

        var methodArgs = method.getParameters();
        var invocationArgs = ((JCTree.JCMethodInvocation) invocation).getArguments();
        return methodArgs.stream().allMatch(methodArg -> invocationArgs.stream().allMatch(invocationArg -> checkMethodParamsEquality(methodArg, invocationArg)));
    }

    private boolean checkMethodParamsEquality(JCTree.JCVariableDecl methodArg, JCTree.JCExpression invocationArg) {
        return methodArg.name.contentEquals(TreeInfo.name(invocationArg)) && methodArg.type.equals(invocationArg.type);
    }
}
