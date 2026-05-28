package symboltable;

import java.util.ArrayList;
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

	enum OverloadStatus {
		LEGAL,
		AMBIGUOUS,
		OVERRIDE;
	}

	OverloadStatus compareMethodPair(MethodSymbol a, MethodSymbol b) {
		if (a.paramList.size() != b.paramList.size()) {
			return OverloadStatus.LEGAL;
		}

		List<VarSymbol> aLocals = new ArrayList<>(a.locals.values());
		List<VarSymbol> bLocals = new ArrayList<>(b.locals.values());

		boolean allIdentical = true;
		for (int i = 0; i < a.paramList.size(); i++) {
			boolean aSubB = aLocals.get(i).type.isAssignableFrom(bLocals.get(i).type);
			boolean bSubA = bLocals.get(i).type.isAssignableFrom(aLocals.get(i).type);

			if (aSubB && bSubA) {
				continue;
			} else if (!aSubB && !bSubA) {
				return OverloadStatus.LEGAL;
			} else {
				allIdentical = false;
			}
		}

		if (allIdentical) {
			return OverloadStatus.OVERRIDE;
		} else {
			return OverloadStatus.AMBIGUOUS;
		}
	}

	public void checkOverloads(GlobalTable globalTable) throws SemanticError {
		for (ClassSymbol classSymbol : globalTable.classes.values()) {
			for (List<MethodSymbol> methodList : classSymbol.methods.values()) {
				for (int i = 0; i < methodList.size(); i++) {
					MethodSymbol M = methodList.get(i);
					for (int j = i + 1; j < methodList.size(); j++) {
						MethodSymbol N = methodList.get(j);
						OverloadStatus compRes = compareMethodPair(M, N);
						switch (compRes) {
							case LEGAL -> { continue; }
							case AMBIGUOUS -> throw new SemanticError(
											"ambiguous overload by method '"
											+ M.name + "' of class '"
											+ M.ownerClass.name + "'");
							case OVERRIDE -> throw new IllegalStateException();
						}
					}

					ClassSymbol c = M.ownerClass;
					while (c.parent != null) {
						c = c.parent;
						List<MethodSymbol> parentList = c.methods.get(M.name);
						if (parentList != null) {
							for (MethodSymbol parentMethod : parentList) {
								OverloadStatus compRes = compareMethodPair(M, parentMethod);
								switch (compRes) {
									case LEGAL -> { continue; }
									case AMBIGUOUS -> throw new SemanticError("ambiguous overload of method '"
													+ M.name
													+ "' between class '" + M.ownerClass.name
													+ "' and ancestor class '" + c.name + "'");
									case OVERRIDE -> {
										if (!M.returnType.isAssignableFrom(parentMethod.returnType)
											|| !parentMethod.returnType.isAssignableFrom(M.returnType)) {
												throw new SemanticError("invalid override of method '" + M.name
													+ "': mismatched return types '"
													+ M.returnTypeName + "' (class '" + M.ownerClass.name + "') vs '"
													+ parentMethod.returnTypeName + "' (class '" + parentMethod.ownerClass.name + "')");
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}
}
