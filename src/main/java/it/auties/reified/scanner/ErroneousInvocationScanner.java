package it.auties.reified.scanner;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.ListBuffer;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleClasses;
import it.auties.reified.simplified.SimpleTypes;

import java.util.*;

public class ErroneousInvocationScanner extends ReifiedScanner<Symbol.MethodSymbol> {
    public ErroneousInvocationScanner(SimpleClasses simpleClasses, SimpleTypes simpleTypes) {
        super(null, simpleClasses, simpleTypes);
    }

    @Override
    public Void visitNewClass(NewClassTree node, Void unused) {
        var rawTree = (JCTree.JCNewClass) node;
        return super.visitNewClass(node, unused);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
        var rawTree = (JCTree.JCMethodInvocation) node;
        var invoked = simpleClasses.resolveMethod(enclosingClass, enclosingMethod, rawTree);
        if(invoked.isPresent() && rawTree.type != null && rawTree.type.isErroneous()) {
            results.add(invoked.get());
        }
        return super.visitMethodInvocation(node, unused);
    }
}
