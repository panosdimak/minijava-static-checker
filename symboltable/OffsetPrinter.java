package symboltable;

import java.util.ArrayList;
import java.util.List;

public class OffsetPrinter {

    public void compute(GlobalTable globalTable) {
        for (ClassSymbol classSymbol : globalTable.classes.values()) {
            if (classSymbol.mainMethod != null) {
                continue;
            }

            int fieldCounter = (classSymbol.parent == null) ? 0 : classSymbol.parent.fieldBlockSize;

            for (VarSymbol field : classSymbol.fields.values()) {
                field.byteOffset = fieldCounter;
                fieldCounter += field.type.byteSize();
            }
            classSymbol.fieldBlockSize = fieldCounter;

            int vtableCounter = (classSymbol.parent == null) ? 0 : classSymbol.parent.vtableSize;

            if (classSymbol.parent != null) {
                classSymbol.vtableList = new ArrayList<>(classSymbol.parent.vtableList);
            }

            for (MethodSymbol method : classSymbol.methodsInOrder) {
                int matchIndex = -1;

                for (int i = 0; i < classSymbol.vtableList.size(); i++) {
                    MethodSymbol vMethod = classSymbol.vtableList.get(i);
                    if (vMethod.name.equals(method.name) && vMethod.paramList.equals(method.paramList)) {
                        matchIndex = i;
                        break;
                    }
                }

                if (matchIndex >= 0) {
                    classSymbol.vtableList.set(matchIndex, method);
                } else {
                    method.byteOffset = vtableCounter;
                    vtableCounter += 8;
                    classSymbol.vtableList.add(method);
                }
            }
            classSymbol.vtableSize = vtableCounter;
        }
    }

    public void print(GlobalTable globalTable) {
        for (ClassSymbol classSymbol : globalTable.classes.values()) {
            if (classSymbol.mainMethod != null) {
                continue;
            }

            System.out.println("-----------Class " + classSymbol.name + "-----------");
            System.out.println("--Variables---");

            for (VarSymbol field : classSymbol.fields.values()) {
                System.out.println(classSymbol.name + "." + field.name + " : " + field.byteOffset);
            }

            System.out.println("---Methods---");

            for (MethodSymbol method : classSymbol.methodsInOrder) {
                if (method.byteOffset < 0) {
                    continue;
                }

                System.out.println(classSymbol.name + "." + method.name + " : " + method.byteOffset);
            }

            System.out.println();
        }
    }
}
