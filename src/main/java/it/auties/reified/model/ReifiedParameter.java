package it.auties.reified.model;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

import javax.lang.model.element.Element;

@Value
@Accessors(fluent = true)
@Builder
@ToString
public class ReifiedParameter {
    Element enclosingClassElement;
    JCTree.JCClassDecl enclosingClassTree;
    List<JCTree.JCMethodDecl> methods;
    boolean isClass;
    AccessModifier modifier;

    public enum AccessModifier {
        PUBLIC,
        PRIVATE,
        PROTECTED,
        PACKAGE_PRIVATE
    }
}
