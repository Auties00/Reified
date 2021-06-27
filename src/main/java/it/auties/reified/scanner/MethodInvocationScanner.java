package it.auties.reified.scanner;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Log;
import it.auties.reified.model.ReifiedParameter;

public class MethodInvocationScanner extends ReifiedScanner<JCTree.JCMethodInvocation>  {
    public MethodInvocationScanner(ReifiedParameter parameter, Log logger) {
        super(parameter, logger);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        logger().printRawLines(Log.WriterKind.WARNING, "Method candidate: " + node);
        if (parameter().methods().stream().noneMatch(method -> checkMethodEquality(method, node))) {
            return super.visitMethodInvocation(node, unused);
        }

        logger().printRawLines(Log.WriterKind.WARNING, "Method invocation found: " + node);
        results().add((JCTree.JCMethodInvocation) node);
        return super.visitMethodInvocation(node, unused);
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
