package symboltable;

import java.util.List;

public class STValidator {

	public void resolveTypes(GlobalTable globalTable) throws SemanticError {
		for (ClassSymbol classSymbol : globalTable.classes.values()) {
			for (VarSymbol field : classSymbol.fields.values()) {
				field.type = globalTable.resolveType(field.typeName);
			}

			if (classSymbol.mainMethod != null) {
				for (VarSymbol local : classSymbol.mainMethod.locals.values()) {
					local.type = globalTable.resolveType(local.typeName);
				}
			}

			for (List<MethodSymbol> methodList : classSymbol.methods.values()) {
				for (MethodSymbol method : methodList) {
					method.returnType = globalTable.resolveType(method.returnTypeName);

					for (VarSymbol local : method.locals.values()) {
						local.type = globalTable.resolveType(local.typeName);
					}
				}
			}
		}
	}
}
