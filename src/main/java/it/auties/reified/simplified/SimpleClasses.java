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
    private final SimpleTrees simpleTrees;
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

    public Type resolveClassType(Symbol.TypeVariableSymbol parameter, Type.ClassType type) {
        var typeArguments = type.getTypeArguments();
        if(typeArguments.isEmpty()){
            return simpleTypes.erase(parameter);
        }

        return typeArguments.stream()
                .filter(arg -> arg.asElement().baseSymbol().equals(parameter))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cannot resolve class type: missing type parameter"));
    }
}
