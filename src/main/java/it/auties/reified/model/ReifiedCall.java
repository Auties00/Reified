package it.auties.reified.model;

import com.sun.tools.javac.tree.JCTree;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
@ToString
public class ReifiedCall<T> {
    @NonNull T caller;
    @NonNull JCTree.JCPolyExpression callerExpression;
    @NonNull JCTree.JCClassDecl callerEnclosingClass;
}
