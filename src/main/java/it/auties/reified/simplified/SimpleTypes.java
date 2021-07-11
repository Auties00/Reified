package it.auties.reified.simplified;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
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

import static com.sun.tools.javac.code.TypeTag.BOT;
import static com.sun.tools.javac.code.TypeTag.VOID;

@AllArgsConstructor
@Data
@ExtensionMethod({CollectionUtils.class, StreamUtils.class})
public class SimpleTypes {
    private final ProcessingEnvironment environment;
    private final Types types;
    private final Attr attr;
    private final Enter enter;

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

    public Optional<Type> resolveClassType(JCTree argument, JCTree.JCClassDecl enclosingClass) {
        var env = findClassEnv(enclosingClass);
        return Optional.ofNullable(attr.attribType(argument, env)).filter(this::isValid);
    }

    public boolean isValid(Type type) {
        return !type.isErroneous();
    }

    public Env<AttrContext> findClassEnv(JCTree.JCClassDecl enclosingClass) {
        return enter.getClassEnv(enclosingClass.sym.asType().asElement());
    }

    public Optional<Type> commonType(List<Type> input) {
        return Optional.ofNullable(types.lub(input))
                .filter(type -> type.getTag() != TypeTag.BOT)
                .filter(this::isValid);
    }

    public Type erase(Symbol.TypeVariableSymbol typeVariableSymbol) {
        return typeVariableSymbol.erasure(types);
    }

    public Type erase(Type type) {
        return types.erasure(type);
    }

    public Type boxed(Type type){
        return types.boxedTypeOrType(type);
    }

    public boolean isGeneric(Type type){
        return type instanceof Type.TypeVar;
    }

    public boolean isWildCard(Type type){
        return type instanceof Type.WildcardType;
    }

    public Type resolveWildCard(Type type){
        if (!isWildCard(type)) {
            return type;
        }

        return ((Type.WildcardType) type).type;
    }

    public List<Type> matchTypeParamToTypedArg(Symbol.TypeVariableSymbol typeParameter, List<Symbol.TypeVariableSymbol> invoked, List<JCTree.JCExpression> arguments, JCTree.JCClassDecl enclosingClass) {
        return invoked.stream()
                .filter(typeParameter::equals)
                .map(invoked::indexOf)
                .map(index -> arguments.getSafe(index))
                .onlyPresent()
                .map(candidate -> resolveClassType(candidate, enclosingClass))
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

    public List<Type> flattenGenericType(List<Type> types){
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

    public Type resolveImplicitType(JCTree.JCVariableDecl variable, Env<AttrContext> env) {
        var initType = attr.attribExpr(variable.init, env);
        if (initType.hasTag(BOT) || initType.hasTag(VOID)) {
            throw new IllegalArgumentException("Cannot deduce type of void or null");
        }

        return types.upward(initType, types.captures(initType));
    }

    public boolean isReified(Symbol typeSymbol) {
       return typeSymbol.getAnnotation(Reified.class) != null;
    }

    public List<Type> resolveTypes(List<? extends JCTree> expressions, JCTree.JCClassDecl clazz){
        var env = findClassEnv(clazz);
        return expressions.stream()
                .map(type -> attr.attribExpr(type, env))
                .collect(List.collector());
    }
}
