package symboltable;

import java.util.List;

public class OffsetPrinter {

    public void printOffsets(GlobalTable globalTable) {
        for (ClassSymbol classSymbol : globalTable.classes.values()) {
            if (classSymbol.mainMethod != null) {
                continue;
            }

            System.out.println("-----------Class " + classSymbol.name + "-----------");
            System.out.println("--Variables---");

            int fieldCounter = (classSymbol.parent == null) ? 0 : classSymbol.parent.fieldBlockSize;

            for (VarSymbol field : classSymbol.fields.values()) {
                System.out.println(classSymbol.name + "." + field.name + " : " + fieldCounter);
                fieldCounter += field.type.byteSize();
            }
            classSymbol.fieldBlockSize = fieldCounter;

            System.out.println("---Methods---");


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
                    System.out.println(classSymbol.name + "." + method.name + " : " + vtableCounter);
                    vtableCounter += 8;
                }
            }
            classSymbol.vtableSize = vtableCounter;

            System.out.println();
        }
    }
}
