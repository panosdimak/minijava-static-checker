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
        for (MethodSymbol vmethod : argu.currentClass.methods.get(
            vmethodName))
        {
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
            throw new SemanticError("cannot use this inside main");
        }
        return argu.currentClass.asType();
    }

    @Override
    public Type visit(PrimaryExpression n, State argu) throws Exception {
        return switch (n.f0.choice) {
            case IntegerLiteral il -> il.accept(this, argu);
            case TrueLiteral tl -> tl.accept(this, argu);
            case FalseLiteral fl -> fl.accept(this, argu);
            case Identifier id -> {
                VarSymbol vs = argu.currentMethod.locals.get(id.f0.tokenImage);
                if (vs == null) {
                    for (ClassSymbol c = argu.currentClass; c != null; c = c.parent) {
                        vs = c.fields.get(id.f0.tokenImage);
                        if (vs != null) {
                            break;
                        }
                    }
                }
                if (vs == null) {
                    throw new SemanticError("undeclared identifier " + id.f0.tokenImage);
                }
                yield vs.type;
            }
            case ThisExpression te -> te.accept(this, argu);
            case NotExpression ne -> ne.accept(this, argu);
            default -> throw new IllegalStateException();
        };
    }

    @Override
    public Type visit(Expression n, State argu) throws Exception {
        return switch (n.f0.choice) {
            case AndExpression ae -> ae.accept(this, argu);
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
        throw new SemanticError("operand of non-boolean type " + t.name + " not allowed in ! expression");
    }

    @Override
    public Type visit(AndExpression n, State argu) throws Exception {
        Type t1 = n.f0.accept(this, argu);
        Type t2 = n.f2.accept(this, argu);
        if ((t1 instanceof BooleanType) && (t2 instanceof BooleanType)) {
            return new BooleanType();
        }
        throw new SemanticError("operands of && expression must be of boolean type, got " + t1.name + " and " + t2.name);
    }
}
