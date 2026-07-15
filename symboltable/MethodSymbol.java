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
    int byteOffset = -1;
    Map<String, VarSymbol> locals = new LinkedHashMap<>();
    List<String> paramList = new ArrayList<>();

    public MethodSymbol(ClassSymbol ownerClass, String name, String returnTypeName) {
        this.ownerClass = ownerClass;
        this.name = name;
        this.returnTypeName = returnTypeName;
    }

    public void addLocal(String name, String typeName) throws SemanticError {
        if (locals.containsKey(name) || (this == ownerClass.mainMethod && name.equals(ownerClass.mainParamName))) {
            throw new SemanticError("duplicate variable '" + name + "' in method '" + ownerClass.name + "." + this.name + "'");
        }

        VarSymbol newVar = new VarSymbol(name, typeName);
        locals.put(name, newVar);
    }
}
