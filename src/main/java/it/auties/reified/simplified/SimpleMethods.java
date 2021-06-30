package it.auties.reified.simplified;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.FatalError;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import lombok.AllArgsConstructor;

import java.util.Optional;

@AllArgsConstructor
public class SimpleMethods {
    private final SimpleTypes simpleTypes;
    private final Resolve resolve;
    private final Enter enter;
    private final MemberEnter memberEnter;
    private final Attr attr;

    public Optional<Symbol.MethodSymbol> resolveMethod(JCTree.JCClassDecl enclosingClass, JCTree.JCMethodDecl enclosingMethod, JCTree.JCMethodInvocation invocation){
        try {
            var invoked = invocation.getMethodSelect();

            var classType = enclosingClass.sym.type;
            var classEnv = enter.getClassEnv(classType.tsym);
            var methodEnv = memberEnter.getMethodEnv(enclosingMethod, classEnv);
            return Optional.of(
                    resolve.resolveInternalMethod(
                            invocation.pos(),
                            methodEnv,
                            deduceClassType(invoked, classType),
                            deduceMethodName(invoked),
                            invocation.getArguments().map(argument -> simpleTypes.resolveType(argument, methodEnv)),
                            List.nil()
                    )
            );

        }catch (FatalError error){
            return Optional.empty();
        }catch (Exception ex){
            ex.printStackTrace();
            return Optional.empty();
        }
    }

    private Type deduceClassType(JCTree.JCExpression invoked, Type classType) {
        if(invoked instanceof JCTree.JCIdent){
            return classType;
        }

        if(invoked instanceof JCTree.JCFieldAccess){
            return deduceClassType(((JCTree.JCFieldAccess) invoked).selected, classType);
        }

        throw new RuntimeException("Cannot deduce class type");
    }

    private Name deduceMethodName(JCTree.JCExpression invoked) {
        if(invoked instanceof JCTree.JCIdent){
            return ((JCTree.JCIdent) invoked).getName();
        }

        if(invoked instanceof JCTree.JCFieldAccess){
            return ((JCTree.JCFieldAccess) invoked).getIdentifier();
        }

        throw new RuntimeException("Cannot deduce class name");
    }

    public Type resolveMethodType(JCTree.JCMethodInvocation invocation, Env<AttrContext> env){
        var args = invocation.getTypeArguments();
        if(args != null && args.length() != 0){
            return simpleTypes.resolveFirstGenericType(args, env).orElseThrow().asType();
        }

        return simpleTypes.resolveType(invocation, env);
    }
}
