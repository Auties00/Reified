package it.auties.reified.model;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
@ToString
public class ReifiedCandidate {
    @NonNull Symbol.TypeVariableSymbol typeVariableSymbol;
    @NonNull JCTree.JCClassDecl enclosingClass;
    JCTree.JCMethodDecl enclosingMethod;

    @Override
    public boolean equals(Object other) {
        if(this == other){
            return true;
        }

        if(!(other instanceof ReifiedCandidate)){
            return false;
        }

        var otherCandidate = (ReifiedCandidate) other;
        return typeVariableSymbol.equals(otherCandidate.typeVariableSymbol);
    }
}
