package it.auties.reified.scanner;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Name;
import it.auties.reified.annotation.Reified;
import it.auties.reified.model.ReifiedCandidate;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
public class AnnotationScanner extends TreeScanner<Void, Void> {
    private Set<ReifiedCandidate> parameters;
    private JCTree.JCClassDecl enclosingClass;
    private JCTree.JCMethodDecl enclosingMethod;

    @Override
    public Void visitClass(ClassTree node, Void unused) {
        this.enclosingClass = (JCTree.JCClassDecl) node;
        this.enclosingMethod = null;
        return super.visitClass(node, unused);
    }

    @Override
    public Void visitMethod(MethodTree node, Void unused) {
        this.enclosingMethod = (JCTree.JCMethodDecl) node;
        return super.visitMethod(node, unused);
    }

    @Override
    public Void visitTypeParameter(TypeParameterTree node, Void unused) {
        var annotations = node.getAnnotations();
        if(!isAnnotated(annotations)){
            return super.visitTypeParameter(node, unused);
        }

        var param = (JCTree.JCTypeParameter) node;
        var symbol = (Symbol.TypeVariableSymbol) param.type.asElement();
        parameters.add(new ReifiedCandidate(symbol, enclosingClass, enclosingMethod));
        return super.visitTypeParameter(node, unused);
    }

    private boolean isAnnotated(java.util.List<?> annotations) {
        return annotations.stream()
                .map(entry -> (JCTree.JCAnnotation) entry)
                .map(JCTree.JCAnnotation::getAnnotationType)
                .map(TreeInfo::name)
                .map(Name::toString)
                .anyMatch(Reified.class.getSimpleName()::equals);
    }

    public Set<ReifiedCandidate> scan(JCTree tree) {
        this.parameters = new HashSet<>();
        scan(tree, null);
        return parameters;
    }
}
