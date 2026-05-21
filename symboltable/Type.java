package symboltable;

import symboltable.ClassSymbol;

abstract class Type {
	String name;
	abstract boolean isAssignableFrom(Type other);
}

class IntType extends Type {
	boolean isAssignableFrom(Type other) {
		return this == other;
	}
}

class BooleanType extends Type {
	boolean isAssignableFrom(Type other) {
		return this == other;
	}
}

class IntArrayType extends Type {
	boolean isAssignableFrom(Type other) {
		return this == other;
	}
}

class ClassType extends Type {
	ClassSymbol classSymbol;

	boolean isAssignableFrom(Type other) {
		if (!(other instanceof ClassType ct)) return false;

		ClassSymbol target = ct.classSymbol;
		while (target != null) {
			if (this.classSymbol == target) return true;
			target = target.parent;
		}
		return false;
	}
}
