package symboltable;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class MethodSymbol {
	ClassSymbol ownerClass;
	String name;
	Type returnType;
	Map<String, VarSymbol> locals = new LinkedHashMap<>();

}
