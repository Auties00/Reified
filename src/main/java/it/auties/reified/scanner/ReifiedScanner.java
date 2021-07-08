package it.auties.reified.scanner;

import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Assert;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleMethods;
import it.auties.reified.simplified.SimpleTypes;
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
public abstract class ReifiedScanner<T> extends TreeScanner<Void, Void> {
    private final ReifiedDeclaration parameter;
    private final SimpleMethods simpleMethods;
    private final SimpleTypes simpleTypes;

    private Set<ReifiedCall<T>> results;
    private JCTree.JCClassDecl enclosingClass;
    private JCTree.JCMethodDecl enclosingMethod;
    private JCTree.JCStatement enclosingStatement;

    @Override
    public Void scan(Tree tree, Void unused) {
        if(tree instanceof JCTree.JCStatement){
            enclosingStatement(null);
        }

        return super.scan(tree, unused);
    }

    @Override
    public Void visitClass(ClassTree node, Void unused) {
        enclosingClass((JCTree.JCClassDecl) node);
        enclosingMethod(null);
        enclosingStatement(null);
        return super.visitClass(node, unused);
    }

    @Override
    public Void visitMethod(MethodTree node, Void unused) {
        Assert.checkNonNull(enclosingClass, "Cannot visit method outside of a class definition");
        enclosingMethod((JCTree.JCMethodDecl) node);
        enclosingStatement(null);
        return super.visitMethod(node, unused);
    }

    @Override
    public Void visitReturn(ReturnTree node, Void unused) {
        enclosingStatement((JCTree.JCReturn) node);
        return super.visitReturn(node, unused);
    }

    @Override
    public Void visitVariable(VariableTree node, Void unused) {
        enclosingStatement((JCTree.JCVariableDecl) node);
        return super.visitVariable(node, unused);
    }

    protected ReifiedCall<T> buildResultCall(JCTree.JCPolyExpression tree, T invoked) {
        return new ReifiedCall<T>(tree, invoked, enclosingClass, enclosingMethod, enclosingStatement);
    }

    public Set<ReifiedCall<T>> scan(JCTree tree) {
        this.results = new HashSet<>();
        scan(tree, null);
        return results;
    }
}
