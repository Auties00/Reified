package it.auties.reified.scanner;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleMethods;
import it.auties.reified.simplified.SimpleTrees;
import it.auties.reified.simplified.SimpleTypes;

public class MethodInvocationScanner extends ReifiedScanner<Symbol.MethodSymbol> {
    public MethodInvocationScanner(ReifiedDeclaration parameter, SimpleMethods simpleMethods, SimpleTypes simpleTypes) {
        super(parameter, simpleMethods, simpleTypes);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        var rawNode = (JCTree.JCMethodInvocation) node;
        var calling = simpleMethods().resolveMethod(enclosingClass(), rawNode);

        System.err.println("Method candidate(" + rawNode + "): " + calling);
        if (parameter().methods().stream().noneMatch(method -> calling.filter(candidate -> method.sym.equals(candidate)).isPresent())) {
            return super.visitMethodInvocation(node, unused);
        }

        results().add(buildResultCall(rawNode, calling.get()));
        return super.visitMethodInvocation(node, unused);
    }
}
