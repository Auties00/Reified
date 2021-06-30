package it.auties.reified.simplified;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Optional;

@AllArgsConstructor
public class SimpleTypes {
    private final ProcessingEnvironment environment;
    private final Attr attr;
    private final Resolve resolve;

    public Type createTypeWithParameter(Class<?> clazz, Element parameter){
        var types = environment.getTypeUtils();
        return (Type) types.getDeclaredType(toTypeElement(clazz), parameter.asType());
    }

    public TypeElement toTypeElement(Class<?> clazz) {
        var type = environment.getElementUtils().getTypeElement(clazz.getName());
        return Objects.requireNonNull(type, "Reified Methods: Cannot compile as the type element associated with the class " + clazz.getName() + " doesn't exist!");
    }

    public Optional<Symbol.ClassSymbol> resolveGenericType(JCTree.JCExpression expression, Env<AttrContext> env){
        if (!(expression instanceof JCTree.JCTypeApply)) {
            return Optional.empty();
        }

        var genericType = (JCTree.JCTypeApply) expression;
        var genericArgs = genericType.getTypeArguments();
        return resolveFirstGenericType(genericArgs, env);
    }

    public Optional<Symbol.ClassSymbol> resolveFirstGenericType(List<JCTree.JCExpression> genericArgs, Env<AttrContext> env) {
        if(genericArgs.isEmpty()) {
            return Optional.empty();
        }

        return resolveGenericClassSymbol(genericArgs.get(0), env);
    }

    public Optional<Symbol.ClassSymbol> resolveGenericClassSymbol(JCTree.JCExpression expression, Env<AttrContext> env){
        if(expression instanceof JCTree.JCIdent){
            var sym = resolveClassSymbol((JCTree.JCIdent) expression, env);
            return Optional.of(sym);
        }

        if(expression instanceof JCTree.JCTypeApply){
            return resolveGenericClassSymbol((JCTree.JCExpression) ((JCTree.JCTypeApply) expression).getType(), env);
        }

        return Optional.empty();
    }

    @SneakyThrows
    private Symbol.ClassSymbol resolveClassSymbol(JCTree.JCIdent expression, Env<AttrContext> env) {
        var resolveClass = resolve.getClass();
        var resolveIdentityMethod = resolveClass.getDeclaredMethod("resolveIdent", JCDiagnostic.DiagnosticPosition.class, Env.class, Name.class, Kinds.KindSelector.class);
        resolveIdentityMethod.setAccessible(true);
        return (Symbol.ClassSymbol) resolveIdentityMethod.invoke(resolve, expression.pos(), env, expression.getName(), Kinds.KindSelector.VAL_TYP);
    }

    public Type resolveType(JCTree.JCExpression argument, Env<AttrContext> methodEnv) {
        return attr.attribExpr(argument, methodEnv);
    }
}
