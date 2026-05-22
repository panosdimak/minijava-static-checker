package symboltable;

public abstract class Type {
	public final String name;

	Type(String n) {
		this.name = n;
	}

	public abstract boolean isAssignableFrom(Type other);
}

class IntType extends Type {
	IntType() {
		super("int");
	}

	public boolean isAssignableFrom(Type other) {
		return (other instanceof IntType);
	}
}

class BooleanType extends Type {
	BooleanType() {
		super("boolean");
	}

	public boolean isAssignableFrom(Type other) {
		return (other instanceof BooleanType);
	}
}

class IntArrayType extends Type {
	IntArrayType() {
		super("int[]");
	}

	public boolean isAssignableFrom(Type other) {
		return (other instanceof IntArrayType);
	}
}

class ClassType extends Type {
	final ClassSymbol classSymbol;

	ClassType(ClassSymbol c) {
		super(c.name);
		this.classSymbol = c;
	}

	public boolean isAssignableFrom(Type other) {
		if (!(other instanceof ClassType ct)) return false;

		ClassSymbol target = ct.classSymbol;
		while (target != null) {
			if (this.classSymbol == target) return true;
			target = target.parent;
		}
		return false;
	}
}
