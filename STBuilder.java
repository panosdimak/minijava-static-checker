import syntaxtree.*;
import visitor.*;
import symboltable.GlobalTable;

import java.util.ArrayList;
import java.util.List;

import symboltable.ClassSymbol;
import symboltable.MethodSymbol;
import symboltable.VarSymbol;

class STBuilder extends DepthFirstVisitor {
	GlobalTable globalTable = new GlobalTable();
	ClassSymbol currentClass = null;
	MethodSymbol currentMethod = null;

	private String typeToString(Type t) {
		return switch (t.f0.choice) {
			case ArrayType ar -> "int[]";
			case BooleanType b -> "boolean";
			case IntegerType in -> "int";
			case Identifier i -> i.f0.tokenImage;
			default -> throw new IllegalStateException("unexpected Type variant " + t.f0.choice.getClass().getName());
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
	public void visit(MainClass n) throws Exception {
		String mainClassName = n.f1.f0.tokenImage;
		ClassSymbol newClass = globalTable.addClass(mainClassName, null);
		MethodSymbol mainMethod = new MethodSymbol(newClass, "main", "void");
		System.out.println("Class: " + mainClassName);

		currentMethod = mainMethod;
		currentClass = newClass;

		super.visit(n);

		currentMethod = null;
		currentClass = null;
	}

	@Override
	public void visit(ClassDeclaration n) throws Exception {
		String className = n.f1.f0.tokenImage;
		ClassSymbol newClass = globalTable.addClass(className, null);
		System.out.println("Class: " + className);

		currentClass = newClass;

		super.visit(n);

		currentClass = null;
	}

	@Override
	public void visit(ClassExtendsDeclaration n) throws Exception {
		String className = n.f1.f0.tokenImage;
		String parentName = n.f3.f0.tokenImage;
		ClassSymbol newClass = globalTable.addClass(className, parentName);

		System.out.println("Class: " + className);

		currentClass = newClass;

		super.visit(n);

		currentClass = null;
	}

	@Override
	public void visit(VarDeclaration n) throws Exception {
		String varName = n.f1.f0.tokenImage;
		String varTypeName = typeToString(n.f0);

		if (currentMethod == null) {
			currentClass.addField(varName, varTypeName);
			System.out.println("\tfield: " + varName);
		} else {
			currentMethod.addLocal(varName, varTypeName);
			System.out.println("\t\tlocal: " + varName);
		}

	}

	@Override
	public void visit(MethodDeclaration n) throws Exception {
		String returnTypeName = typeToString(n.f1);
		String methodName = n.f2.f0.tokenImage;

		List<VarSymbol> params = nodeoptToVarSymbolList(n.f4);

		MethodSymbol newMethod = currentClass.addMethod(methodName, returnTypeName, params);
		currentMethod = newMethod;

		System.out.println("\tmethod: " + methodName);

		super.visit(n);

		currentMethod = null;
	}

	@Override
	public void visit(FormalParameter n) throws Exception {

	}

}
