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
public class ReifiedCall {
    @NonNull JCTree.JCPolyExpression invocation;
    @NonNull Symbol.MethodSymbol invoked;
    @NonNull JCTree.JCClassDecl enclosingClass;
    JCTree.JCMethodDecl enclosingMethod;
    JCTree.JCStatement enclosingStatement;
}
