package it.auties.reified.model;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;

import java.util.Objects;

public final class ReifiedDeclaration {
    private final Symbol.TypeVariableSymbol typeParameter;
    private final JCTree.JCClassDecl enclosingClass;
    private final AccessModifier accessModifier;
    private final List<JCTree.JCMethodDecl> methods;
    private final boolean isClass;

    public ReifiedDeclaration(
            Symbol.TypeVariableSymbol typeParameter,
            JCTree.JCClassDecl enclosingClass,
            AccessModifier accessModifier,
            List<JCTree.JCMethodDecl> methods,
            boolean isClass
    ) {
        this.typeParameter = typeParameter;
        this.enclosingClass = enclosingClass;
        this.accessModifier = accessModifier;
        this.methods = methods;
        this.isClass = isClass;
    }

    public Symbol.TypeVariableSymbol typeParameter() {
        return typeParameter;
    }

    public JCTree.JCClassDecl enclosingClass() {
        return enclosingClass;
    }

    public AccessModifier modifier() {
        return accessModifier;
    }

    public List<JCTree.JCMethodDecl> methods() {
        return methods;
    }

    public boolean isClass() {
        return isClass;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ReifiedDeclaration) obj;
        return Objects.equals(this.typeParameter, that.typeParameter) &&
                Objects.equals(this.enclosingClass, that.enclosingClass) &&
                Objects.equals(this.methods, that.methods) &&
                this.isClass == that.isClass;
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeParameter, enclosingClass, methods, isClass);
    }

    @Override
    public String toString() {
        return "ReifiedDeclaration[" +
                "typeParameter=" + typeParameter + ", " +
                "enclosingClass=" + enclosingClass + ", " +
                "methods=" + methods + ", " +
                "isClass=" + isClass + ']';
    }

    public enum AccessModifier {
        PUBLIC,
        PRIVATE,
        PROTECTED,
        PACKAGE_PRIVATE
    }
}
