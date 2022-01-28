package it.auties.reified.simplified;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.List;

import static com.sun.tools.javac.code.Flags.GENERATEDCONSTR;

public record SimpleClasses(SimpleTypes simpleTypes) {
    public List<JCTree.JCMethodDecl> findConstructors(JCTree.JCClassDecl tree) {
        return tree.getMembers()
                .stream()
                .filter(TreeInfo::isConstructor)
                .map(constructor -> (JCTree.JCMethodDecl) constructor)
                .map(constructor -> removeDefaultConstructorFlag(tree, constructor))
                .collect(List.collector());
    }

    private JCTree.JCMethodDecl removeDefaultConstructorFlag(JCTree.JCClassDecl owner, JCTree.JCMethodDecl constructor) {
        if (simpleTypes.isRecord(owner.getModifiers())) {
            return constructor;
        }

        constructor.mods.flags &= ~GENERATEDCONSTR;
        return constructor;
    }
}
