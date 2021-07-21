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
import it.auties.reified.model.ReifiedDeclaration;
import lombok.AllArgsConstructor;

import javax.lang.model.element.Element;
import java.util.LinkedList;

@AllArgsConstructor
public class SimpleMaker {
    private final TreeMaker treeMaker;
    private final SimpleClasses simpleClasses;
    private final SimpleTypes simpleTypes;

    public JCTree.JCExpression classLiteral(Type type){
        return treeMaker.ClassLiteral(type);
    }

    private JCTree.JCIdent identity(Symbol symbol){
        return treeMaker.Ident(symbol);
    }

    public void processMembers(ReifiedDeclaration declaration) {
        if(declaration.isClass()){
            processClassMembers(declaration);
            return;
        }

        var parameter = declaration.methods().head;
        addParameter(declaration.typeParameter(), parameter, false);
    }

    public void processClassMembers(ReifiedDeclaration declaration) {
        var localVariable = createLocalVariable(declaration.typeParameter(), declaration.enclosingClass());
        declaration.methods().forEach(constructor ->
                addParameterAndAssign(declaration, constructor, localVariable));
    }

    private void addParameterAndAssign(ReifiedDeclaration declaration, JCTree.JCMethodDecl constructor, JCTree.JCVariableDecl localVariable) {
        var typeParameter = declaration.typeParameter();
        var isRecord = simpleClasses.isRecord(declaration.enclosingClass());
        var parameter = addParameter(typeParameter, constructor, isRecord);
        if(isRecord){
            return;
        }

        var thisCall = treeMaker.This(declaration.enclosingClass().sym.asType());
        var localVariableSelection = treeMaker.Select(thisCall, localVariable.getName());
        var localVariableAssignment = treeMaker.Assign(localVariableSelection, treeMaker.Ident(parameter));
        var localVariableStatement = treeMaker.at(constructor.pos).Exec(localVariableAssignment);
        var newStats = addStatement(typeParameter, constructor, localVariableStatement);
        constructor.body.stats = List.from(newStats);
    }

    private LinkedList<JCTree.JCStatement> addStatement(Symbol.TypeVariableSymbol typeParameter, JCTree.JCMethodDecl constructor, JCTree.JCExpressionStatement localVariableStatement) {
        var newStats = new LinkedList<>(constructor.body.stats);
        if(newStats.isEmpty()){
            newStats.add(localVariableStatement);
            return newStats;
        }

        var firstStatement = newStats.getFirst();
        if(TreeInfo.isSuperCall(firstStatement)){
            newStats.add(1, localVariableStatement);
            return newStats;
        }

        if(firstStatement.getKind() != Tree.Kind.EXPRESSION_STATEMENT){
            newStats.addFirst(localVariableStatement);
            return newStats;
        }

        var expressionStatement = (JCTree.JCExpressionStatement) firstStatement;
        if(expressionStatement.getExpression().getKind() != Tree.Kind.METHOD_INVOCATION){
            newStats.addFirst(localVariableStatement);
            return newStats;
        }

        var thisCall = (JCTree.JCMethodInvocation) expressionStatement.getExpression();
        if(!TreeInfo.isThisQualifier(thisCall.getMethodSelect())){
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

        var localVariableModifiers = treeMaker.Modifiers(rawLocalVariableModifiers);

        var rawLocalVariableType = simpleTypes.createTypeWithParameter(Class.class, typeParameter);
        var localVariableType = treeMaker.Type(rawLocalVariableType);

        var localVariable = treeMaker.at(enclosingClass.pos)
                .VarDef(localVariableModifiers, localVariableName, localVariableType, null);
        localVariable.sym = new Symbol.VarSymbol(rawLocalVariableModifiers, localVariableName, rawLocalVariableType, enclosingClass.sym);

        enclosingClass.defs = enclosingClass.defs.prepend(localVariable);
        return localVariable;
    }

    private long createVariableModifiers(JCTree.JCClassDecl enclosingClass) {
        if(simpleClasses.isRecord(enclosingClass)){
            return Flags.PRIVATE | Flags.FINAL | Flags.RECORD;
        }

        return Flags.PRIVATE | Flags.FINAL;
    }

    private JCTree.JCVariableDecl addParameter(Element typeParameter, JCTree.JCMethodDecl method, boolean isRecord) {
        var paramType = simpleTypes.createTypeWithParameter(Class.class, typeParameter);
        var param = treeMaker.at(method.pos)
                .Param((Name) typeParameter.getSimpleName(), paramType, method.sym);
        param.sym.adr = 0;
        if(isRecord){
            param.mods.flags |= Flags.RECORD;
        }

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
                .orElseThrow(() -> new AssertionError("Nested reified parameter cannot be processed if enclosing parameters have not been processed yet"));
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
                .orElseThrow(() -> new AssertionError("Nested reified parameter cannot be processed if enclosing parameters have not been processed yet"));
    }
}
