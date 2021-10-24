package it.auties.reified.simplified;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import it.auties.reified.model.ReifiedArrayInitialization;
import it.auties.reified.model.ReifiedDeclaration;
import lombok.AllArgsConstructor;

import javax.lang.model.element.Element;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.NoSuchElementException;

@AllArgsConstructor
public class SimpleMaker {
    private final TreeMaker maker;
    private final SimpleTypes simpleTypes;

    public JCTree.JCExpression classLiteral(Type type) {
        return maker.ClassLiteral(type);
    }

    public JCTree.JCExpression type(Type rawLocalVariableType) {
        return maker.Type(rawLocalVariableType);
    }

    private JCTree.JCIdent identity(Symbol symbol) {
        return maker.Ident(symbol);
    }

    public void processArrayInitialization(ReifiedArrayInitialization array) {
        var expressions = array.enclosingExpressions().reverse();
        if(expressions.head == null || expressions.head.getTag() != JCTree.Tag.NEWARRAY){
            throw new IllegalArgumentException("Cannot process array init, corrupted expression stack: " + array);
        }

        castArrayToParameter(expressions);
        var varType = simpleTypes.createArray(array.typeVariableSymbol().asType());
        array.initialization().elemtype = type(simpleTypes.createTypeWithParameters(Object.class));
        array.initialization().type = simpleTypes.createTypeWithParameters(Object.class);
        if (!(array.enclosingStatement() instanceof JCTree.JCVariableDecl)) {
            return;
        }

        changeEnclosingVariableArrayType(array, varType);
    }

    private void changeEnclosingVariableArrayType(ReifiedArrayInitialization array, Type.ArrayType varType) {
        var enclosingVariable = (JCTree.JCVariableDecl) array.enclosingStatement();
        if (!TreeInfo.skipParens(enclosingVariable.init).equals(array.initialization())) {
            return;
        }

        enclosingVariable.init = maker.TypeCast(maker.TypeArray(type(array.typeVariableSymbol().asType())), array.initialization());
        enclosingVariable.vartype = type(varType);
        enclosingVariable.type = varType;
        enclosingVariable.sym.type = varType;
        enclosingVariable.sym.flags_field = 0;
        enclosingVariable.sym.adr = 0;
        suppressUncheckedCast(enclosingVariable);
    }

    // There is room for improvement in this method.
    // Performance shouldn't be that bad considering that the list isn't supposed to be massive,
    // though a better approach would be better.
    private void castArrayToParameter(List<JCTree.JCExpression> expressions) {
        expressions.forEach(expression -> {
            var headIndex = findHeadArgument(expressions, expression);
            if (headIndex == -1) {
                return;
            }

            castArrayToParameter(expressions, expression, headIndex);
        });
    }

    private int findHeadArgument(List<JCTree.JCExpression> expressions, JCTree.JCExpression expression) {
        switch (expression.getTag()){
            case NEWCLASS:
                return findHeadArgument(expressions, ((JCTree.JCNewClass) expression).getArguments());
            case APPLY:
                return findHeadArgument(expressions, ((JCTree.JCMethodInvocation) expression).getArguments());
            default:
                return -1;
        }
    }

    private int findHeadArgument(List<JCTree.JCExpression> expressions, List<JCTree.JCExpression> arguments) {
        for(var x = 0; x < arguments.size(); x++){
            var argument = TreeInfo.skipParens(arguments.get(x));
            if(argument.equals(expressions.head)){
                return x;
            }
        }

        return -1;
    }

    private void castArrayToParameter(List<JCTree.JCExpression> expressions, JCTree.JCExpression expression, int headIndex) {
        switch (expression.getTag()){
            case NEWCLASS:
                castArrayToClassParameter(expressions, (JCTree.JCNewClass) expression, headIndex);
                break;
            case APPLY:
                castArrayToMethodParameter(expressions, (JCTree.JCMethodInvocation) expression, headIndex);
                break;
            default:
                throw new IllegalArgumentException("Unexpected tree: " + expression);
        }
    }

    private void castArrayToClassParameter(List<JCTree.JCExpression> expressions, JCTree.JCNewClass candidate, int parameterIndex) {
        var invocationArguments = new ArrayList<>(candidate.getArguments());
        invocationArguments.remove(parameterIndex);
        invocationArguments.add(parameterIndex, maker.TypeCast(candidate.constructor.asType().getParameterTypes().get(parameterIndex), expressions.head));
        candidate.args = List.from(invocationArguments);
    }

    private void castArrayToMethodParameter(List<JCTree.JCExpression> expressions, JCTree.JCMethodInvocation candidate, int parameterIndex) {
        var invoked = TreeInfo.symbol(candidate.getMethodSelect());
        if(!(invoked instanceof Symbol.MethodSymbol)){
            return;
        }

        var invocationArguments = new ArrayList<>(candidate.getArguments());
        invocationArguments.remove(parameterIndex);
        invocationArguments.add(parameterIndex, maker.TypeCast(invoked.asType().getParameterTypes().get(parameterIndex), expressions.head));
        candidate.args = List.from(invocationArguments);
    }

    private void suppressUncheckedCast(JCTree.JCVariableDecl enclosing) {
        if (simpleTypes.hasUncheckedAnnotation(enclosing.getModifiers())) {
            return;
        }

        var oldAnnotations = new ArrayList<>(enclosing.mods.annotations);
        oldAnnotations.add(maker.Annotation(type(simpleTypes.createTypeWithParameters(SuppressWarnings.class)), List.of(maker.Literal("unchecked"))));
        enclosing.mods.annotations = List.from(oldAnnotations);
    }

    public void processMembers(ReifiedDeclaration declaration) {
        if (declaration.isClass()) {
            processClassMembers(declaration);
            return;
        }

        var parameter = declaration.methods().head;
        addParameter(declaration.typeParameter(), parameter);
    }

    public void processClassMembers(ReifiedDeclaration declaration) {
        var localVariable = createLocalVariable(declaration.typeParameter(), declaration.enclosingClass());
        declaration.methods().forEach(constructor ->
                addParameterAndAssign(declaration, constructor, localVariable));
    }

    private void addParameterAndAssign(ReifiedDeclaration declaration, JCTree.JCMethodDecl constructor, JCTree.JCVariableDecl localVariable) {
        var typeParameter = declaration.typeParameter();
        var parameter = addParameter(typeParameter, constructor);
        if (simpleTypes.record(declaration.enclosingClass().getModifiers())) {
            removeRecordSuperCall(constructor);
        }

        if (simpleTypes.compactConstructor(constructor)) {
            return;
        }

        var thisCall = maker.This(declaration.enclosingClass().sym.asType());
        var localVariableSelection = maker.Select(thisCall, localVariable.getName());
        var localVariableAssignment = maker.Assign(localVariableSelection, maker.Ident(parameter));
        var localVariableStatement = maker.at(constructor.pos).Exec(localVariableAssignment);
        var newStats = addStatement(typeParameter, constructor, localVariableStatement);
        constructor.body.stats = List.from(newStats);
    }

    // The canonical constructor of a record cannot call another constructor(super() or this()).
    // For some reason though, the generated super() method is still present which makes the compilation process fail.
    // I have no idea why this happens, maybe the compiler also removes said method call, but I couldn't find any evidence of this.
    // For now this is a workaround.
    private void removeRecordSuperCall(JCTree.JCMethodDecl constructor) {
        var statements = constructor.getBody().getStatements();
        if (!TreeInfo.isSuperCall(statements.head)) {
            return;
        }

        constructor.body.stats = List.filter(statements, statements.head);
    }

    private LinkedList<JCTree.JCStatement> addStatement(Symbol.TypeVariableSymbol typeParameter, JCTree.JCMethodDecl constructor, JCTree.JCExpressionStatement localVariableStatement) {
        var newStats = new LinkedList<>(constructor.getBody().getStatements());
        if (newStats.isEmpty()) {
            newStats.add(localVariableStatement);
            return newStats;
        }

        var firstStatement = newStats.getFirst();
        if (TreeInfo.isSuperCall(firstStatement)) {
            newStats.add(1, localVariableStatement);
            return newStats;
        }

        if (firstStatement.getKind() != Tree.Kind.EXPRESSION_STATEMENT) {
            newStats.addFirst(localVariableStatement);
            return newStats;
        }

        var expressionStatement = (JCTree.JCExpressionStatement) firstStatement;
        if (expressionStatement.getExpression().getKind() != Tree.Kind.METHOD_INVOCATION) {
            newStats.addFirst(localVariableStatement);
            return newStats;
        }

        var thisCall = (JCTree.JCMethodInvocation) expressionStatement.getExpression();
        if (!TreeInfo.isThisQualifier(thisCall.getMethodSelect())) {
            newStats.addFirst(localVariableStatement);
            return newStats;
        }

        var typeLiteral = createGenericMethodLiteral(constructor, typeParameter.getSimpleName());
        thisCall.args = thisCall.args.prepend(typeLiteral);
        return newStats;
    }

    private JCTree.JCVariableDecl createLocalVariable(Element typeParameter, JCTree.JCClassDecl enclosingClass) {
        var localVariableName = (Name) typeParameter.getSimpleName();
        long rawLocalVariableModifiers = createVariableModifiers(enclosingClass);

        var localVariableModifiers = maker.Modifiers(rawLocalVariableModifiers);

        var rawLocalVariableType = simpleTypes.createTypeWithParameters(Class.class, typeParameter);
        var localVariableType = type(rawLocalVariableType);

        var localVariable = maker.at(enclosingClass.pos)
                .VarDef(localVariableModifiers, localVariableName, localVariableType, null);
        localVariable.sym = new Symbol.VarSymbol(rawLocalVariableModifiers, localVariableName, rawLocalVariableType, enclosingClass.sym);

        enclosingClass.defs = enclosingClass.defs.prepend(localVariable);
        return localVariable;
    }

    private long createVariableModifiers(JCTree.JCClassDecl enclosingClass) {
        if (simpleTypes.record(enclosingClass.getModifiers())) {
            return Flags.PRIVATE | Flags.FINAL | Flags.COMPOUND | Flags.RECORD;
        }

        return Flags.PRIVATE | Flags.FINAL;
    }

    private JCTree.JCVariableDecl addParameter(Symbol.TypeVariableSymbol typeParameter, JCTree.JCMethodDecl method) {
        var paramType = simpleTypes.createTypeWithParameters(Class.class, typeParameter);
        var param = maker.at(method.pos).Param(typeParameter.getSimpleName(), paramType, method.sym);
        param.sym.adr = 0;
        method.params = method.params.prepend(param);

        var methodSymbol = (Symbol.MethodSymbol) method.sym;
        methodSymbol.params = methodSymbol.params.prepend(param.sym);

        var methodType = methodSymbol.type.asMethodType();
        methodType.argtypes = methodType.argtypes.prepend(paramType);

        return param;
    }

    public void addSuperParam(JCTree.JCExpression superType, JCTree.JCExpressionStatement firstStatement) {
        var superCall = (JCTree.JCMethodInvocation) firstStatement.getExpression();
        superCall.args = superCall.args.prepend(superType);
    }

    public JCTree.JCIdent createGenericMethodLiteral(JCTree.JCMethodDecl method, Name name) {
        return method.sym.getParameters()
                .stream()
                .filter(param -> param.getSimpleName().contentEquals(name))
                .findFirst()
                .map(this::identity)
                .orElseThrow(() -> new NoSuchElementException("Nested reified parameter cannot be processed if enclosing parameters have not been processed yet"));
    }

    public JCTree.JCIdent createGenericClassLiteral(JCTree.JCClassDecl clazz, Name name) {
        return clazz.getMembers()
                .stream()
                .filter(tree -> tree.getTag() == JCTree.Tag.VARDEF)
                .map(tree -> (JCTree.JCVariableDecl) tree)
                .filter(variable -> variable.getName().contentEquals(name))
                .findFirst()
                .map(variable -> variable.sym)
                .map(this::identity)
                .orElseThrow(() -> new NoSuchElementException("Nested reified parameter cannot be processed if enclosing parameters have not been processed yet"));
    }
}