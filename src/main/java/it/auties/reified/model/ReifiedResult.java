package it.auties.reified.model;

import com.sun.tools.javac.tree.JCTree;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class ReifiedResult<T extends JCTree.JCPolyExpression> {
    T caller;
    JCTree.JCExpression generated;
}
