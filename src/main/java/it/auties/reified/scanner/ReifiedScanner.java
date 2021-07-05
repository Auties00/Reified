package it.auties.reified.scanner;

import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.tree.JCTree;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedParameter;
import it.auties.reified.simplified.SimpleMethods;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Data
@Accessors(fluent = true)
public abstract class ReifiedScanner<R> extends TreeScanner<Void, Void> {
    private final ReifiedParameter parameter;
    private final SimpleMethods simpleMethods;
    private Set<ReifiedCall<R>> results;
    private JCTree.JCClassDecl enclosingClass;

    @Override
    public Void visitClass(ClassTree node, Void unused) {
        enclosingClass((JCTree.JCClassDecl) node);
        return super.visitClass(node, unused);
    }

    protected ReifiedCall<R> buildResultCall(JCTree.JCPolyExpression tree, R caller) {
        return new ReifiedCall<>(caller, tree, enclosingClass);
    }

    public Set<ReifiedCall<R>> scan(JCTree tree) {
        this.results = new HashSet<>();
        scan(tree, null);
        return results;
    }
}
