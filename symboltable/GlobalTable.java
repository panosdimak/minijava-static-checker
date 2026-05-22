package symboltable;

import java.util.LinkedHashMap;
import java.util.Map;

public class GlobalTable {

	private Map<String, ClassSymbol> classes = new LinkedHashMap<>();

	public void addClass(String name, String parentName) throws SemanticError {
		if (classes.containsKey(name)) {
			throw new SemanticError("duplicate class " + name);
		}

		ClassSymbol parent = classes.get(parentName);
		if (parentName != null && parent == null) {
			throw new SemanticError("unknown parent class " + parentName);
		}

		ClassSymbol ct = new ClassSymbol(name, parent);
		classes.put(name, ct);
	}

	public ClassSymbol lookupClass(String name) {
		return classes.get(name);
	}
}
