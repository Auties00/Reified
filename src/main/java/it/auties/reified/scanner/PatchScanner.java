package it.auties.reified.scanner;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.tree.JCTree;

public class PatchScanner extends TreeScanner<Void, Void> {
    private final Symtab symtab;
    public PatchScanner(Symtab symtab) {
        this.symtab = symtab;
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        var invocation = (JCTree.JCMethodInvocation) node;
        if(invocation.meth.type == null || invocation.meth.type.isErroneous()){
            invocation.meth.type = symtab.unknownType;
        }

        return super.visitMethodInvocation(node, unused);
    }

    public void scan(JCTree tree) {
       scan(tree, null);
    }
}
