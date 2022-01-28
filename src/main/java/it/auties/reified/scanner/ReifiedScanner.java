package it.auties.reified.scanner;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleClasses;
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
    private final SimpleClasses simpleClasses;
    private final SimpleTypes simpleTypes;

    private Set<T> results;
    private JCTree.JCClassDecl enclosingClass;
    private JCTree.JCMethodDecl enclosingMethod;
    private JCTree.JCStatement enclosingStatement;
    private JCTree.JCExpression enclosingExpression;

    @Override
    public Void scan(Tree tree, Void unused) {
        if (tree instanceof JCTree.JCStatement statement) {
            this.enclosingExpression = null;
            this.enclosingStatement = statement;
        }else if(tree instanceof JCTree.JCExpression expression){
            this.enclosingExpression = expression;
        }else {
            this.enclosingStatement = null;
        }

        return super.scan(tree, unused);
    }

    @Override
    public Void visitClass(ClassTree node, Void unused) {
        enclosingClass((JCTree.JCClassDecl) node);
        return super.visitClass(node, unused);
    }

    @Override
    public Void visitMethod(MethodTree node, Void unused) {
        Assert.checkNonNull(enclosingClass, "Cannot visit method outside of a class definition");
        enclosingMethod((JCTree.JCMethodDecl) node);
        return super.visitMethod(node, unused);
    }

    protected ReifiedCall buildCall(JCTree.JCPolyExpression tree, Symbol.MethodSymbol invoked) {
        return new ReifiedCall(parameter().typeParameter(), tree, invoked, enclosingClass, enclosingMethod, enclosingStatement);
    }

    public List<T> scan(JCTree tree) {
        this.results = new HashSet<>();
        scan(tree, null);
        return List.from(results);
    }
}
