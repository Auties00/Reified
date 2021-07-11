package it.auties.reified.simplified;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.List;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.util.StreamUtils;
import lombok.AllArgsConstructor;
import lombok.experimental.ExtensionMethod;

import javax.lang.model.element.Modifier;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

import static com.sun.tools.javac.code.Flags.GENERATEDCONSTR;

@AllArgsConstructor
@ExtensionMethod(StreamUtils.class)
public class SimpleClasses {
    private final SimpleTypes simpleTypes;
    private final Resolve resolve;

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

    public List<JCTree.JCMethodDecl> findConstructors(JCTree.JCClassDecl tree) {
        return tree.getMembers()
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
        var invokedTypeArgs = invoked.asElement().baseSymbol().getTypeParameters();
        var invocationTypeArgs = simpleTypes.flattenGenericType(invocation.clazz);
        if (invocationTypeArgs != null && !invocationTypeArgs.isEmpty()) {
            var deduced = simpleTypes.matchTypeParamToTypedArg(typeVariable, invokedTypeArgs, invocationTypeArgs, enclosingClass);
            if(deduced.isEmpty()){
                throw new IllegalArgumentException("Cannot resolve class type for explicit type variable");
            }

            return deduced.head;
        }

        var parameterType = resolveClassType(typeVariable, invocation, invoked, enclosingClass);
        var flatReturnType = simpleTypes.flattenGenericType(invoked.getTypeArguments()).iterator();
        var statementType = resolveClassType(typeVariable, flatReturnType, enclosingClass, enclosingMethod, enclosingStatement);
        return statementType
                .map(type -> resolveClassType(typeVariable, parameterType.orElse(null), type))
                .orElse(resolveClassType(typeVariable, parameterType.orElse(null)));
    }

    private Type resolveClassType(Symbol.TypeVariableSymbol typeVariable, Type parameterType, Type type) {
        if (!simpleTypes.isWildCard(type)) {
            return type;
        }

        return resolveClassType(typeVariable, parameterType);
    }

    private Type resolveClassType(Symbol.TypeVariableSymbol typeVariable, Type parameterType) {
        return Objects.requireNonNullElse(parameterType, simpleTypes.erase(typeVariable));
    }

    private Optional<Type> resolveClassType(Symbol.TypeVariableSymbol typeVariable, JCTree.JCNewClass invocation, Type.ClassType invoked, JCTree.JCClassDecl enclosingClass) {
        var invocationArgs = simpleTypes.resolveTypes(invocation.getArguments(), enclosingClass);
        var constructor = resolveConstructor(typeVariable, invocation, invoked, enclosingClass);
        var constructorParams = constructor.getParameters().subList(1, constructor.getParameters().size());
        var commonTypes = simpleTypes.matchTypeVariableSymbolToArgs(typeVariable, List.from(constructorParams), invocationArgs);
        return simpleTypes.commonType(commonTypes);
    }

    public Optional<Type> resolveClassType(Symbol.TypeVariableSymbol typeVariable, Iterator<Type> flatReturnType, JCTree.JCClassDecl enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCStatement enclosingStatement) {
        if (enclosingStatement instanceof JCTree.JCReturn) {
            var methodReturnType = simpleTypes.resolveClassType(enclosingMethod.getReturnType(), enclosingClass);
            if (methodReturnType.isEmpty()) {
                return Optional.empty();
            }

            var flatMethodReturn = simpleTypes.flattenGenericType(methodReturnType.get()).iterator();
            return simpleTypes.resolveImplicitType(flatMethodReturn, flatReturnType, typeVariable);
        }

        if (enclosingStatement instanceof JCTree.JCVariableDecl) {
            var variable = (JCTree.JCVariableDecl) enclosingStatement;
            if(variable.isImplicitlyTyped()){
                return Optional.empty();
            }

            var variableType = simpleTypes.resolveClassType(variable.vartype, enclosingClass);
            if (variableType.isEmpty()) {
                return Optional.empty();
            }

            var flatVariableType = simpleTypes.flattenGenericType(variableType.get()).iterator();
            return simpleTypes.resolveImplicitType(flatVariableType, flatReturnType, typeVariable);
        }

        return Optional.empty();
    }


    private Symbol.MethodSymbol resolveConstructor(Symbol.TypeVariableSymbol typeVariable, JCTree.JCNewClass invocation, Type.ClassType invoked, JCTree.JCClassDecl enclosingClass) {
        return resolve.resolveInternalConstructor(
                invocation.pos(),
                simpleTypes.findClassEnv(enclosingClass),
                simpleTypes.erase(invoked),
                simpleTypes.resolveTypes(invocation.getArguments(), enclosingClass).prepend(generatedParameter(typeVariable)),
                List.of(typeVariable.asType())
        );
    }

    private Type generatedParameter(Symbol.TypeVariableSymbol typeVariable){
        return simpleTypes.createTypeWithParameter(Class.class, typeVariable);
    }
}
