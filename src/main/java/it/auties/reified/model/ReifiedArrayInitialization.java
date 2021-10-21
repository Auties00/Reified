package it.auties.reified.model;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import it.auties.reified.simplified.SimpleTypes;
import lombok.*;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
@ToString
public class ReifiedArrayInitialization {
    @NonNull JCTree.JCNewArray initialization;
    @NonNull Symbol.TypeVariableSymbol typeVariableSymbol;
    @NonNull JCTree.JCClassDecl enclosingClass;
    JCTree.JCMethodDecl enclosingMethod;
    JCTree.JCStatement enclosingStatement;

    public boolean hasEnclosingStatement(){
        return enclosingStatement() != null;
    }
}
