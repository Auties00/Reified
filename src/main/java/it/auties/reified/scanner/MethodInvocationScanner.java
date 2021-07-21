package it.auties.reified.scanner;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleClasses;
import it.auties.reified.simplified.SimpleMethods;

public class MethodInvocationScanner extends ReifiedScanner {
    public MethodInvocationScanner(ReifiedDeclaration parameter, SimpleClasses simpleClasses, SimpleMethods simpleMethods) {
        super(parameter, simpleClasses, simpleMethods);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        var rawNode = (JCTree.JCMethodInvocation) node;
        var calling = simpleMethods().resolveMethod(enclosingClass(), enclosingMethod(), rawNode);
        if (calling.filter(this::isMatchingMethod).isEmpty()) {
            return super.visitMethodInvocation(node, unused);
        }

        results().add(buildResultCall(rawNode, calling.get()));
        return super.visitMethodInvocation(node, unused);
    }

    private boolean isMatchingMethod(Symbol.MethodSymbol calling) {
        return parameter()
                .methods()
                .stream()
                .anyMatch(method -> method.sym.equals(calling));
    }
}
