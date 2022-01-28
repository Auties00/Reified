package it.auties.reified.scanner;

import com.sun.source.tree.IdentifierTree;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.util.Context;
import it.auties.reified.simplified.SimpleTypes;

import static com.sun.tools.javac.code.Flags.UNATTRIBUTED;
import static com.sun.tools.javac.tree.TreeInfo.symbolFor;

public class CompleterScanner extends ReifiedScanner<Void> {
    private final Attr attr;
    private final Enter enter;
    private final MemberEnter memberEnter;
    public CompleterScanner(SimpleTypes simpleTypes, Context context) {
        super(null, null, simpleTypes);
        this.attr = Attr.instance(context);
        this.enter = Enter.instance(context);
        this.memberEnter = MemberEnter.instance(context);
    }


    @Override
    public Void visitIdentifier(IdentifierTree node, Void unused) {
        var identifier = (JCIdent) node;
        if(identifier.sym != null && simpleTypes().isReified(identifier.sym)){
            identifier.sym = enclosingMethod().sym.params.stream()
                    .filter(entry -> entry.getSimpleName().equals(identifier.getName()))
                    .findFirst()
                    .orElseThrow();
            identifier.type = identifier.sym.type;
            var env = enter.getEnv(enclosingClass().sym);
            if(enclosingMethod() != null){
                env = memberEnter.getMethodEnv(enclosingMethod(), env);
            }

            var known = symbolFor(env.tree).enclClass();
            known.flags_field = known.flags_field | UNATTRIBUTED;
            attr.attrib(env);
        }

        return super.visitIdentifier(node, unused);
    }
}
