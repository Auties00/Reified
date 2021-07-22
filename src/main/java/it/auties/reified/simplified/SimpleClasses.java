package it.auties.reified.simplified;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.List;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.util.StreamUtils;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.ExtensionMethod;

import javax.lang.model.element.Modifier;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

import static com.sun.tools.javac.code.Flags.GENERATEDCONSTR;
import static com.sun.tools.javac.code.Flags.RECORD;

@AllArgsConstructor
@ExtensionMethod(StreamUtils.class)
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

    public List<JCTree.JCMethodDecl> findConstructors(JCTree.JCClassDecl tree) {
        return tree.getMembers()
                .stream()
                .filter(TreeInfo::isConstructor)
                .map(constructor -> (JCTree.JCMethodDecl) constructor)
                .map(constructor -> removeDefaultConstructorFlag(tree, constructor))
                .collect(List.collector());
    }

    private JCTree.JCMethodDecl removeDefaultConstructorFlag(JCTree.JCClassDecl owner, JCTree.JCMethodDecl constructor) {
        if(simpleTypes.isRecord(owner.getModifiers())){
            return constructor;
        }

        constructor.mods.flags &= ~GENERATEDCONSTR;
        return constructor;
    }

    public Optional<Symbol.MethodSymbol> resolveClass(@NonNull JCTree.JCClassDecl enclosingClass, JCTree.JCMethodDecl enclosingMethod, @NonNull JCTree.JCNewClass invocation) {
        var classEnv = simpleTypes.findClassEnv(enclosingClass);
        var methodEnv = simpleTypes.findMethodEnv(enclosingMethod, classEnv);
        simpleTypes.resolveEnv(methodEnv);
        if (!(invocation.constructor instanceof Symbol.MethodSymbol)) {
            return Optional.empty();
        }

        return Optional.of((Symbol.MethodSymbol) invocation.constructor);
    }

    public Type resolveClassType(Symbol.TypeVariableSymbol typeVariable, JCTree.JCNewClass invocation, Symbol.MethodSymbol invoked, JCTree.JCClassDecl enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCStatement enclosingStatement) {
        var classType = invoked.enclClass();
        var invokedTypeArgs = classType.getTypeParameters();
        var invocationTypeArgs = simpleTypes.flattenGenericType(invocation.getIdentifier());
        if (invocationTypeArgs != null && !invocationTypeArgs.isEmpty()) {
            var deduced = simpleTypes.eraseTypeVariableFromTypeParameters(typeVariable, invokedTypeArgs, invocationTypeArgs, enclosingClass);
            if(deduced.isEmpty()){
                throw new IllegalArgumentException("Cannot resolve class type for explicit type variable");
            }

            return simpleTypes.resolveWildCard(deduced.head);
        }

        var parameterType = resolveClassType(typeVariable, invocation, invoked, enclosingClass);
        var flatReturnType = simpleTypes.flattenGenericType(classType.asType().getTypeArguments()).iterator();
        var statementType = resolveClassType(typeVariable, flatReturnType, enclosingClass, enclosingMethod, enclosingStatement);
        return statementType
                .map(type -> resolveClassType(typeVariable, parameterType, type))
                .orElse(resolveClassType(typeVariable, parameterType));
    }

    private Type resolveClassType(Symbol.TypeVariableSymbol typeVariable, Type parameterType, Type type) {
        if (simpleTypes.isNotWildCard(type)) {
            return type;
        }

        return resolveClassType(typeVariable, parameterType);
    }

    private Type resolveClassType(Symbol.TypeVariableSymbol typeVariable, Type parameterType) {
        return Objects.requireNonNullElse(simpleTypes.resolveWildCard(parameterType), simpleTypes.erase(typeVariable));
    }

    private Type resolveClassType(Symbol.TypeVariableSymbol typeVariable, JCTree.JCNewClass invocation, Symbol.MethodSymbol constructor, JCTree.JCClassDecl enclosingClass) {
        var invocationArgs = simpleTypes.resolveTypes(invocation.getArguments(), enclosingClass);
        var constructorParams = constructor.getParameters();
        var commonTypes = simpleTypes.eraseTypeVariableFromArguments(typeVariable, List.from(constructorParams), invocationArgs, constructor.isVarArgs());
        return simpleTypes.commonType(commonTypes);
    }

    public Optional<Type> resolveClassType(Symbol.TypeVariableSymbol typeVariable, Iterator<Type> flatReturnType, JCTree.JCClassDecl enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCStatement enclosingStatement) {
        if (enclosingStatement instanceof JCTree.JCReturn) {
            var env = simpleTypes.findClassEnv(enclosingClass);
            var methodReturnType = simpleTypes.resolveClassType(enclosingMethod.getReturnType(), env);
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

            var env = simpleTypes.findClassEnv(enclosingClass);
            var variableType = simpleTypes.resolveClassType(variable.vartype, env);
            if (variableType.isEmpty()) {
                return Optional.empty();
            }

            var flatVariableType = simpleTypes.flattenGenericType(variableType.get()).iterator();
            return simpleTypes.resolveImplicitType(flatVariableType, flatReturnType, typeVariable);
        }

        return Optional.empty();
    }

    public boolean isAssignable(Type classType, Type assign){
        return simpleTypes.isAssignable(classType, assign);
    }
}
