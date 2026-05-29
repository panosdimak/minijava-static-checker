package util;

import java.util.ArrayList;
import java.util.List;
import symboltable.VarSymbol;
import syntaxtree.*;

public class Util {

    public static String typeToString(Type t) {
        return switch (t.f0.choice) {
            case ArrayType ar -> "int[]";
            case BooleanType b -> "boolean";
            case IntegerType in -> "int";
            case Identifier i -> i.f0.tokenImage;
            default -> throw new IllegalStateException(
                "unexpected Type variant " + t.f0.choice.getClass().getName()
            );
        };
    }

    public static VarSymbol fpToVarSymbol(FormalParameter fp) {
        return new VarSymbol(fp.f1.f0.tokenImage, typeToString(fp.f0));
    }

    public static List<VarSymbol> nodeoptToVarSymbolList(NodeOptional nodeopt) {
        List<VarSymbol> list = new ArrayList<>();

        if (nodeopt.present()) {
            FormalParameterList fplist = ((FormalParameterList) nodeopt.node);

            list.add(fpToVarSymbol(fplist.f0));
            for (Node node : fplist.f1.f0.nodes) {
                FormalParameterTerm fpterm = ((FormalParameterTerm) node);
                list.add(fpToVarSymbol(fpterm.f1));
            }
        }
        return list;
    }
}
