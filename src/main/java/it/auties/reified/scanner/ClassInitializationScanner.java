package it.auties.reified.scanner;

import com.sun.source.tree.NewClassTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleClasses;
import it.auties.reified.simplified.SimpleTypes;

public class ClassInitializationScanner extends ReifiedScanner<ReifiedCall> {
    public ClassInitializationScanner(ReifiedDeclaration parameter, SimpleClasses simpleClasses, SimpleTypes simpleTypes) {
        super(parameter, simpleClasses, simpleTypes);
    }

    @Override
    public Void visitNewClass(NewClassTree node, Void unused) {
        var rawTree = (JCTree.JCNewClass) node;
        var initializedClass = rawTree.constructor;
        if (initializedClass == null) {
            return super.visitNewClass(node, unused);
        }

        var expected = initializedClass.enclClass();
        var actual = parameter().enclosingClass().sym;
        if (!simpleTypes().isAssignable(actual.asType(), expected.asType())) {
            return super.visitNewClass(node, unused);
        }

        results().add(buildCall(rawTree, (Symbol.MethodSymbol) initializedClass));
        return super.visitNewClass(node, unused);
    }
}
