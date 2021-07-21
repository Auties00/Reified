package it.auties.reified.model;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@Data
@Accessors(fluent = true)
@ToString
public class ReifiedCall {
    private final @NonNull JCTree.JCPolyExpression invocation;
    private final @NonNull Symbol.MethodSymbol invoked;
    private final @NonNull JCTree.JCClassDecl enclosingClass;
    private final JCTree.JCMethodDecl enclosingMethod;
    private final JCTree.JCStatement enclosingStatement;
    private Type reifiedType;
}
