package it.auties.reified.scanner;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleMethods;

public class MethodInvocationScanner extends ReifiedScanner<Symbol.MethodSymbol> {
    public MethodInvocationScanner(ReifiedDeclaration parameter, SimpleMethods simpleMethods) {
        super(parameter, simpleMethods);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        var rawNode = (JCTree.JCMethodInvocation) node;
        var calling = simpleMethods().resolveMethod(enclosingClass(), rawNode);

        System.err.println("Method candidate(" + rawNode + "): " + calling);
        if (parameter().methods().stream().noneMatch(method -> calling.filter(candidate -> method.sym.equals(candidate)).isPresent())) {
            return super.visitMethodInvocation(node, unused);
        }

        System.err.println("Method invocation found: " + node);
        results().add(buildResultCall(rawNode, calling.get()));
        return super.visitMethodInvocation(node, unused);
    }
}
