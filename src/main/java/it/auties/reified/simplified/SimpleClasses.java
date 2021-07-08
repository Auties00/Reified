package it.auties.reified.simplified;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.List;
import it.auties.reified.model.ReifiedDeclaration;
import lombok.AllArgsConstructor;

import javax.lang.model.element.Modifier;

import static com.sun.tools.javac.code.Flags.GENERATEDCONSTR;

@AllArgsConstructor
public class SimpleClasses {
    private final SimpleTypes simpleTypes;

    public ReifiedDeclaration.AccessModifier findRealAccess(JCTree.JCClassDecl clazz, JCTree.JCMethodDecl method) {
        if (method == null) {
            return findRealAccess(clazz);
        }

        var classMod = findRealAccess(clazz);
        var methodMod = method.getModifiers().getFlags();
        if (methodMod.contains(Modifier.PRIVATE)) {
            return ReifiedDeclaration.AccessModifier.PRIVATE;
        }

        if (methodMod.contains(Modifier.PROTECTED)) {
            return ReifiedDeclaration.AccessModifier.PROTECTED;
        }

        if (classMod == ReifiedDeclaration.AccessModifier.PACKAGE_PRIVATE) {
            return ReifiedDeclaration.AccessModifier.PACKAGE_PRIVATE;
        }

        return ReifiedDeclaration.AccessModifier.PUBLIC;
    }

    private ReifiedDeclaration.AccessModifier findRealAccess(JCTree.JCClassDecl method) {
        var methodMod = method.getModifiers().getFlags();
        if (methodMod.contains(Modifier.PUBLIC)) {
            return ReifiedDeclaration.AccessModifier.PUBLIC;
        }

        if (methodMod.contains(Modifier.PRIVATE)) {
            return ReifiedDeclaration.AccessModifier.PRIVATE;
        }

        if (methodMod.contains(Modifier.PROTECTED)) {
            return ReifiedDeclaration.AccessModifier.PROTECTED;
        }

        return ReifiedDeclaration.AccessModifier.PACKAGE_PRIVATE;
    }

    public List<JCTree.JCMethodDecl> findConstructors(JCTree tree) {
        var clazz = (JCTree.JCClassDecl) tree;
        return clazz.getMembers()
                .stream()
                .filter(method -> method instanceof JCTree.JCMethodDecl)
                .map(method -> (JCTree.JCMethodDecl) method)
                .filter(TreeInfo::isConstructor)
                .map(this::removeDefaultConstructorFlag)
                .collect(List.collector());
    }

    private JCTree.JCMethodDecl removeDefaultConstructorFlag(JCTree.JCMethodDecl constructor) {
        constructor.mods.flags &= ~GENERATEDCONSTR;
        return constructor;
    }

    public Type resolveClassType(Symbol.TypeVariableSymbol typeVariable, JCTree.JCNewClass invocation, Type.ClassType invoked, JCTree.JCClassDecl enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCStatement enclosingStatement) {
        var args = simpleTypes.flattenGenericType(invocation.clazz);
        if (args != null && !args.isEmpty()) {
            var deduced = simpleTypes.matchTypeParamToTypedArg(typeVariable, invoked.asElement().baseSymbol().getTypeParameters(), args, enclosingClass);
            if(deduced.isEmpty()){
                throw new IllegalArgumentException("Cannot resolve class type for explicit type variable");
            }

            return deduced.get(0);
        }

        var parametrizedArgs = simpleTypes.matchTypeParamToTypedArg(typeVariable, invoked.getParameterTypes().stream().map(e -> (Symbol.TypeVariableSymbol) e.tsym.baseSymbol()).collect(List.collector()), invocation.getArguments(), enclosingClass);
        var parameterType = simpleTypes.commonType(parametrizedArgs);
        if(parameterType.isPresent()){
            return parameterType.get();
        }

        var flatReturnType = simpleTypes.flattenGenericType(invoked.getTypeArguments()).iterator();
        if (enclosingStatement instanceof JCTree.JCReturn) {
            var methodReturnType = simpleTypes.resolveClassType(enclosingMethod.getReturnType(), enclosingClass);
            if (methodReturnType.isEmpty()) {
                return simpleTypes.erase(typeVariable);
            }

            var flatMethodReturn = simpleTypes.flattenGenericType(methodReturnType.get()).iterator();
            return simpleTypes.resolveImplicitType(flatMethodReturn, flatReturnType, typeVariable)
                    .orElse(simpleTypes.erase(typeVariable));
        }

        if (enclosingStatement instanceof JCTree.JCVariableDecl) {
            var variable = (JCTree.JCVariableDecl) enclosingStatement;
            if(variable.isImplicitlyTyped()){
                return simpleTypes.erase(typeVariable);
            }

            var variableType = simpleTypes.resolveClassType(variable.vartype, enclosingClass);
            if (variableType.isEmpty()) {
                return simpleTypes.erase(typeVariable);
            }

            var flatVariableType = simpleTypes.flattenGenericType(variableType.get()).iterator();
            return simpleTypes.resolveImplicitType(flatVariableType, flatReturnType, typeVariable)
                    .orElse(simpleTypes.erase(typeVariable));
        }

        return simpleTypes.erase(typeVariable);
    }
}
