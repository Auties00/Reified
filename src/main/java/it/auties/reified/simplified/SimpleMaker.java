package it.auties.reified.simplified;

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
import java.lang.reflect.Modifier;
import java.util.LinkedList;

@AllArgsConstructor
public class SimpleMaker {
    private final SimpleTypes simpleTypes;
    private final TreeMaker treeMaker;

    public JCTree.JCIdent identity(Symbol symbol){
        return treeMaker.Ident(symbol);
    }

    public JCTree.JCExpression classLiteral(Type type){
        return treeMaker.ClassLiteral(type);
    }

    public void processMembers(ReifiedDeclaration declaration) {
        if(declaration.isClass()){
            var localVariable = createLocalVariable(declaration.typeParameter(), declaration.enclosingClass());
            var constructors = declaration.methods();
            constructors.forEach(constructor -> addParameterAndAssign(declaration.typeParameter(), declaration.enclosingClass(), localVariable, constructor));
            return;
        }

        var parameter = declaration.methods().head;
        addParameter(declaration.typeParameter(), parameter);
    }

    private void addParameterAndAssign(Element typeParameter, JCTree.JCClassDecl typeParentTree, JCTree.JCVariableDecl localVariable, JCTree.JCMethodDecl constructor) {
        var parameter = addParameter(typeParameter, constructor);
        var thisCall = treeMaker.This(typeParentTree.sym.asType());
        var localVariableSelection = treeMaker.Select(thisCall, localVariable.getName());
        var localVariableAssignment = treeMaker.Assign(localVariableSelection, treeMaker.Ident(parameter));

        var localVariableStatement = treeMaker.at(constructor.pos).Exec(localVariableAssignment);
        var newStats = new LinkedList<>(constructor.body.stats);
        addStatement(localVariableStatement, newStats);
        constructor.body.stats = List.from(newStats);
    }

    private void addStatement(JCTree.JCExpressionStatement localVariableStatement, LinkedList<JCTree.JCStatement> newStats) {
        if(newStats.isEmpty()){
            newStats.add(localVariableStatement);
            return;
        }

        var firstStatement = newStats.getFirst();
        if(TreeInfo.isSuperCall(firstStatement)){
            newStats.add(1, localVariableStatement);
            return;
        }

        newStats.addFirst(localVariableStatement);
    }

    private JCTree.JCVariableDecl createLocalVariable(Element typeParameter, JCTree.JCClassDecl enclosingClass) {
        var localVariableName = (Name) typeParameter.getSimpleName();
        var localVariableModifiers = treeMaker.Modifiers(Modifier.PRIVATE | Modifier.FINAL);
        var localVariableType = treeMaker.Type(simpleTypes.createTypeWithParameter(Class.class, typeParameter));
        var localVariable = treeMaker.at(enclosingClass.pos).VarDef(localVariableModifiers, localVariableName, localVariableType, null);
        enclosingClass.defs = enclosingClass.defs.prepend(localVariable);
        return localVariable;
    }

    private JCTree.JCVariableDecl addParameter(Element typeParameter, JCTree.JCMethodDecl method) {
        var paramType = simpleTypes.createTypeWithParameter(Class.class, typeParameter);
        var param = treeMaker.at(method.pos).Param((Name) typeParameter.getSimpleName(), paramType, method.sym);
        param.sym.adr = 0;

        method.params = method.params.prepend(param);

        var methodSymbol = (Symbol.MethodSymbol) method.sym;
        methodSymbol.params = methodSymbol.params.prepend(param.sym);

        var methodType = methodSymbol.type.asMethodType();
        methodType.argtypes = methodType.argtypes.prepend(paramType);

        return param;
    }
}
