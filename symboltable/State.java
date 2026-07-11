package symboltable;

public class State {
    ClassSymbol currentClass;
    MethodSymbol currentMethod;
    String currentBlock;
    int regCounter;
    int labelCounter;
    StringBuilder irText;

    public String newReg() {
        regCounter++;
        return "%_" + String.valueOf(regCounter);
    }

    public String newLabel() {
        labelCounter++;
        return "%l" +String.valueOf(labelCounter);
    }

    public void emit(String format, Object... args) {
        irText.append(String.format(format, args)).append("\n");
    }
}
