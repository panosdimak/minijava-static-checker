package symboltable;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class ClassSymbol {
	String name;
	ClassSymbol parent;
	private ClassType classType = null;
	Map<String, VarSymbol> fields = new LinkedHashMap<>();
	Map<String, List<MethodSymbol>> methods = new LinkedHashMap<>();

	public ClassSymbol(String name, ClassSymbol parent) {
		this.name = name;
		this.parent = parent;
	}

	public Type asType() {
		if (this.classType == null) {
			this.classType = new ClassType(this);
		}
		return classType;
	}
}
