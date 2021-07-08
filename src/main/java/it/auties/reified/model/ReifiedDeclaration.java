package it.auties.reified.model;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
@Builder
@ToString
public class ReifiedDeclaration {
    @NonNull Symbol.TypeVariableSymbol typeParameter;
    @NonNull JCTree.JCClassDecl enclosingClass;
    @NonNull List<JCTree.JCMethodDecl> methods;
    @NonNull AccessModifier modifier;
    boolean isClass;

    public enum AccessModifier {
        PUBLIC,
        PRIVATE,
        PROTECTED,
        PACKAGE_PRIVATE
    }
}
