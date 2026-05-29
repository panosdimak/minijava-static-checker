import syntaxtree.*;
import visitor.*;
import symboltable.GlobalTable;

import java.util.List;

import symboltable.ClassSymbol;
import symboltable.MethodSymbol;
import symboltable.VarSymbol;
import util.Util;

class STBuilder extends DepthFirstVisitor {
    GlobalTable globalTable = new GlobalTable();
    ClassSymbol currentClass = null;
    MethodSymbol currentMethod = null;

    @Override
    public void visit(MainClass n) throws Exception {
        String mainClassName = n.f1.f0.tokenImage;
        ClassSymbol newClass = globalTable.addClass(mainClassName, null);

        currentMethod = newClass.setMainMethod(n.f11.f0.tokenImage);
        currentClass = newClass;

        super.visit(n);

        currentMethod = null;
        currentClass = null;
    }

    @Override
    public void visit(ClassDeclaration n) throws Exception {
        String className = n.f1.f0.tokenImage;
        ClassSymbol newClass = globalTable.addClass(className, null);

        currentClass = newClass;

        super.visit(n);

        currentClass = null;
    }

    @Override
    public void visit(ClassExtendsDeclaration n) throws Exception {
        String className = n.f1.f0.tokenImage;
        String parentName = n.f3.f0.tokenImage;
        ClassSymbol newClass = globalTable.addClass(className, parentName);


        currentClass = newClass;

        super.visit(n);

        currentClass = null;
    }

    @Override
    public void visit(VarDeclaration n) throws Exception {
        String varName = n.f1.f0.tokenImage;
        String varTypeName = Util.typeToString(n.f0);

        if (currentMethod == null) {
            currentClass.addField(varName, varTypeName);
        } else {
            currentMethod.addLocal(varName, varTypeName);
        }

    }

    @Override
    public void visit(MethodDeclaration n) throws Exception {
        String returnTypeName = Util.typeToString(n.f1);
        String methodName = n.f2.f0.tokenImage;

        List<VarSymbol> params = Util.nodeoptToVarSymbolList(n.f4);

        MethodSymbol newMethod = currentClass.addMethod(methodName, returnTypeName, params);
        currentMethod = newMethod;

        super.visit(n);

        currentMethod = null;
    }

    @Override
    public void visit(FormalParameter n) throws Exception {

    }

}
