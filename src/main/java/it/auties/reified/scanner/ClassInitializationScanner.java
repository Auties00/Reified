package it.auties.reified.scanner;

import com.sun.source.tree.NewClassTree;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleMethods;
import it.auties.reified.simplified.SimpleTrees;
import it.auties.reified.simplified.SimpleTypes;

public class ClassInitializationScanner extends ReifiedScanner<Type.ClassType> {
    public ClassInitializationScanner(ReifiedDeclaration parameter, SimpleMethods simpleMethods, SimpleTypes simpleTypes) {
        super(parameter, simpleMethods, simpleTypes);
    }

    @Override
    public Void visitNewClass(NewClassTree node, Void unused) {
        var rawTree = (JCTree.JCNewClass) node;
        var initializedClass = simpleTypes().resolveClassType(rawTree.clazz, enclosingClass());

        System.err.printf("Class candidate(%s): %s%n", node, initializedClass);
        var target = parameter().enclosingClass().sym.asType();
        if (initializedClass.filter(candidate -> candidate.tsym.getQualifiedName().equals(target.tsym.getQualifiedName())).isEmpty()) {
            return super.visitNewClass(node, unused);
        }

        results().add(buildResultCall(rawTree, (Type.ClassType) initializedClass.get()));
        return super.visitNewClass(node, unused);
    }
}
