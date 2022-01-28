package it.auties.reified.simplified;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.TypeVariableSymbol;
import com.sun.tools.javac.code.Type.ArrayType;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import it.auties.reified.annotation.Reified;
import it.auties.reified.model.ReifiedCall;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static com.sun.tools.javac.code.TypeTag.TYPEVAR;
import static com.sun.tools.javac.tree.JCTree.Tag.TYPEAPPLY;

public record SimpleTypes(ProcessingEnvironment environment,
                          Types types) {
    public Type createTypeWithParameters(Element parameter) {
        var types = environment.getTypeUtils();
        var wildcard = types.getWildcardType(null, parameter.asType());
        var type = environment.getElementUtils()
                .getTypeElement(Class.class.getName());
        return (Type) types.getDeclaredType(type, wildcard);
    }

    public boolean isValid(Type type) {
        return !type.isErroneous();
    }

    public Type commonType(List<Type> input) {
        var type = types.lub(input);
        if (type.getTag() == TypeTag.BOT || !isValid(type)) {
            return null;
        }

        return types.erasure(type);
    }

    public Type erase(Symbol typeVariableSymbol) {
        return erase(typeVariableSymbol.asType());
    }

    public Type erase(Type type){
        if(type instanceof ArrayType arrayType){
            type = arrayType.elemtype;
        }

        var boxed = types.boxedTypeOrType(type);
        var erased = types.erasure(type);
        return erased != null && isValid(erased) ? boxed
                : erased;
    }

    public List<Type> eraseTypeVariableFromTypeParameters(TypeVariableSymbol typeParameter, List<TypeVariableSymbol> parameters, List<JCTree.JCExpression> arguments) {
        return IntStream.range(0, parameters.size())
                .limit(arguments.size())
                .filter(index -> Objects.equals(parameters.get(index), typeParameter))
                .mapToObj(arguments::get)
                .map(arg -> erase(arg.type))
                .collect(List.collector());
    }


    // <T> void <name>(Map<Map<String, T>, Map<String, T>> <name>)
    // <name>(new HashMap<Map<String, String>, Map<String, Integer>>())
    private Type matchArgumentsAndParameters(TypeVariableSymbol typeParameter, Iterator<TypeStructure> parametersStructures, Iterator<TypeStructure> argumentsStructures) {
        var matches = new ListBuffer<Type>();
        while (parametersStructures.hasNext() && argumentsStructures.hasNext()){
            var parameter = parametersStructures.next();
            var argument = argumentsStructures.next();
            if(Objects.equals(typeParameter, parameter.type().asElement())){
                matches.add(argument.type());
                continue;
            }

            var inner = matchArgumentsAndParameters(typeParameter, parameter.types().iterator(), argument.types().iterator());
            matches.add(inner);
        }

        return commonType(matches.toList());
    }

    private TypeStructure createTypeStructure(Type type, int index){
        var baseStructure = new TypeStructure(index, type, new ListBuffer<>());
        if(!type.isParameterized()){
            return baseStructure;
        }


        for(var parameterIndex = 0; parameterIndex < type.getTypeArguments().size(); parameterIndex++){
            var parameterType = type.getTypeArguments().get(parameterIndex);
            var innerTypes = createTypeStructure(parameterType, parameterIndex);
            var innerStructure = new TypeStructure(parameterIndex, parameterType, ListBuffer.of(innerTypes));
            baseStructure.types().add(innerStructure);
        }

        return baseStructure;
    }

    private record TypeStructure(int index, Type type, ListBuffer<TypeStructure> types){

    }

    public List<JCTree.JCExpression> flattenGenericType(JCTree.JCExpression type) {
        if (type.getTag() != TYPEAPPLY) {
            return List.of(type);
        }

        var apply = (JCTree.JCTypeApply) type;
        return apply.arguments.stream()
                .map(this::flattenGenericType)
                .flatMap(Collection::stream)
                .collect(List.collector());
    }

    public Iterator<Type> flattenGenericType(Type type) {
        if (!type.isParameterized()) {
            return List.of(type)
                    .iterator();
        }

        return flattenGenericType(type.getTypeArguments());
    }

    public Iterator<Type> flattenGenericType(List<? extends Type> types) {
        return types.stream()
                .map(this::flattenGenericType)
                .<Iterable<Type>>map(iterator -> () -> iterator)
                .flatMap(iterator -> StreamSupport.stream(iterator.spliterator(), false))
                .iterator();
    }

    public Optional<Type> resolveImplicitType(Iterator<Type> invocationIterator, Iterator<Type> genericIterator, TypeVariableSymbol typeVariable) {
        while (invocationIterator.hasNext() && genericIterator.hasNext()) {
            var invocation = invocationIterator.next();
            var generic = genericIterator.next();
            if (!generic.tsym.equals(typeVariable)) {
                continue;
            }

            return Optional.of(invocation);
        }

        return Optional.empty();
    }

    public boolean isAssignable(Type assignable, Type assigned) {
        return types.isSubtype(assignable, assigned);
    }

    public boolean isReified(Symbol typeSymbol) {
        return typeSymbol.getAnnotation(Reified.class) != null;
    }

    public boolean isRecord(JCTree.JCModifiers mods) {
        return isRecord(mods.flags);
    }

    public boolean isRecord(long flags) {
        return (flags & Flags.RECORD) != 0;
    }

    public boolean isCompactConstructor(JCTree.JCMethodDecl constructor) {
        return (constructor.mods.flags & Flags.COMPACT_RECORD_CONSTRUCTOR) != 0;
    }

    public Type infer(ReifiedCall call) {
        if(call.invocation() instanceof JCTree.JCMethodInvocation methodInvocation) {
            var invocationTypeArguments = methodInvocation.getTypeArguments();
            if (invocationTypeArguments != null && !invocationTypeArguments.isEmpty()) {
                var deduced = eraseTypeVariableFromTypeParameters(call.typeVariable(),
                        call.invoked().getTypeParameters(), invocationTypeArguments);
                return commonType(deduced);
            }

            var methodParameterType = inferWithParameters(methodInvocation, call);
            var methodReturnTypeFlat = flattenGenericType(call.invoked().getReturnType());
            return inferWithReturnType(call, methodReturnTypeFlat)
                    .filter(type -> type.getTag() != TYPEVAR)
                    .orElseGet(() -> eraseWildCard(call.typeVariable(), methodParameterType));
        }

        if(call.invocation() instanceof JCTree.JCNewClass classInitialization) {
            var invokedTypeArgs = call.invoked().enclClass().getTypeParameters();
            var invocationTypeArgs = flattenGenericType(classInitialization.getIdentifier());
            if (!invocationTypeArgs.isEmpty()) {
                var deduced = eraseTypeVariableFromTypeParameters(call.typeVariable(),
                        invokedTypeArgs, invocationTypeArgs);
                return commonType(deduced);
            }

            var classParameterType = inferWithParameters(classInitialization, call);
            var initializedClassFlatType = flattenGenericType(call.invoked().enclClass().asType().getTypeArguments());
            return inferWithReturnType(call, initializedClassFlatType)
                    .filter(type -> type.getTag() != TYPEVAR)
                    .orElseGet(() -> eraseWildCard(call.typeVariable(), classParameterType));
        }

        throw new IllegalArgumentException("Cannot resolve type: expected APPLY or NEW_CLASS, got " + call.invocation().getTag());
    }

    private Type eraseWildCard(TypeVariableSymbol typeVariable, Type parameterType) {
        return Objects.requireNonNullElse(erase(parameterType), erase(typeVariable));
    }

    private Type inferWithParameters(JCTree.JCPolyExpression invocation, ReifiedCall call) {
        var invocationArgs = getArguments(invocation);
        var parameters = call.invoked().getParameters();
        var parametersStructures = IntStream.range(0, parameters.size())
                .mapToObj(index -> createTypeStructure(parameters.get(index).asType(), index))
                .iterator();
        var argumentsStructures = IntStream.range(0, invocationArgs.size())
                .mapToObj(index -> createTypeStructure(invocationArgs.get(index), index))
                .iterator();
        return matchArgumentsAndParameters(call.typeVariable(), parametersStructures, argumentsStructures);
    }

    private List<Type> getArguments(JCTree.JCPolyExpression invocation) {
        if(invocation instanceof JCTree.JCMethodInvocation methodInvocation) {
            return methodInvocation.getArguments()
                    .stream()
                    .map(arg -> arg.type)
                    .collect(List.collector());
        }

        if(invocation instanceof JCTree.JCNewClass classInitialization) {
            return classInitialization.getArguments()
                    .stream()
                    .map(arg -> arg.type)
                    .collect(List.collector());
        }

        throw new IllegalArgumentException("Cannot find arguments of poly expression: expected APPLY or NEW_CLASS, got " + invocation.getTag());
    }

    private Optional<Type> inferWithReturnType(ReifiedCall call, Iterator<Type> flatReturnType) {
        if (call.enclosingStatement() == null) {
            return Optional.empty();
        }

        return switch (call.enclosingStatement().getTag()) {
            case RETURN -> {
                var methodReturnType = call.enclosingMethod().getReturnType();
                var flatMethodReturn = flattenGenericType(methodReturnType.type);
                yield resolveImplicitType(flatMethodReturn, flatReturnType, call.typeVariable());
            }

            case VARDEF -> {
                var variable = (JCTree.JCVariableDecl) call.enclosingStatement();
                var flatVariableType = flattenGenericType(variable.type);
                yield resolveImplicitType(flatVariableType, flatReturnType, call.typeVariable());
            }

            default -> Optional.empty();
        };
    }

    public Optional<MethodType> asMethodType(Type type){
        try {
            return Optional.ofNullable(type.asMethodType());
        }catch (AssertionError error){
            return Optional.empty();
        }
    }
}
