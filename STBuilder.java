import syntaxtree.*;
import visitor.*;
import symboltable.*;

class STBuilder extends DepthFirstVisitor {
	GlobalTable globalTable = new GlobalTable();
	ClassSymbol currentClass = null;
	MethodSymbol currentMethod = null;

	@Override
	public void visit(MainClass n) throws Exception {
		String mainClassName = n.f1.f0.tokenImage;
		ClassSymbol newClass = globalTable.addClass(mainClassName, null);
		System.out.println("Class: " + mainClassName);

		currentClass = newClass;

		super.visit(n);

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
		super.visit(n);
	}

	@Override
	public void visit(MethodDeclaration n) throws Exception {
		super.visit(n);
	}

	@Override
	public void visit(FormalParameter n) throws Exception {

	}

}
