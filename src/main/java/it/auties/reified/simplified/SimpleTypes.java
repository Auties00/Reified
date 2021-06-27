package it.auties.reified.simplified;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Objects;
import java.util.Optional;

@AllArgsConstructor
public class SimpleTypes {
    private final ProcessingEnvironment environment;
    private final Resolve resolve;
    private final Enter enter;
    public Type createTypeWithParameter(Class<?> clazz, Element parameter){
        var types = environment.getTypeUtils();
        return (Type) types.getDeclaredType(toTypeElement(clazz), parameter.asType());
    }

    public TypeElement toTypeElement(Class<?> clazz) {
        var type = environment.getElementUtils().getTypeElement(clazz.getName());
        return Objects.requireNonNull(type, "Reified Methods: Cannot compile as the type element associated with the class " + clazz.getName() + " doesn't exist!");
    }

    public Optional<Name> resolveGenericType(JCTree.JCExpression expression, Env<AttrContext> env){
        if(expression instanceof JCTree.JCIdent){
            return Optional.empty();
        }

        if(expression instanceof JCTree.JCTypeApply){
            var genericType = (JCTree.JCTypeApply) expression;
            var genericArgs = genericType.getTypeArguments();
            return resolveGenericArgument(genericArgs.get(0), env);
        }

        return Optional.empty();
    }

    @SneakyThrows
    private Optional<Symbol.ClassSymbol> resolveGenericArgument(JCTree.JCExpression expression, Env<AttrContext> env){
        if(expression instanceof JCTree.JCIdent){
            var resolveClass = resolve.getClass();
            var resolveIdentityMethod = resolveClass.getDeclaredMethod("resolveIdent", JCDiagnostic.DiagnosticPosition.class, Env.class, Name.class, Kinds.KindSelector.class);
            var sym = resolveIdentityMethod.invoke(resolve, expression.pos(), env, ((JCTree.JCIdent) expression).getName(), Kinds.KindSelector.
            return Optional.of((Symbol.ClassSymbol) ((JCTree.JCIdent) expression).sym);
        }

        if(expression instanceof JCTree.JCTypeApply){
            return resolveGenericArgument((JCTree.JCExpression) ((JCTree.JCTypeApply) expression).getType());
        }

        return Optional.empty();
    }
    public Env<AttrContext> findEnvironment(Element element) {
        return enter.getTopLevelEnv((JCTree.JCCompilationUnit) trees.getPath(element).getCompilationUnit());
    }

}
