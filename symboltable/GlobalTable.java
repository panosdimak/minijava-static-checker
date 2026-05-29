package symboltable;

import java.util.LinkedHashMap;
import java.util.Map;

public class GlobalTable {

    Map<String, ClassSymbol> classes = new LinkedHashMap<>();

    public ClassSymbol addClass(String name, String parentName) throws SemanticError {
        if (classes.containsKey(name)) {
            throw new SemanticError("duplicate class '" + name + "'");
        }

        ClassSymbol parent = classes.get(parentName);
        if (parentName != null && parent == null) {
            throw new SemanticError("unknown parent class '" + parentName + "'");
        }

        ClassSymbol ct = new ClassSymbol(name, parent);
        classes.put(name, ct);

        return ct;
    }

    public ClassSymbol lookupClass(String name) {
        return classes.get(name);
    }

    public Type resolveType(String name) throws SemanticError {
        if ("int".equals(name)) {
            return new IntType();
        } else if ("boolean".equals(name)) {
            return new BooleanType();
        } else if ("int[]".equals(name)) {
            return new IntArrayType();
        } else {
            ClassSymbol ct = this.lookupClass(name);
            if (ct != null) {
                return ct.asType();
            } else {
                throw new SemanticError("unrecognized type '" + name + "'");
            }
        }
    }
}
