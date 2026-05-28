package symboltable;

import java.util.ArrayList;
import java.util.List;

import syntaxtree.*;
import visitor.*;

public class TypeChecker extends GJDepthFirst<Type, State> {

    private final GlobalTable globalTable;

    public TypeChecker(GlobalTable globalTable) {
        this.globalTable = globalTable;
    }

    private String typeToString(syntaxtree.Type t) {
        return switch (t.f0.choice) {
            case ArrayType ar -> "int[]";
            case syntaxtree.BooleanType b -> "boolean";
            case IntegerType in -> "int";
            case Identifier i -> i.f0.tokenImage;
            default -> throw new IllegalStateException(
                "unexpected Type variant " + t.f0.choice.getClass().getName()
            );
        };
    }

    private VarSymbol fpToVarSymbol(FormalParameter fp) {
        return new VarSymbol(fp.f1.f0.tokenImage, typeToString(fp.f0));
    }

    private List<VarSymbol> nodeoptToVarSymbolList(NodeOptional nodeopt) {
        List<VarSymbol> list = new ArrayList<>();

        if (nodeopt.present()) {
            FormalParameterList fplist = ((FormalParameterList) nodeopt.node);

            list.add(fpToVarSymbol(fplist.f0));
            for (Node node : fplist.f1.f0.nodes) {
                FormalParameterTerm fpterm = ((FormalParameterTerm) node);
                list.add(fpToVarSymbol(fpterm.f1));
            }
        }
        return list;
    }

    private Type resolveIdentifier(Identifier id, State state) throws Exception {
        VarSymbol vs = state.currentMethod.locals.get(id.f0.tokenImage);
        if (vs == null) {
            for (ClassSymbol c = state.currentClass; c != null; c = c.parent) {
                vs = c.fields.get(id.f0.tokenImage);
                if (vs != null) {
                    break;
                }
            }
        }
        if (vs == null) {
            throw new SemanticError("undeclared identifier '" + id.f0.tokenImage + "'");
        }
        return vs.type;
    }

    private List<Type> exprListToTypeList(NodeOptional nodeopt, State state) throws Exception {
        List<Type> typeList = new ArrayList<>();

        if (nodeopt.present()) {
            ExpressionList exprList = ((ExpressionList) nodeopt.node);

            typeList.add(exprList.f0.accept(this, state));

            for (Node node : exprList.f1.f0.nodes) {
                ExpressionTerm exprTerm = ((ExpressionTerm) node);
                Type t = exprTerm.f1.accept(this, state);
                typeList.add(t);
            }
        }
        return typeList;
    }

    @Override
    public Type visit(MainClass n, State argu) throws Exception {
        ClassSymbol savedClass = argu.currentClass;
        MethodSymbol savedMethod = argu.currentMethod;

        String mainClassName = n.f1.f0.tokenImage;
        ClassSymbol mainClass = globalTable.lookupClass(mainClassName);
        argu.currentClass = mainClass;
        argu.currentMethod = mainClass.mainMethod;

        super.visit(n, argu);

        argu.currentClass = savedClass;
        argu.currentMethod = savedMethod;

        return null;
    }

    @Override
    public Type visit(ClassDeclaration n, State argu) throws Exception {
        ClassSymbol savedClass = argu.currentClass;

        String vClassName = n.f1.f0.tokenImage;
        ClassSymbol vClass = globalTable.lookupClass(vClassName);
        argu.currentClass = vClass;

        super.visit(n, argu);

        argu.currentClass = savedClass;

        return null;
    }

    @Override
    public Type visit(ClassExtendsDeclaration n, State argu) throws Exception {
        ClassSymbol savedClass = argu.currentClass;

        String vClassName = n.f1.f0.tokenImage;
        ClassSymbol vClass = globalTable.lookupClass(vClassName);
        argu.currentClass = vClass;

        super.visit(n, argu);

        argu.currentClass = savedClass;

        return null;
    }

    @Override
    public Type visit(MethodDeclaration n, State argu) throws Exception {
        MethodSymbol savedMethod = argu.currentMethod;
        String vmethodName = n.f2.f0.tokenImage;
        List<VarSymbol> vparams = nodeoptToVarSymbolList(n.f4);

        List<String> vParamStrings = new ArrayList<>();
        for (VarSymbol v : vparams) {
            vParamStrings.add(v.typeName);
        }

        MethodSymbol found = null;
        for (MethodSymbol vmethod : argu.currentClass.methods.get(vmethodName)) {
            if (vmethod.paramList.equals(vParamStrings)) {
                found = vmethod;
                break;
            }
        }
        if (found == null) {
            throw new IllegalStateException("no MethodSymbol for " + vmethodName +
                                            " in " + argu.currentClass.name);
        }
        argu.currentMethod = found;

        super.visit(n, argu);

        Type returnExprT = n.f10.accept(this, argu);
        if (!(argu.currentMethod.returnType.isAssignableFrom(returnExprT))) {
            throw new SemanticError("return type of method " + argu.currentMethod.name +
                                    " in class " + argu.currentMethod.ownerClass +
                                    " is '" + argu.currentMethod.returnType.name +
                                    "', got '" + returnExprT.name + "'");
        }

        argu.currentMethod = savedMethod;

        return null;
    }

    @Override
    public Type visit(IntegerLiteral n, State argu) throws Exception {

        return new IntType();
    }

    @Override
    public Type visit(TrueLiteral n, State argu) throws Exception {

        return new BooleanType();
    }

    @Override
    public Type visit(FalseLiteral n, State argu) throws Exception {

        return new BooleanType();
    }

    @Override
    public Type visit(ThisExpression n, State argu) throws Exception {
        if (argu.currentMethod == argu.currentClass.mainMethod) {
            throw new SemanticError("cannot use 'this' inside main");
        }
        return argu.currentClass.asType();
    }

    @Override
    public Type visit(PrimaryExpression n, State argu) throws Exception {
        return switch (n.f0.choice) {
            case IntegerLiteral il -> il.accept(this, argu);
            case TrueLiteral tl -> tl.accept(this, argu);
            case FalseLiteral fl -> fl.accept(this, argu);
            case Identifier id -> resolveIdentifier(id, argu);
            case ThisExpression te -> te.accept(this, argu);
            case ArrayAllocationExpression ae -> ae.accept(this, argu);
            case AllocationExpression ae -> ae.accept(this, argu);
            case NotExpression ne -> ne.accept(this, argu);
            default -> throw new IllegalStateException();
        };
    }

    @Override
    public Type visit(Expression n, State argu) throws Exception {
        return switch (n.f0.choice) {
            case AndExpression ae -> ae.accept(this, argu);
            case CompareExpression ce -> ce.accept(this, argu);
            case PlusExpression pe -> pe.accept(this, argu);
            case MinusExpression me -> me.accept(this, argu);
            case TimesExpression te -> te.accept(this, argu);
            case ArrayLookup al -> al.accept(this, argu);
            case ArrayLength al -> al.accept(this, argu);
            case MessageSend ms -> ms.accept(this, argu);
            case PrimaryExpression pe -> pe.accept(this, argu);
            default -> throw new IllegalStateException();
        };
    }

    @Override
    public Type visit(NotExpression n, State argu) throws Exception {
        Type t = n.f1.accept(this, argu);
        if (t instanceof BooleanType) {
            return new BooleanType();
        }
        throw new SemanticError("operand of non-boolean type '" + t.name + "' not allowed in ! expression");
    }

    @Override
    public Type visit(AndExpression n, State argu) throws Exception {
        Type t1 = n.f0.accept(this, argu);
        Type t2 = n.f2.accept(this, argu);
        if ((t1 instanceof BooleanType) && (t2 instanceof BooleanType)) {
            return new BooleanType();
        }
        throw new SemanticError("operands of && expression must be of boolean type, got '" + t1.name + "' and '" + t2.name + "'");
    }

    @Override
    public Type visit(CompareExpression n, State argu) throws Exception {
        Type t1 = n.f0.accept(this, argu);
        Type t2 = n.f2.accept(this, argu);
        if ((t1 instanceof IntType) && (t2 instanceof IntType)) {
            return new BooleanType();
        }
        throw new SemanticError("operands of < expression must be of int type, got '" + t1.name + "' and '" + t2.name + "'");
    }

    @Override
    public Type visit(PlusExpression n, State argu) throws Exception {
        Type t1 = n.f0.accept(this, argu);
        Type t2 = n.f2.accept(this, argu);
        if ((t1 instanceof IntType) && (t2 instanceof IntType)) {
            return new IntType();
        }
        throw new SemanticError("operands of + expression must be of int type, got '" + t1.name + "' and '" + t2.name + "'");
    }

    @Override
    public Type visit(MinusExpression n, State argu) throws Exception {
        Type t1 = n.f0.accept(this, argu);
        Type t2 = n.f2.accept(this, argu);
        if ((t1 instanceof IntType) && (t2 instanceof IntType)) {
            return new IntType();
        }
        throw new SemanticError("operands of - expression must be of int type, got '" + t1.name + "' and '" + t2.name + "'");
    }

    @Override
    public Type visit(TimesExpression n, State argu) throws Exception {
        Type t1 = n.f0.accept(this, argu);
        Type t2 = n.f2.accept(this, argu);
        if ((t1 instanceof IntType) && (t2 instanceof IntType)) {
            return new IntType();
        }
        throw new SemanticError("operands of * expression must be of int type, got '" + t1.name + "' and '" + t2.name + "'");
    }

    @Override
    public Type visit(ArrayLookup n, State argu) throws Exception {
        Type t1 = n.f0.accept(this, argu);
        Type t2 = n.f2.accept(this, argu);
        if ((t1 instanceof IntArrayType) && (t2 instanceof IntType)) {
            return new IntType();
        }
        throw new SemanticError("array in [] expression must be of int[] type and index must be of int type, got '" + t1.name + "' and '" + t2.name + "'");
    }

    @Override
    public Type visit(ArrayLength n, State argu) throws Exception {
        Type t = n.f0.accept(this, argu);
        if (t instanceof IntArrayType) {
            return new IntType();
        }
        throw new SemanticError("operand of non-int[] type '" + t.name + "' not allowed in array length expression");
    }

    @Override
    public Type visit(ArrayAllocationExpression n, State argu) throws Exception {
        Type t = n.f3.accept(this, argu);
        if (t instanceof IntType) {
            return new IntArrayType();
        }
        throw new SemanticError("expression in array allocation must be of type 'int', got '" + t.name + "'");
    }

    @Override
    public Type visit(AllocationExpression n, State argu) throws Exception {
        ClassSymbol c = globalTable.lookupClass(n.f1.f0.tokenImage);
        if (c == null) {
            throw new SemanticError("undeclared class '" + n.f1.f0.tokenImage + "' cannot be used for new allocation");
        }
        return c.asType();
    }

    @Override
    public Type visit(AssignmentStatement n, State argu) throws Exception {
        Type lt = resolveIdentifier(n.f0, argu);
        Type rt = n.f2.accept(this, argu);
        if (!lt.isAssignableFrom(rt)) {
            throw new SemanticError("cannot assign '" + rt.name + "' to identifier '"
                                    + n.f0.f0.tokenImage + "' of type '" + lt.name + "'");
        }
        return null;
    }

    @Override
    public Type visit(ArrayAssignmentStatement n, State argu) throws Exception {
        Type arrT = resolveIdentifier(n.f0, argu);
        if (!(arrT instanceof IntArrayType)) {
            throw new SemanticError("identifier '" + n.f0.f0.tokenImage + "' of type '" + arrT.name + "' cannot be indexed");
        }
        Type indexT = n.f2.accept(this, argu);
        if (!(indexT instanceof IntType)) {
            throw new SemanticError("index of array '" + n.f0.f0.tokenImage + "' must be of type 'int', got '" + indexT.name + "'");
        }
        Type rT = n.f5.accept(this, argu);
        if (!(rT instanceof IntType)) {
            throw new SemanticError("cannot assign '" + rT.name + "' to element of array '" + n.f0.f0.tokenImage + "' of type 'int'");
        }

        return null;
    }

    @Override
    public Type visit(IfStatement n, State argu) throws Exception {
        Type ifT = n.f2.accept(this, argu);
        if (!(ifT instanceof BooleanType)) {
            throw new SemanticError("expression in if statement must evaluate to a boolean, got '" + ifT.name + "'");
        }
        n.f4.accept(this, argu);
        n.f6.accept(this, argu);

        return null;
    }

    @Override
    public Type visit(WhileStatement n, State argu) throws Exception {
        Type whileT = n.f2.accept(this, argu);
        if (!(whileT instanceof BooleanType)) {
            throw new SemanticError("expression in while statement must evaluate to a boolean, got '" + whileT.name + "'");
        }
        n.f4.accept(this, argu);

        return null;
    }

    @Override
    public Type visit(PrintStatement n, State argu) throws Exception {
        Type printT = n.f2.accept(this, argu);
        if (!(printT instanceof IntType)) {
            throw new SemanticError("expression in print statement must be of type 'int', got '" + printT.name + "'");
        }

        return null;
    }

    @Override
    public Type visit(MessageSend n, State argu) throws Exception {
        Type t = n.f0.accept(this, argu);
        if (t instanceof ClassType cT) {
            ClassSymbol cs = cT.classSymbol;
            List<Type> argTypes = exprListToTypeList(n.f4, argu);
            for (ClassSymbol c = cs; c != null; c = c.parent) {
                List<MethodSymbol> mList = c.methods.get(n.f2.f0.tokenImage);

                if (mList != null) {
                    for (MethodSymbol m : mList) {
                        if (m.paramList.size() == argTypes.size()) {
                            boolean allMatch = true;
                            List<VarSymbol> mLocals = new ArrayList<>(m.locals.values());
                            for (int i = 0; i < m.paramList.size(); i++) {
                                if (!(mLocals.get(i).type.isAssignableFrom(argTypes.get(i)))) {
                                    allMatch = false;
                                    break;
                                }
                            }

                            if (allMatch) {
                                return m.returnType;
                            }
                        }
                    }
                }
            }
            List<String> argTypesStringList = new ArrayList<>();
            for (Type argType : argTypes) {
                argTypesStringList.add(argType.name);
            }
            throw new SemanticError("no overload of " + cs.name + "." + n.f2.f0.tokenImage + " accepts arguments (" + String.join(", ", argTypesStringList) + ")");
        }
        throw new SemanticError("cannot call method on non-class type '" + t.name + "'");
    }
}
