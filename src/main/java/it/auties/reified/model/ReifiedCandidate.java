package it.auties.reified.model;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import lombok.NonNull;

import java.util.Objects;

public record ReifiedCandidate(@NonNull Symbol.TypeVariableSymbol typeVariable,
                               @NonNull JCTree.JCClassDecl enclosingClass,
                               JCTree.JCMethodDecl enclosingMethod) {
    public boolean hasClass() {
        return Objects.isNull(enclosingMethod());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReifiedCandidate that
                && typeVariable.equals(that.typeVariable);
    }
}
