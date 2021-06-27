package it.auties.reified.simplified;

import com.sun.source.tree.Scope;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import it.auties.reified.model.ReifiedParameter;
import lombok.AllArgsConstructor;

import javax.lang.model.element.Modifier;

@AllArgsConstructor
public class SimpleClasses {
    private final SimpleTrees simpleTrees;
    public ReifiedParameter.AccessModifier findRealAccess(Tree tree, Scope scope, boolean classScoped){
        if (classScoped) {
            return findRealAccess((JCTree.JCClassDecl) tree);
        }

        var clazz = simpleTrees.toTree(scope.getEnclosingClass());
        var classMod = findRealAccess((JCTree.JCClassDecl) clazz);
        var methodMod = ((JCTree.JCMethodDecl) tree).getModifiers().getFlags();
        if(methodMod.contains(Modifier.PRIVATE)){
            return ReifiedParameter.AccessModifier.PRIVATE;
        }

        if(methodMod.contains(Modifier.PROTECTED)){
            return ReifiedParameter.AccessModifier.PROTECTED;
        }

        if(classMod == ReifiedParameter.AccessModifier.PACKAGE_PRIVATE){
            return ReifiedParameter.AccessModifier.PACKAGE_PRIVATE;
        }

        return ReifiedParameter.AccessModifier.PUBLIC;
    }

    private ReifiedParameter.AccessModifier findRealAccess(JCTree.JCClassDecl method){
        var methodMod = method.getModifiers().getFlags();
        if(methodMod.contains(Modifier.PUBLIC)){
            return ReifiedParameter.AccessModifier.PUBLIC;
        }

        if(methodMod.contains(Modifier.PRIVATE)){
            return ReifiedParameter.AccessModifier.PRIVATE;
        }

        if(methodMod.contains(Modifier.PROTECTED)){
            return ReifiedParameter.AccessModifier.PROTECTED;
        }

        return ReifiedParameter.AccessModifier.PACKAGE_PRIVATE;
    }

    public List<JCTree.JCMethodDecl> findConstructors(JCTree tree){
        var clazz = (JCTree.JCClassDecl) tree;
        return clazz.getMembers()
                .stream()
                .filter(e -> e instanceof JCTree.JCMethodDecl)
                .map(e -> (JCTree.JCMethodDecl) e)
                .filter(e -> e.getName().contentEquals("<init>"))
                .collect(List.collector());
    }
}
