package symboltable;

import java.util.IdentityHashMap;

import syntaxtree.MessageSend;

public class State {
    ClassSymbol currentClass;
    MethodSymbol currentMethod;
    String currentBlock;
    int regCounter;
    int labelCounter;
    StringBuilder irText;
    IdentityHashMap<MessageSend, MethodSymbol> resolvedCalls = new IdentityHashMap<>();

    public String newReg() {
        regCounter++;
        return "%_" + String.valueOf(regCounter);
    }

    public String newLabel() {
        labelCounter++;
        return "%l" +String.valueOf(labelCounter);
    }

    public void emit(String format, Object... args) {
        irText.append("\t").append(String.format(format, args)).append("\n");
    }

    public void emitRaw(String format, Object... args) {
        irText.append(String.format(format, args)).append("\n");
    }
}
