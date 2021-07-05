package it.auties.reified.simplified;

import com.sun.source.tree.Scope;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Assert;
import lombok.AllArgsConstructor;

import javax.lang.model.element.Element;
import java.util.Objects;

@AllArgsConstructor
public class SimpleTrees {
    private final Trees trees;

    public JCTree toTree(Element element) {
        return (JCTree) Assert.checkNonNull(trees.getTree(element), "Reified Methods: Cannot compile as the tree associated with the element " + element.getSimpleName() + " doesn't exist!");
    }

    public TreePath toPath(Element tree) {
        return Assert.checkNonNull(trees.getPath(tree), "Reified Methods: Cannot compile as the path associated with the tree " + tree + " doesn't exist!");
    }

    public Scope findScope(TreePath path) {
        return Assert.checkNonNull(trees.getScope(path), "Reified Methods: Cannot compile as the scope associated with the path " + path + " doesn't exist!");
    }

    public Element findParent(Scope scope) {
        return Objects.requireNonNullElse(scope.getEnclosingMethod(), scope.getEnclosingClass());
    }
}
