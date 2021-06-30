package it.auties.reified.scanner;

import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import it.auties.reified.model.ReifiedCall;
import it.auties.reified.model.ReifiedParameter;
import it.auties.reified.simplified.SimpleMethods;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@RequiredArgsConstructor
@Data
@Accessors(fluent = true)
public abstract class ReifiedScanner extends TreeScanner<Void, Void> {
    private final ReifiedParameter parameter;
    private final SimpleMethods simpleMethods;
    private final Log logger;
    private Set<ReifiedCall> results;
    private JCTree.JCClassDecl enclosingClass;
    private JCTree.JCMethodDecl enclosingMethod;
    private JCTree.JCStatement callerStatement;

    public Set<ReifiedCall> scan(){
        this.results = new HashSet<>();
        scan(parameter.enclosingClassTree(), null);
        return results;
    }
}
