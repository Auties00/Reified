package it.auties.reified.scanner;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.ListBuffer;
import it.auties.reified.model.ReifiedArrayInitialization;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleClasses;
import it.auties.reified.simplified.SimpleTypes;

import java.util.HashSet;
import java.util.Set;

public abstract class ReifiedScanner<T> extends TreeScanner<Void, Void> {
    protected final ReifiedDeclaration parameter;
    protected final SimpleClasses simpleClasses;
    protected final SimpleTypes simpleTypes;

    protected Set<T> results;
    protected JCTree.JCClassDecl enclosingClass;
    protected JCTree.JCMethodDecl enclosingMethod;
    protected JCTree.JCStatement enclosingStatement;
    protected ListBuffer<JCTree.JCExpression> enclosingExpressions;

    protected ReifiedScanner(ReifiedDeclaration parameter, SimpleClasses simpleClasses, SimpleTypes simpleTypes) {
        this.parameter = parameter;
        this.simpleClasses = simpleClasses;
        this.simpleTypes = simpleTypes;
    }

    @Override
    public Void scan(Tree tree, Void unused) {
        if (tree instanceof JCTree.JCStatement) {
            enclosingExpressions.clear();
            this.enclosingStatement = (JCTree.JCStatement) tree;
        }else if(tree instanceof JCTree.JCExpression){
            enclosingExpressions.add((JCTree.JCExpression) tree);
        }

        return super.scan(tree, unused);
    }

    @Override
    public Void visitClass(ClassTree node, Void unused) {
        this.enclosingClass = (JCTree.JCClassDecl) node;
        return super.visitClass(node, unused);
    }

    @Override
    public Void visitMethod(MethodTree node, Void unused) {
        Assert.checkNonNull(enclosingClass, "Cannot visit method outside of a class definition");
        this.enclosingMethod = (JCTree.JCMethodDecl) node;
        return super.visitMethod(node, unused);
    }

    protected ReifiedCall buildCall(JCTree.JCPolyExpression tree, Symbol.MethodSymbol invoked) {
        return new ReifiedCall(parameter.typeParameter(), tree, invoked, enclosingClass, enclosingMethod, enclosingStatement);
    }

    protected ReifiedArrayInitialization buildArrayInit(JCTree.JCNewArray tree, Symbol.TypeVariableSymbol typeVariableSymbol) {
        return new ReifiedArrayInitialization(tree, typeVariableSymbol, enclosingClass, enclosingMethod, enclosingStatement, enclosingExpressions.toList());
    }

    public Set<T> scan(JCTree tree) {
        this.results = new HashSet<>();
        this.enclosingExpressions = new ListBuffer<>();
        scan(tree, null);
        return results;
    }
}
