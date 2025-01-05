package it.auties.reified.model;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;

public class ReifiedArrayInitialization {
    private final JCTree.JCNewArray initialization;
    private final Symbol.TypeVariableSymbol typeVariableSymbol;
    private final JCTree.JCClassDecl enclosingClass;
    private final JCTree.JCMethodDecl enclosingMethod;
    private final JCTree.JCStatement enclosingStatement;
    private final List<JCTree.JCExpression> enclosingExpressions;
    public ReifiedArrayInitialization(JCTree.JCNewArray initialization, Symbol.TypeVariableSymbol typeVariableSymbol, JCTree.JCClassDecl enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCStatement enclosingStatement, List<JCTree.JCExpression> enclosingExpressions) {
        this.initialization = initialization;
        this.typeVariableSymbol = typeVariableSymbol;
        this.enclosingClass = enclosingClass;
        this.enclosingMethod = enclosingMethod;
        this.enclosingStatement = enclosingStatement;
        this.enclosingExpressions = enclosingExpressions;
    }

    public JCTree.JCNewArray initialization() {
        return initialization;
    }

    public Symbol.TypeVariableSymbol typeVariableSymbol() {
        return typeVariableSymbol;
    }

    public JCTree.JCClassDecl enclosingClass() {
        return enclosingClass;
    }

    public JCTree.JCMethodDecl enclosingMethod() {
        return enclosingMethod;
    }

    public JCTree.JCStatement enclosingStatement() {
        return enclosingStatement;
    }

    public List<JCTree.JCExpression> enclosingExpressions() {
        return enclosingExpressions;
    }
}
