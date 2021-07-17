package it.auties.reified.scanner;

import com.sun.source.tree.NewClassTree;
import com.sun.tools.javac.tree.JCTree;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleClasses;
import it.auties.reified.simplified.SimpleMethods;

public class ClassInitializationScanner extends ReifiedScanner {
    public ClassInitializationScanner(ReifiedDeclaration parameter, SimpleClasses simpleClasses, SimpleMethods simpleMethods) {
        super(parameter, simpleClasses, simpleMethods);
    }

    @Override
    public Void visitNewClass(NewClassTree node, Void unused) {
        var rawTree = (JCTree.JCNewClass) node;
        var initializedClass = simpleClasses().resolveClass(enclosingClass(), enclosingMethod(), rawTree);
        if(!initializedClass.isEnclosedBy(parameter().enclosingClass().sym)){
            return super.visitNewClass(node, unused);
        }

        results().add(buildResultCall(rawTree, initializedClass));
        return super.visitNewClass(node, unused);
    }
}
