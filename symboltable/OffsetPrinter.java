package symboltable;

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

            for (MethodSymbol method : classSymbol.methodsInOrder) {
                ClassSymbol c = method.ownerClass.parent;
                boolean isOverride = false;

                while (c != null && !isOverride) {
                    List<MethodSymbol> pMethods = c.methods.get(method.name);
                    if (pMethods != null) {
                        for (MethodSymbol pMethod : pMethods) {
                            if (pMethod.paramList.equals(method.paramList)) {
                                isOverride = true;
                                break;
                            }
                        }
                    }

                    c = c.parent;
                }

                if (!isOverride) {
                    method.byteOffset = vtableCounter;
                    vtableCounter += 8;
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
