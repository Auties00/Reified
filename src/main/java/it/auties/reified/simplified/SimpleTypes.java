package it.auties.reified.simplified;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import lombok.AllArgsConstructor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Objects;
import java.util.Optional;

import static com.sun.tools.javac.code.TypeTag.BOT;
import static com.sun.tools.javac.code.TypeTag.VOID;

@AllArgsConstructor
public class SimpleTypes {
    private final ProcessingEnvironment environment;
    private final Types types;
    private final Attr attr;
    private final Enter enter;

    public Type createTypeWithParameter(Class<?> clazz, Element parameter) {
        var types = environment.getTypeUtils();
        return (Type) types.getDeclaredType(toTypeElement(clazz), parameter.asType());
    }

    public TypeElement toTypeElement(Class<?> clazz) {
        var type = environment.getElementUtils().getTypeElement(clazz.getName());
        return Assert.checkNonNull(type, "Reified Methods: Cannot compile as the type element associated with the class " + clazz.getName() + " doesn't exist!");
    }

    public Optional<Symbol.ClassSymbol> resolveGenericType(JCTree.JCExpression expression, Env<AttrContext> env) {
        if (!(expression instanceof JCTree.JCTypeApply)) {
            return Optional.empty();
        }

        var genericType = (JCTree.JCTypeApply) expression;
        var genericArgs = genericType.getTypeArguments();
        return resolveFirstGenericType(genericArgs, env);
    }

    public Optional<Symbol.ClassSymbol> resolveFirstGenericType(List<JCTree.JCExpression> genericArgs, Env<AttrContext> env) {
        if (genericArgs.isEmpty()) {
            return Optional.empty();
        }

        return resolveGenericClassSymbol(genericArgs.get(0), env);
    }

    public Optional<Symbol.ClassSymbol> resolveGenericClassSymbol(JCTree.JCExpression expression, Env<AttrContext> env) {
        if (expression instanceof JCTree.JCIdent) {
            var sym = resolveClassSymbol((JCTree.JCIdent) expression, env);
            return Optional.of(sym);
        }

        if (expression instanceof JCTree.JCTypeApply) {
            return resolveGenericClassSymbol((JCTree.JCExpression) ((JCTree.JCTypeApply) expression).getType(), env);
        }

        return Optional.empty();
    }

    private Symbol.ClassSymbol resolveClassSymbol(JCTree.JCIdent expression, Env<AttrContext> env) {
        return (Symbol.ClassSymbol) attr.attribIdent(expression, env);
    }

    public Optional<Type> resolveClassType(JCTree.JCExpression argument, JCTree.JCClassDecl enclosingClass) {
        var env = enter.getClassEnv(enclosingClass.sym.asType().asElement());
        return Optional.ofNullable(attr.attribType(argument, env)).filter(this::isValid);
    }

    public Optional<Type> resolveExpressionType(JCTree.JCExpression argument, JCTree.JCClassDecl enclosingClass) {
        var env = enter.getClassEnv(enclosingClass.sym.asType().asElement());
        return resolveExpressionType(argument, env);
    }

    public Optional<Type> resolveExpressionType(JCTree.JCExpression argument, Env<AttrContext> methodEnv) {
        return Optional.ofNullable(attr.attribExpr(argument, methodEnv)).filter(this::isValid);
    }

    public boolean isValid(Type type) {
        return !type.isErroneous();
    }

    public Optional<Type> commonType(List<Type> input) {
        return Optional.ofNullable(types.lub(input))
                .filter(type -> type.getTag() != TypeTag.BOT)
                .filter(this::isValid);
    }

    public Type erase(Symbol.TypeVariableSymbol typeVariableSymbol) {
        return typeVariableSymbol.erasure(types);
    }

    public Type boxed(Type type){
        return types.boxedTypeOrType(type);
    }

    public Type resolveImplicitType(JCTree.JCVariableDecl variable, Env<AttrContext> env) {
        var initType = attr.attribExpr(variable.init, env);
        if (initType.hasTag(BOT) || initType.hasTag(VOID)) {
            throw new IllegalArgumentException("Cannot deduce type of void or null");
        }

        return types.upward(initType, types.captures(initType));
    }
}
