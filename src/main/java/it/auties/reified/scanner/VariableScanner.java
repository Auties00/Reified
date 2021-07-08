package it.auties.reified.scanner;

import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Name;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class VariableScanner extends TreeScanner<Void, Void> {
    private final Name identity;
    private JCTree.JCVariableDecl last;
    public VariableScanner(JCTree.JCIdent identity){
        this.identity = identity.getName();
    }

    @Override
    public Void visitVariable(VariableTree node, Void unused) {
        var rawNode = (JCTree.JCVariableDecl) node;
        if (rawNode.getName().contentEquals(identity)) {
            this.last = rawNode;
        }

        return super.visitVariable(node, unused);
    }

    public JCTree.JCVariableDecl scan(JCTree tree) {
        scan(tree, null);
        return last;
    }
}
