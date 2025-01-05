package it.auties.reified.scanner;

import com.sun.source.tree.NewClassTree;
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
        var initializedClass = simpleClasses.findAndResolveConstructor(enclosingClass, enclosingMethod, rawTree);
        if (initializedClass.isEmpty()) {
            return super.visitNewClass(node, unused);
        }

        var expected = initializedClass.get().enclClass();
        var actual = parameter.enclosingClass().sym;
        if (!simpleTypes.assignable(actual.asType(), expected.asType())) {
            return super.visitNewClass(node, unused);
        }

        results.add(buildCall(rawTree, initializedClass.get()));
        return super.visitNewClass(node, unused);
    }
}
