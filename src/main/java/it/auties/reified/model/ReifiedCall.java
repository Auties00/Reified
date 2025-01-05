package it.auties.reified.model;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;

public class ReifiedCall {
    private final Symbol.TypeVariableSymbol typeVariable;
    private final JCTree.JCPolyExpression invocation;
    private final Symbol.MethodSymbol invoked;
    private final JCTree.JCClassDecl enclosingClass;
    private final JCTree.JCMethodDecl enclosingMethod;
    private final JCTree.JCStatement enclosingStatement;
    private Type reifiedType;

    public ReifiedCall(Symbol.TypeVariableSymbol typeVariable, JCTree.JCPolyExpression invocation, Symbol.MethodSymbol invoked, JCTree.JCClassDecl enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCStatement enclosingStatement) {
        this.typeVariable = typeVariable;
        this.invocation = invocation;
        this.invoked = invoked;
        this.enclosingClass = enclosingClass;
        this.enclosingMethod = enclosingMethod;
        this.enclosingStatement = enclosingStatement;
    }

    public Symbol.TypeVariableSymbol typeVariable() {
        return typeVariable;
    }

    public JCTree.JCPolyExpression invocation() {
        return invocation;
    }

    public Symbol.MethodSymbol invoked() {
        return invoked;
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

    public Type reifiedType() {
        return reifiedType;
    }

    public void setReifiedType(Type reifiedType) {
        this.reifiedType = reifiedType;
    }
}
