package symboltable;

public abstract class Type {
    public final String name;

    Type(String n) {
        this.name = n;
    }

    public abstract boolean isAssignableFrom(Type other);

    public abstract int byteSize();
}

class IntType extends Type {
    IntType() {
        super("int");
    }

    public boolean isAssignableFrom(Type other) {
        return (other instanceof IntType);
    }

    public int byteSize() {
        return 4;
    }
}

class BooleanType extends Type {
    BooleanType() {
        super("boolean");
    }

    public boolean isAssignableFrom(Type other) {
        return (other instanceof BooleanType);
    }

    public int byteSize() {
        return 1;
    }
}

class IntArrayType extends Type {
    IntArrayType() {
        super("int[]");
    }

    public boolean isAssignableFrom(Type other) {
        return (other instanceof IntArrayType);
    }

    public int byteSize() {
        return 8;
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

    public int byteSize() {
        return 8;
    }
}
