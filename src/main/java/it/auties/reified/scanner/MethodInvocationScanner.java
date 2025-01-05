package it.auties.reified.scanner;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleClasses;
import it.auties.reified.simplified.SimpleTypes;

public class MethodInvocationScanner extends ReifiedScanner<ReifiedCall> {
    public MethodInvocationScanner(ReifiedDeclaration parameter, SimpleClasses simpleClasses, SimpleTypes simpleTypes) {
        super(parameter, simpleClasses, simpleTypes);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        var rawNode = (JCTree.JCMethodInvocation) node;
        simpleClasses.resolveMethod(enclosingClass, enclosingMethod, rawNode)
                .filter(this::isMatchingMethod)
                .ifPresent(methodSymbol -> results.add(buildCall(rawNode, methodSymbol)));
        return super.visitMethodInvocation(node, unused);
    }

    private boolean isMatchingMethod(Symbol.MethodSymbol calling) {
        return parameter.methods()
                .stream()
                .anyMatch(method -> method.sym.equals(calling));
    }
}
