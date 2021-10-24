package it.auties.reified.scanner;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import it.auties.reified.model.ReifiedArrayInitialization;
import it.auties.reified.model.ReifiedDeclaration;
import it.auties.reified.simplified.SimpleClasses;
import it.auties.reified.simplified.SimpleTypes;

public class ArrayInitializationScanner extends ReifiedScanner<ReifiedArrayInitialization> {
    public ArrayInitializationScanner(ReifiedDeclaration parameter, SimpleClasses simpleClasses, SimpleTypes simpleTypes) {
        super(parameter, simpleClasses, simpleTypes);
    }

    @Override
    public Void visitAssignment(AssignmentTree node, Void unused) {
        return super.visitAssignment(node, unused);
    }

    @Override
    public Void visitNewArray(NewArrayTree node, Void unused) {
        var rawTree = (JCTree.JCNewArray) node;
        var type = TreeInfo.symbol(rawTree.elemtype);
        if(type == null || !simpleTypes().reified(type)){
            return super.visitNewArray(node, unused);
        }

        results().add(buildArrayInit(rawTree, (Symbol.TypeVariableSymbol) type));
        return super.visitNewArray(node, unused);
    }
}
