package it.auties.reified.scanner;

import com.sun.source.tree.*;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Assert;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleClasses;
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
public abstract class ReifiedScanner extends TreeScanner<Void, Void> {
    private final ReifiedDeclaration parameter;
    private final SimpleClasses simpleClasses;
    private final SimpleMethods simpleMethods;

    private Set<ReifiedCall> results;
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

    protected ReifiedCall buildResultCall(JCTree.JCPolyExpression tree, Symbol.MethodSymbol invoked) {
        return new ReifiedCall(tree, invoked, enclosingClass, enclosingMethod, enclosingStatement);
    }

    public Set<ReifiedCall> scan(JCTree tree) {
        this.results = new HashSet<>();
        scan(tree, null);
        return results;
    }
}
