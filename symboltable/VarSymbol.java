package symboltable;

public class VarSymbol {
    String name;
    String typeName;
    Type type;

    public VarSymbol(String name, String typeName) {
        this.name = name;
        this.typeName = typeName;
    }
}
