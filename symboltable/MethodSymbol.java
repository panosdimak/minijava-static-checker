package symboltable;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class MethodSymbol {
	ClassSymbol ownerClass;
	String name;
	String returnTypeName;
	Type returnType;
	Map<String, VarSymbol> locals = new LinkedHashMap<>();
	List<String> paramList = new ArrayList<>();

	public MethodSymbol(ClassSymbol ownerClass, String name, String returnTypeName) {
		this.ownerClass = ownerClass;
		this.name = name;
		this.returnTypeName = returnTypeName;
	}
}
