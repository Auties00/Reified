package it.auties.reified.model;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import lombok.NonNull;

public record ReifiedDeclaration(@NonNull Symbol.TypeVariableSymbol typeParameter,
                                 @NonNull JCTree.JCClassDecl enclosingClass,
                                 @NonNull List<JCTree.JCMethodDecl> methods,
                                 boolean isClass) {
}
