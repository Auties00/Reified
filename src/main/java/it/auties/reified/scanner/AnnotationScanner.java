package it.auties.reified.scanner;

import com.sun.source.tree.TypeParameterTree;
import com.sun.tools.javac.code.Symbol.TypeVariableSymbol;
import com.sun.tools.javac.tree.JCTree;
import it.auties.reified.model.ReifiedCandidate;
import it.auties.reified.simplified.SimpleClasses;
import it.auties.reified.simplified.SimpleTypes;

import static com.sun.tools.javac.tree.TreeInfo.symbolFor;

public class AnnotationScanner extends ReifiedScanner<ReifiedCandidate> {
    public AnnotationScanner(SimpleClasses simpleClasses, SimpleTypes simpleTypes) {
        super(null, simpleClasses, simpleTypes);
    }

    @Override
    public Void visitTypeParameter(TypeParameterTree node, Void unused) {
        var symbol = symbolFor((JCTree) node);
        if(symbol != null && simpleTypes().isReified(symbol)){
            var typeSymbol = (TypeVariableSymbol) symbol;
            var candidate = new ReifiedCandidate(typeSymbol, enclosingClass(), enclosingMethod());
            results().add(candidate);
        }

        return super.visitTypeParameter(node, unused);
    }
}
