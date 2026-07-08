# MiniJava Static Checker

A static semantic analyzer for **MiniJava**. It parses one or more `.java` files and type checks them. Valid programs are accepted and their field/method **offset table** is printed; invalid programs are rejected with a specific error message.

Built on top of a JavaCC parser and a JTB-generated visitor AST.

## What it checks

The checker runs a semantic pass over MiniJava that covers:

- **Declarations.** Globally unique class names, per-class field uniqueness
  (with parent-field shadowing), per-method local/parameter uniqueness, and
  the source-order `extends` rule that makes inheritance cycles impossible.
- **Types.** Resolves every type name (`int`, `boolean`, `int[]`, declared
  classes) and enforces the subtype relation (reflexive, transitive over
  `extends`, primitives unrelated to anything but themselves).
- **Overloading and overriding.** Classifies every same-name method pair across
  a class and its ancestor chain as a legal overload, an ambiguous overload
  (rejected), or an override (with return-type identity enforced).
- **Statements and expressions.** Type checks assignments, array operations,
  conditionals, arithmetic, boolean and comparison operators, method calls
  (with overload resolution against the receiver's class and ancestors),
  allocation, `this`, and `return`.
- **Offsets.** For every non-`main`, non-inherited field and method, computes
  its memory offset. Fields are packed by size (`int` 4, `boolean` 1, pointer 8,
  no alignment) starting at the parent's field-block size; methods are placed
  in vtable order, skipping overrides.

## Build

Requires **Java 21+** (uses pattern matching for `switch`). The build is
self-contained: the JTB and JavaCC tools and the grammar are vendored in the
repo.

```bash
make
```

`make clean` removes everything JTB, JavaCC and `javac` generated.

## Usage

```bash
java Main <inputFile1> [<inputFile2> ...]
```

The offset table is printed to **stdout**; errors and the `=== <path> ===`
headers go to **stderr**.

### Accepted program

```java
class Demo {
    public static void main(String[] a) {
        System.out.println(new Tree().init());
    }
}

class Tree {
    int value;
    Tree left;
    public int init() { value = 42; return value; }
    public int size() { return 1; }
}

class Leaf extends Tree {
    boolean marked;
    public int size() { return 1; }
}
```

```
$ java Main Demo.java
-----------Class Tree-----------
--Variables---
Tree.value : 0
Tree.left : 4
---Methods---
Tree.init : 0
Tree.size : 8

-----------Class Leaf-----------
--Variables---
Leaf.marked : 12
---Methods---
```

`Leaf.marked` continues from `Tree`'s field block (`int` 4 + pointer 8 = 12),
and `Leaf.size` is correctly omitted as an override of `Tree.size`.

### Rejected program

```java
class Checker {
    public int run() {
        boolean b;
        b = 3 < true;   // '<' needs two ints
        return 0;
    }
}
```

```
$ java Main Bad.java
operands of < expression must be of int type, got 'int' and 'boolean'
```

## Architecture

The checker runs three passes and a printer over the JTB AST:

```
STBuilder -> STValidator -> TypeChecker -> OffsetPrinter
```

1. **STBuilder** walks the AST and populates the symbol table.
2. **STValidator** resolves type names to `Type` objects and classifies
   method overloads/overrides (needs the full ancestor chain, so it is a
   separate pass).
3. **TypeChecker** walks method bodies and type-checks every statement and
   expression.
4. **OffsetPrinter** walks the symbol table and emits the offset table.

### Project layout

- `Main.java`: entry point; drives the four phases per input file.
- `STBuilder.java`: builds the symbol table (JTB `DepthFirstVisitor`).
- `symboltable/` package:
  - `GlobalTable.java`: top-level table mapping class names to class symbols.
  - `ClassSymbol.java`: a class (fields, methods, parent reference, offsets).
  - `MethodSymbol.java`: a method (parameters, locals, return type, owner).
  - `VarSymbol.java`: a variable (field, local, or parameter).
  - `Type.java`: MiniJava types with assignability and byte-size logic.
  - `SemanticError.java`: the single checked exception used across passes.
  - `STValidator.java`: type-name resolution and overload/override checking.
  - `State.java`: current class/method context for the `TypeChecker`.
  - `TypeChecker.java`: the type-checking visitor (JTB `GJDepthFirst`).
  - `OffsetPrinter.java`: computes and prints field/method offsets.
- `util/Util.java`: shared AST helpers used by `STBuilder` and `TypeChecker`.

## Credits & third-party components

- **MiniJava** is a teaching subset of Java from Andrew Appel's
  *Modern Compiler Implementation in Java*.
- **JTB** (Java Tree Builder), from the UCLA Compilers Group, is licensed
  BSD-3-Clause. The bundled build carries DIT@UoA patches (see the license
  inside `lib/jtb133di.jar`).
- **JavaCC** is also licensed BSD-3-Clause.

The MIT license below covers the checker source written for this project. The
vendored tools under `lib/` and the grammar keep their own respective terms.

## License

[MIT](LICENSE)
