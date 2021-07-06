package it.auties.reified.scanner;

import com.sun.source.tree.NewClassTree;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleMethods;
import it.auties.reified.simplified.SimpleTypes;

public class ClassInitializationScanner extends ReifiedScanner<Type.ClassType> {
    private final SimpleTypes simpleTypes;

    public ClassInitializationScanner(ReifiedDeclaration parameter, SimpleMethods simpleMethods, SimpleTypes simpleTypes) {
        super(parameter, simpleMethods);
        this.simpleTypes = simpleTypes;
    }

    @Override
    public Void visitNewClass(NewClassTree node, Void unused) {
        var rawTree = (JCTree.JCNewClass) node;
        var initializedClass = simpleTypes.resolveClassType(rawTree.clazz, enclosingClass());
        System.err.printf("Class candidate(%s): %s%n", node, initializedClass);

        var target = parameter().enclosingClassTree().sym.asType();
        if (initializedClass.filter(candidate -> candidate.tsym.getQualifiedName().equals(target.tsym.getQualifiedName())).isEmpty()) {
            return super.visitNewClass(node, unused);
        }

        System.err.printf("Class found: %s%n", rawTree);
        var type = (Type.ClassType) simpleTypes.resolveExpressionType(rawTree, enclosingClass()).orElseThrow();
        results().add(buildResultCall(rawTree, type));
        return super.visitNewClass(node, unused);
    }
}
