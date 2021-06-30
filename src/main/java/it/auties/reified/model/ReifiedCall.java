package it.auties.reified.model;

import com.sun.tools.javac.tree.JCTree;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
@Builder
@ToString
public class ReifiedCall {
    JCTree.JCPolyExpression caller;
    JCTree.JCStatement callerStatement;
    JCTree.JCMethodDecl enclosingMethod;
}
