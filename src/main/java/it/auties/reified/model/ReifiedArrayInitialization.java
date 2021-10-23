package it.auties.reified.model;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
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
    List<JCTree.JCExpression> enclosingExpressions;
}
