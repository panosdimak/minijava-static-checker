package symboltable;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class ClassSymbol {
	String name;
	ClassSymbol parent;
	Map<String, VarSymbol> fields = new LinkedHashMap<>();
	Map<String, List<MethodSymbol>> methods = new LinkedHashMap<>();
}
