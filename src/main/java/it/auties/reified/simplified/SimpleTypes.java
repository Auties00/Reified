package it.auties.reified.simplified;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import it.auties.reified.annotation.Reified;
import it.auties.reified.model.ReifiedCall;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.sun.tools.javac.code.TypeTag.TYPEVAR;
import static com.sun.tools.javac.code.TypeTag.WILDCARD;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

public class SimpleTypes {
    private final ProcessingEnvironment environment;
    private final Types types;
    private final Attr attr;
    private final Enter enter;
    private final MemberEnter memberEnter;

    public SimpleTypes(ProcessingEnvironment environment, Types types, Attr attr, Enter enter, MemberEnter memberEnter) {
        this.environment = environment;
        this.types = types;
        this.attr = attr;
        this.enter = enter;
        this.memberEnter = memberEnter;
    }

    public Type createTypeWithParameters(Class<?> clazz, Element parameter) {
        return createTypeWithParameters(clazz, parameter.asType());
    }

    public Type createTypeWithParameters(Class<?> clazz, TypeMirror... parameter) {
        var types = environment.getTypeUtils();
        return (Type) types.getDeclaredType(toTypeElement(clazz), parameter);
    }

    public TypeElement toTypeElement(Class<?> clazz) {
        var type = environment.getElementUtils().getTypeElement(clazz.getName());
        return Assert.checkNonNull(type, "Reified Methods: Cannot compile as the type element associated with the class " + clazz.getName() + " doesn't exist!");
    }

    public void resolveEnv(Env<AttrContext> attrContextEnv) {
        attr.attrib(attrContextEnv);
    }

    public void resolveClass(JCTree.JCClassDecl clazz) {
        attr.attribClass(clazz.pos(), clazz.sym);
    }

    public List<Type> resolveTypes(List<? extends JCTree> expressions, JCTree.JCClassDecl clazz) {
        var env = findClassEnv(clazz);
        return expressions.stream()
                .map(type -> attr.attribExpr(type, env))
                .collect(List.collector());
    }

    public boolean valid(Type type) {
        return !type.isErroneous();
    }

    public Optional<Env<AttrContext>> findClassEnv(Tree tree) {
        if (!(tree instanceof JCTree.JCClassDecl)) {
            return Optional.empty();
        }

        return Optional.of(findClassEnv((JCTree.JCClassDecl) tree));
    }

    public Env<AttrContext> findClassEnv(JCTree.JCClassDecl enclosingClass) {
        return enter.getClassEnv(enclosingClass.sym.asType().asElement());
    }

    public Env<AttrContext> findMethodEnv(JCTree.JCMethodDecl method, Env<AttrContext> env) {
        if (method == null) {
            return env;
        }

        return memberEnter.getMethodEnv(method, env);
    }

    public Type commonType(List<Type> input) {
        var type = types.lub(input);
        if (type.getTag() == TypeTag.BOT || !valid(type)) {
            return null;
        }

        return type;
    }

    public Type erase(Symbol typeVariableSymbol) {
        return typeVariableSymbol.erasure(types);
    }

    public Type boxed(Type type) {
        return types.boxedTypeOrType(type);
    }

    public boolean generic(Type type) {
        return type.getTag() == TYPEVAR;
    }

    public boolean notWildCard(Type type) {
        return type.getTag() != WILDCARD;
    }

    public Type resolveWildCard(Type type) {
        if(type == null){
            return null;
        }

        if (notWildCard(type)) {
            return type;
        }

        return ((Type.WildcardType) type).type;
    }

    public List<Type> eraseTypeVariableFromTypeParameters(Symbol.TypeVariableSymbol typeParameter, List<Symbol.TypeVariableSymbol> parameters, List<JCTree.JCExpression> arguments, JCTree.JCClassDecl enclosingClass) {
        var env = findClassEnv(enclosingClass);
        return IntStream.range(0, parameters.size())
                .limit(arguments.size())
                .filter(index -> Objects.equals(parameters.get(index), typeParameter))
                .mapToObj(arguments::get)
                .map(arg -> inferReifiedType(arg, env))
                .flatMap(Optional::stream)
                .map(this::parseArgument)
                .collect(List.collector());
    }

    public List<Type> eraseTypeVariableFromArguments(Symbol.TypeVariableSymbol typeParameter, List<Symbol.VarSymbol> parameters, List<Type> arguments, boolean varArgs) {
        return IntStream.range(0, arguments.size())
                .filter(index -> matchTypeVariableToParameter(typeParameter, parameters, index, varArgs))
                .mapToObj(arguments::get)
                .map(this::parseArgument)
                .collect(List.collector());
    }

    private boolean matchTypeVariableToParameter(Symbol.TypeVariableSymbol typeParameter, List<Symbol.VarSymbol> parameters, int index, boolean varArgs) {
        var param = getVarArgsParam(parameters, index, varArgs);
        if (types.isArray(param)) {
            var arrayType = (Type.ArrayType) param;
            return typeParameter.equals(arrayType.getComponentType().asElement());
        }

        return typeParameter.equals(param.asElement());
    }

    private Type getVarArgsParam(List<Symbol.VarSymbol> parameters, int index, boolean varArgs) {
        if (!varArgs || index < parameters.size() - 1) {
            return parameters.get(index).asType();
        }

        return parameters.last().asType();
    }

    private Type parseArgument(Type type) {
        if (types.isArray(type)) {
            var arrayType = (Type.ArrayType) type;
            return boxed(arrayType.getComponentType());
        }

        return boxed(type);
    }

    public Optional<Type> inferReifiedType(JCTree argument, Env<AttrContext> env) {
        return Optional.ofNullable(attr.attribType(argument, env)).filter(this::valid);
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

    public List<Type> flattenGenericType(Type type) {
        if (!type.isParameterized()) {
            return List.of(type);
        }

        return flattenGenericType(type.getTypeArguments());
    }

    public List<Type> flattenGenericType(List<? extends Type> types) {
        return types.stream()
                .map(this::flattenGenericType)
                .flatMap(Collection::stream)
                .collect(List.collector());
    }

    public Optional<Type> resolveImplicitType(Iterator<Type> invocationIterator, Iterator<Type> genericIterator, Symbol.TypeVariableSymbol typeVariable) {
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

    public boolean assignable(Type assignable, Type assigned) {
        return types.isSubtype(assignable, assigned);
    }

    public boolean reified(Symbol typeSymbol) {
        return typeSymbol.getAnnotation(Reified.class) != null;
    }

    public boolean record(JCTree.JCModifiers mods) {
        return record(mods.flags);
    }

    public boolean record(long flags) {
        return (flags & Flags.RECORD) != 0;
    }

    public boolean compactConstructor(JCTree.JCMethodDecl constructor) {
        return (constructor.mods.flags & Flags.COMPACT_RECORD_CONSTRUCTOR) != 0;
    }

    public Type inferReifiedType(ReifiedCall call) {
        switch (call.invocation().getTag()){
            case APPLY:
                var methodInvocation = (JCTree.JCMethodInvocation) call.invocation();
                var invocationTypeArguments = methodInvocation.getTypeArguments();
                if (invocationTypeArguments != null && !invocationTypeArguments.isEmpty()) {
                    var deduced = eraseTypeVariableFromTypeParameters(call.typeVariable(), call.invoked().getTypeParameters(), invocationTypeArguments, call.enclosingClass());
                    Assert.check(!deduced.isEmpty(), "Cannot resolve method type for explicit type variable");
                    return resolveWildCard(deduced.head);
                }

                var methodParameterType = inferReifiedType(methodInvocation, call);
                var invokedReturnIterator = flattenGenericType(call.invoked().getReturnType()).iterator();
                return inferReifiedType(call, invokedReturnIterator)
                        .map(type -> inferReifiedType(call.typeVariable(), methodParameterType, type))
                        .orElse(inferReifiedType(call.typeVariable(), methodParameterType));
            case NEWCLASS:
                var classInitialization = (JCTree.JCNewClass) call.invocation();
                var invokedTypeArgs = call.invoked().enclClass().getTypeParameters();
                var invocationTypeArgs = flattenGenericType(classInitialization.getIdentifier());
                if (!invocationTypeArgs.isEmpty()) {
                    var deduced = eraseTypeVariableFromTypeParameters(call.typeVariable(), invokedTypeArgs, invocationTypeArgs, call.enclosingClass());
                    Assert.check(!deduced.isEmpty(), "Cannot resolve class type for explicit type variable");
                    return resolveWildCard(deduced.head);
                }

                var classParameterType = inferReifiedType(classInitialization, call);
                var initializedClassTypeIterator = flattenGenericType(call.invoked().enclClass().asType().getTypeArguments()).iterator();
                return inferReifiedType(call, initializedClassTypeIterator)
                        .map(type -> inferReifiedType(call.typeVariable(), classParameterType, type))
                        .orElse(inferReifiedType(call.typeVariable(), classParameterType));
            default:
                throw new IllegalArgumentException("Cannot resolve type: expected APPLY or NEWCLASS, got " + call.invocation().getTag());
        }
    }

    private Type inferReifiedType(Symbol.TypeVariableSymbol typeVariable, Type parameterType, Type type) {
        if (notWildCard(type)) {
            return type;
        }

        return inferReifiedType(typeVariable, parameterType);
    }

    private Type inferReifiedType(Symbol.TypeVariableSymbol typeVariable, Type parameterType) {
        return Objects.requireNonNullElse(resolveWildCard(parameterType), erase(typeVariable));
    }

    private Type inferReifiedType(JCTree.JCPolyExpression invocation, ReifiedCall call) {
        var invocationArgs = resolveTypes(findPolyExpressionArguments(invocation), call.enclosingClass());
        var commonTypes = eraseTypeVariableFromArguments(call.typeVariable(), call.invoked().getParameters(), invocationArgs, call.invoked().isVarArgs());
        return commonType(commonTypes);
    }

    private List<JCTree.JCExpression> findPolyExpressionArguments(JCTree.JCPolyExpression invocation) {
        if (invocation.getTag() == APPLY) {
            return ((JCTree.JCMethodInvocation) invocation).getArguments();
        }

        if (invocation.getTag() == NEWCLASS) {
            return ((JCTree.JCNewClass) invocation).getArguments();
        }

        throw new IllegalArgumentException("Cannot find arguments of poly expression: expected APPLY or NEW_CLASS, got " + invocation.getTag());
    }

    private Type inferReifiedType(JCTree.JCNewClass invocation, ReifiedCall call) {
        var invocationArgs = resolveTypes(invocation.getArguments(), call.enclosingClass());
        var commonTypes = eraseTypeVariableFromArguments(call.typeVariable(), call.invoked().getParameters(), invocationArgs, call.invoked().isVarArgs());
        return commonType(commonTypes);
    }

    private Optional<Type> inferReifiedType(ReifiedCall call, Iterator<Type> flatReturnType) {
        if(call.enclosingStatement() == null){
            return Optional.empty();
        }

        var env = findClassEnv(call.enclosingClass());
        switch (call.enclosingStatement().getTag()) {
            case RETURN:
                var methodReturnType = inferReifiedType(call.enclosingMethod().getReturnType(), env);
                if (methodReturnType.isEmpty()) {
                    return Optional.empty();
                }

                var flatMethodReturn = flattenGenericType(methodReturnType.get()).iterator();
                return resolveImplicitType(flatMethodReturn, flatReturnType, call.typeVariable());
            case VARDEF:
                var variable = (JCTree.JCVariableDecl) call.enclosingStatement();
                if (variable.isImplicitlyTyped()) {
                    return Optional.empty();
                }

                var variableType = inferReifiedType(variable.vartype, env);
                if (variableType.isEmpty()) {
                    return Optional.empty();
                }

                var flatVariableType = flattenGenericType(variableType.get()).iterator();
                return resolveImplicitType(flatVariableType, flatReturnType, call.typeVariable());
            default:
                return Optional.empty();
        }
    }

    public Type.ArrayType createArray(Type type){
        return types.makeArrayType(type);
    }

    public boolean hasUncheckedAnnotation(JCTree.JCModifiers modifiers){
        return modifiers.getAnnotations()
                .stream()
                .anyMatch(this::uncheckedAnnotation);
    }

    private boolean uncheckedAnnotation(JCTree.JCAnnotation annotation) {
        var type = TreeInfo.symbol(annotation.getAnnotationType()).asType();
        if(!types.isSameType(type, createTypeWithParameters(SuppressWarnings.class))){
            return false;
        }

        var annotationValueAssignment = annotation.args.head;
        if(annotationValueAssignment.getTag() != ASSIGN){
            System.err.printf("SimpleTypes#isUncheckedAnnotation: unexpectedly got tag %s(report this on github)", annotationValueAssignment.getTag().name());
            return false;
        }

        var annotationValue = ((JCTree.JCAssign) annotationValueAssignment).getExpression();
        if (annotationValue.getTag() == LITERAL) {
            return ((JCTree.JCLiteral) annotationValue).getValue().equals("unchecked");
        }

        var annotationValueSymbol = TreeInfo.symbol(annotationValue);
        if(!(annotationValueSymbol instanceof Symbol.VarSymbol)){
            System.err.printf("SimpleTypes#isUncheckedAnnotation: unexpectedly got symbol %s(report this on github)", annotationValueSymbol.getClass().getName());
            return false;
        }

        return ((Symbol.VarSymbol) annotationValueSymbol).getConstantValue().equals("unchecked");
    }
}
