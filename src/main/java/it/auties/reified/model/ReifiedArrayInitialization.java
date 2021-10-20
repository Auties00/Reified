package it.auties.reified.model;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import it.auties.reified.simplified.SimpleTypes;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@Data
@Accessors(fluent = true)
@ToString
public class ReifiedArrayInitialization {
    private final @NonNull JCTree.JCNewArray initialization;
    private final @NonNull Symbol.TypeVariableSymbol typeVariableSymbol;
    private final @NonNull JCTree.JCClassDecl enclosingClass;
    private final JCTree.JCMethodDecl enclosingMethod;
    private final JCTree.JCStatement enclosingStatement;
}
