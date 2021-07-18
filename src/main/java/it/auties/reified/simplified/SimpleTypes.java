package it.auties.reified.simplified;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import it.auties.reified.annotation.Reified;
import it.auties.reified.util.CollectionUtils;
import it.auties.reified.util.StreamUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.ExtensionMethod;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

@AllArgsConstructor
@Data
@ExtensionMethod({CollectionUtils.class, StreamUtils.class})
public class SimpleTypes {
    private final ProcessingEnvironment environment;
    private final Types types;
    private final Attr attr;
    private final Enter enter;
    private final MemberEnter memberEnter;

    public Type createTypeWithParameter(Class<?> clazz, Element parameter) {
        return createTypeWithParameter(clazz, parameter.asType());
    }

    public Type createTypeWithParameter(Class<?> clazz, TypeMirror parameter) {
        var types = environment.getTypeUtils();
        return (Type) types.getDeclaredType(toTypeElement(clazz), parameter);
    }

    public TypeElement toTypeElement(Class<?> clazz) {
        var type = environment.getElementUtils().getTypeElement(clazz.getName());
        return Assert.checkNonNull(type, "Reified Methods: Cannot compile as the type element associated with the class " + clazz.getName() + " doesn't exist!");
    }

    public void resolveEnv(Env<AttrContext> attrContextEnv){
        attr.attrib(attrContextEnv);
    }

    public void resolveClass(JCTree.JCClassDecl clazz){
        attr.attribClass(clazz.pos(), clazz.sym);
    }

    public List<Type> resolveTypes(List<? extends JCTree> expressions, JCTree.JCClassDecl clazz){
        var env = findClassEnv(clazz);
        return expressions.stream()
                .map(type -> attr.attribExpr(type, env))
                .collect(List.collector());
    }

    public boolean isValid(Type type) {
        return !type.isErroneous();
    }

    public Optional<Env<AttrContext>> findClassEnv(Tree tree){
        if(!(tree instanceof JCTree.JCClassDecl)){
            return Optional.empty();
        }

        return Optional.of(findClassEnv((JCTree.JCClassDecl) tree));
    }

    public Env<AttrContext> findClassEnv(JCTree.JCClassDecl enclosingClass) {
        return enter.getClassEnv(enclosingClass.sym.asType().asElement());
    }

    public Env<AttrContext> findClassEnv(Type.ClassType type) {
        return enter.getClassEnv(type.asElement());
    }

    public Type commonType(List<Type> input) {
        var type = types.lub(input);
        if (type.getTag() == TypeTag.BOT || !isValid(type)) {
            return null;
        }

        return type;
    }

    public Type erase(Symbol.TypeVariableSymbol typeVariableSymbol) {
        return typeVariableSymbol.erasure(types);
    }

    public Type boxed(Type type){
        return types.boxedTypeOrType(type);
    }

    public boolean isGeneric(Type type){
        return type instanceof Type.TypeVar;
    }

    public boolean isNotWildCard(Type type){
        return !(type instanceof Type.WildcardType);
    }

    public Type resolveWildCard(Type type){
        if (isNotWildCard(type)) {
            return type;
        }

        return ((Type.WildcardType) type).type;
    }

    public List<Type> matchTypeParamToTypedArg(Symbol.TypeVariableSymbol typeParameter, List<Symbol.TypeVariableSymbol> invoked, List<JCTree.JCExpression> arguments, JCTree.JCClassDecl enclosingClass) {
        var env = findClassEnv(enclosingClass);
        return invoked.stream()
                .filter(typeParameter::equals)
                .map(invoked::indexOf)
                .map(index -> arguments.getSafe(index))
                .onlyPresent()
                .map(arg -> resolveClassType(arg, env))
                .onlyPresent()
                .map(this::boxed)
                .collect(List.collector());
    }

    public List<Type> matchTypeVariableSymbolToArgs(Symbol.TypeVariableSymbol typeParameter, List<Symbol.VarSymbol> parameters, List<Type> arguments){
        return IntStream.range(0, parameters.size())
                .filter(index -> Objects.equals(typeParameter, parameters.get(index).asType().asElement()))
                .mapToObj(arguments::get)
                .map(this::boxed)
                .collect(List.collector());
    }

    public Optional<Type> resolveClassType(JCTree argument, Env<AttrContext> env) {
        return Optional.ofNullable(attr.attribType(argument, env)).filter(this::isValid);
    }

    public List<JCTree.JCExpression> flattenGenericType(JCTree.JCExpression type){
        if(!(type instanceof JCTree.JCTypeApply)){
            return List.of(type);
        }

        var apply = (JCTree.JCTypeApply) type;
        return apply.arguments.stream()
                .map(this::flattenGenericType)
                .flatMap(Collection::stream)
                .collect(List.collector());
    }

    public List<Type> flattenGenericType(Type type){
        if(!type.isParameterized()){
            return List.of(type);
        }

        return flattenGenericType(type.getTypeArguments());
    }

    public List<Type> flattenGenericType(List<? extends Type> types){
        return types.stream()
                .map(this::flattenGenericType)
                .flatMap(Collection::stream)
                .collect(List.collector());
    }

    public Optional<Type> resolveImplicitType(Iterator<Type> invocationIterator, Iterator<Type> genericIterator, Symbol.TypeVariableSymbol typeVariable){
        while (invocationIterator.hasNext() && genericIterator.hasNext()){
            var invocation = invocationIterator.next();
            var generic = genericIterator.next();
            if (!generic.tsym.equals(typeVariable)) {
                continue;
            }

            return Optional.of(invocation);
        }

        return Optional.empty();
    }

    public boolean isAssignable(Type assignable, Type assigned){
        return types.isSubtype(assignable, assigned);
    }

    public boolean isReified(Symbol typeSymbol) {
       return typeSymbol.getAnnotation(Reified.class) != null;
    }
}
