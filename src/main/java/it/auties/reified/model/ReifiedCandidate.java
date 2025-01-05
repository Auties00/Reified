package it.auties.reified.model;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;

import java.util.Objects;

public final class ReifiedCandidate {
    private final Symbol.TypeVariableSymbol typeVariable;
    private final JCTree.JCClassDecl enclosingClass;
    private final JCTree.JCMethodDecl enclosingMethod;

    public ReifiedCandidate(
            Symbol.TypeVariableSymbol typeVariable,
            JCTree.JCClassDecl enclosingClass,
            JCTree.JCMethodDecl enclosingMethod
    ) {
        this.typeVariable = typeVariable;
        this.enclosingClass = enclosingClass;
        this.enclosingMethod = enclosingMethod;
    }

    public boolean hasClass() {
        return Objects.isNull(enclosingMethod());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ReifiedCandidate that = (ReifiedCandidate) o;
        return Objects.equals(typeVariable, that.typeVariable) && Objects.equals(enclosingClass, that.enclosingClass) && Objects.equals(enclosingMethod, that.enclosingMethod);
    }

    public Symbol.TypeVariableSymbol typeVariable() {
        return typeVariable;
    }

    public JCTree.JCClassDecl enclosingClass() {
        return enclosingClass;
    }

    public JCTree.JCMethodDecl enclosingMethod() {
        return enclosingMethod;
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeVariable, enclosingClass, enclosingMethod);
    }

    @Override
    public String toString() {
        return "ReifiedCandidate[" +
                "typeVariable=" + typeVariable + ", " +
                "enclosingClass=" + enclosingClass + ", " +
                "enclosingMethod=" + enclosingMethod + ']';
    }
}
