package it.auties.reified.scanner;

import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.tree.JCTree;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class VariableScanner extends TreeScanner<Void, Void> {
    private final JCTree.JCIdent identity;
    private JCTree.JCVariableDecl last;

    @Override
    public Void visitVariable(VariableTree node, Void unused) {
        var rawNode = (JCTree.JCVariableDecl) node;
        if (rawNode.getName().contentEquals(identity.getName())) {
            this.last = rawNode;
        }

        return super.visitVariable(node, unused);
    }

    public JCTree.JCVariableDecl scan(JCTree tree) {
        scan(tree, null);
        return last;
    }
}
