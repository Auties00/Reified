package it.auties.reified.scanner;

import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import it.auties.reified.model.ReifiedParameter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@Data
@Accessors(fluent = true)
public abstract class ReifiedScanner<R> extends TreeScanner<Void, Void> {
    private final ReifiedParameter parameter;
    private final Log logger;
    private final Set<R> results = new HashSet<>();
    public Set<R> scan(){
        scan(parameter.enclosingClassTree(), null);
        return results;
    }
}
