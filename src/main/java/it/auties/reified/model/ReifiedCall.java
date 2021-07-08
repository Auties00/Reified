package it.auties.reified.model;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
@ToString
public class ReifiedCall<T> {
    @NonNull JCTree.JCPolyExpression invocation;
    @NonNull T invoked;
    @NonNull JCTree.JCClassDecl enclosingClass;
    JCTree.JCMethodDecl enclosingMethod;
    JCTree.JCStatement enclosingStatement;
}
