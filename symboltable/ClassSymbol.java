package symboltable;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class ClassSymbol {
    String name;
    ClassSymbol parent;
    private ClassType classType = null;

    MethodSymbol mainMethod = null;
    String mainParamName = null;

    Map<String, VarSymbol> fields = new LinkedHashMap<>();
    Map<String, List<MethodSymbol>> methods = new LinkedHashMap<>();
    List<MethodSymbol> methodsInOrder = new ArrayList<>();

    int fieldBlockSize = 0;
    int vtableSize = 0;

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

    public void addField(String name, String typeName) throws SemanticError {
        if (fields.containsKey(name)) {
            throw new SemanticError("duplicate field '" + name + "' in class '" + this.name + "'");
        }

        VarSymbol vs = new VarSymbol(name, typeName);
        fields.put(name, vs);
    }

    public MethodSymbol setMainMethod(String paramName) {
        mainMethod = new MethodSymbol(this, "main", "void");
        mainParamName = paramName;
        return mainMethod;
    }

    public MethodSymbol addMethod(String name, String returnTypeName, List<VarSymbol> params) throws SemanticError {
        MethodSymbol newMethod = new MethodSymbol(this, name, returnTypeName);
        for (VarSymbol param : params) {
            newMethod.paramList.add(param.typeName);
            newMethod.addLocal(param.name, param.typeName);
        }

        List<MethodSymbol> methodList = methods.computeIfAbsent(name, l -> new ArrayList<>());
        for (MethodSymbol method : methodList) {
            if (method.paramList.equals(newMethod.paramList)) {
                throw new SemanticError("duplicate method '" + name + "' in class '" + this.name + "'");
            }
        }
        methodList.add(newMethod);
        methodsInOrder.add(newMethod);

        return newMethod;
    }
}
