package it.auties.reified.simplified;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.List;
import it.auties.reified.model.ReifiedDeclaration;

import javax.lang.model.element.Modifier;
import java.util.Optional;

import static com.sun.tools.javac.code.Flags.GENERATEDCONSTR;

public class SimpleClasses {
    private final SimpleTypes simpleTypes;
    public SimpleClasses(SimpleTypes simpleTypes) {
        this.simpleTypes = simpleTypes;
    }

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
        if (simpleTypes.record(owner.getModifiers())) {
            return constructor;
        }

        constructor.mods.flags &= ~GENERATEDCONSTR;
        return constructor;
    }

    public Optional<Symbol.MethodSymbol> findAndResolveConstructor(JCTree.JCClassDecl enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCNewClass invocation) {
        var classEnv = simpleTypes.findClassEnv(enclosingClass);
        var methodEnv = simpleTypes.findMethodEnv(enclosingMethod, classEnv);
        simpleTypes.resolveEnv(methodEnv);
        if (!(invocation.constructor instanceof Symbol.MethodSymbol)) { // Do not use Symbol#getKind, might be erroneous, but it is right
            return Optional.empty();
        }

        return Optional.of((Symbol.MethodSymbol) invocation.constructor);
    }

    public boolean assignable(Type classType, Type assign) {
        return simpleTypes.assignable(classType, assign);
    }

    public Optional<Symbol.MethodSymbol> resolveMethod(JCTree.JCClassDecl enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation) {
        var classEnv = simpleTypes.findClassEnv(enclosingClass);
        var methodEnv = simpleTypes.findMethodEnv(enclosingMethod, classEnv);
        simpleTypes.resolveEnv(methodEnv);
        var symbol = TreeInfo.symbol(invocation.getMethodSelect());
        if (!(symbol instanceof Symbol.MethodSymbol)) {
            return Optional.empty();
        }

        var method = (Symbol.MethodSymbol) symbol;
        return Optional.of(method);
    }
}
