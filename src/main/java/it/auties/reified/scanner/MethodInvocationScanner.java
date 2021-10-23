package it.auties.reified.scanner;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleClasses;
import it.auties.reified.simplified.SimpleTypes;
import lombok.NonNull;

import javax.lang.model.element.ElementKind;
import java.util.Optional;

public class MethodInvocationScanner extends ReifiedScanner<ReifiedCall> {
    public MethodInvocationScanner(ReifiedDeclaration parameter, SimpleClasses simpleClasses, SimpleTypes simpleTypes) {
        super(parameter, simpleClasses, simpleTypes);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        var rawNode = (JCTree.JCMethodInvocation) node;
        var calling = resolveMethod(rawNode);
        if (calling.filter(this::isMatchingMethod).isEmpty()) {
            return super.visitMethodInvocation(node, unused);
        }

        results().add(buildCall(rawNode, calling.get()));
        return super.visitMethodInvocation(node, unused);
    }

    public Optional<Symbol.MethodSymbol> resolveMethod(@NonNull JCTree.JCMethodInvocation invocation) {
        var classEnv = simpleTypes().findClassEnv(enclosingClass());
        var methodEnv = simpleTypes().findMethodEnv(enclosingMethod(), classEnv);
        simpleTypes().resolveEnv(methodEnv);
        var symbol = TreeInfo.symbol(invocation.getMethodSelect());
        if (symbol.getKind() != ElementKind.METHOD) {
            return Optional.empty();
        }

        var method = (Symbol.MethodSymbol) symbol;
        if(!isMatchingMethod(method)){
            return Optional.empty();
        }
        
        return Optional.of(method);
    }

    private boolean isMatchingMethod(Symbol.MethodSymbol calling) {
        return parameter().methods()
                .stream()
                .anyMatch(method -> method.sym.equals(calling));
    }
}
