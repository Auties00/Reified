package it.auties.reified.scanner;

import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import it.auties.reified.simplified.SimpleTypes;

import java.util.ArrayList;
import java.util.List;

public class ExtendedClassesScanner extends TreeScanner<Void, Void> {
    private final JCTree.JCClassDecl superClass;
    private final SimpleTypes simpleTypes;
    private List<JCTree.JCClassDecl> results;

    public ExtendedClassesScanner(JCTree.JCClassDecl superClass, SimpleTypes simpleTypes){
        simpleTypes.resolveClass(superClass);
        this.superClass = superClass;
        this.simpleTypes = simpleTypes;
    }

    @Override
    public Void visitClass(ClassTree node, Void unused) {
        var rawNode = (JCTree.JCClassDecl) node;
        if(rawNode.equals(superClass)){
            return super.visitClass(node, unused);
        }

        simpleTypes.resolveClass(rawNode);
        var clause = rawNode.getExtendsClause();
        if(clause == null || !TreeInfo.symbol(clause).equals(superClass.sym)){
            return super.visitClass(node, unused);
        }

        results.add(rawNode);
        return super.visitClass(node, unused);
    }

    public List<JCTree.JCClassDecl> scan(JCTree tree) {
        this.results = new ArrayList<>();
        scan(tree, null);
        return results;
    }
}