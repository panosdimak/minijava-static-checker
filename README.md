# MiniJava Compiler

An end-to-end compiler for **MiniJava**. The front end does a full semantic
analysis: classes and single inheritance, method overloading
and overriding, and static type checking. The back end lowers every accepted
program to **LLVM IR** with real object layouts, virtual method dispatch, and
bounds-checked arrays.

Compiles one or more `.java` files at a time, emitting textual
LLVM IR that [`clang`](https://clang.llvm.org/) turns into a binary. Ill-typed
programs are rejected with a specific error message and produce no output.

Built on a JavaCC parser and a JTB-generated visitor AST: a static-analysis
front end (three semantic passes) feeds an LLVM code-generation back end.

## What it does

### Front end: static analysis

Only well-typed programs reach code generation. The semantic passes cover:

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

### Back end: LLVM code generation

Every accepted program is lowered to textual LLVM IR:

- **Object layout.** Each object is a vtable pointer (8 bytes) followed by its
  fields, packed by size with no alignment, continuing from the parent's field
  block. Field and method **offsets** are computed once and reused by codegen.
- **Objects and dispatch.** `new C()` is a zeroing `calloc` (Java default field
  values for free) with the class's vtable installed in the header. Method
  calls load the function pointer from the vtable slot and call through it, so
  dispatch is virtual and overrides resolve at run time.
- **Overloading.** Same-name methods that differ by signature get distinct,
  unambiguous LLVM symbols, so legal MiniJava overloads compile cleanly.
- **Arrays.** `new int[n]` stores the length in a header slot; every lookup and
  store is bounds-checked and traps out-of-range accesses via a runtime helper.
- **Control flow.** `if`/`while` and short-circuit `&&` lower to explicit basic
  blocks with `phi` nodes; locals live in memory (`alloca`/`load`/`store`).

The emitted IR uses opaque pointers (`ptr`) and declares no target triple, so
`clang` compiles it for whatever host it runs on.

## Build

Requires **Java 21+** (uses pattern matching for `switch`) to build the
compiler, and **`clang`** to compile the emitted IR (verified with LLVM 21). The
build is self-contained: the JTB and JavaCC tools and the grammar are vendored
in the repo.

```bash
make
```

`make clean` removes everything JTB, JavaCC and `javac` generated.

## Usage

```bash
java Main <inputFile1> [<inputFile2> ...]
```

For each `<file>.java` the compiler writes `<file>.ll`. The field/method offset
table is printed to **stdout**; errors and the `=== <path> ===` headers go to
**stderr**. Compile and run the IR with `clang`:

```bash
clang -o out file.ll
./out
```

### Example

```java
class Fac {
    public static void main(String[] a) {
        System.out.println(new Calc().fac(5));
    }
}

class Calc {
    public int fac(int n) {
        int r;
        if (n < 1) r = 1;
        else r = n * (this.fac(n - 1));
        return r;
    }
}
```

```bash
$ java Main Fac.java     # emits Fac.ll (and prints the offset table)
$ clang -o out Fac.ll
$ ./out
120
```

The generated `Fac.ll` (boilerplate omitted) shows the vtable, the
`calloc`-allocated object, and virtual dispatch:

```llvm
@.Calc_vtable = global [ 1 x ptr ] [ ptr @Calc.fac ]

define i32 @main() {
entry:
	%_1 = call i8* @calloc(i32 1, i32 8)          ; new Calc()
	%_2 = getelementptr [1 x ptr], ptr @.Calc_vtable, i32 0, i32 0
	store ptr %_2, ptr %_1                         ; install vtable
	%_3 = load ptr, ptr %_1
	%_4 = getelementptr ptr, ptr %_3, i32 0        ; vtable slot 0
	%_5 = load ptr, ptr %_4
	%_6 = call i32 %_5(i8* %_1, i32 5)             ; this.fac(5)
	call void @print_int(i32 %_6)
	ret i32 0
}

define i32 @Calc.fac(i8* %this, i32 %.n) {
entry:
	%n = alloca i32
	store i32 %.n, ptr %n
	%r = alloca i32
	%_7 = load i32, ptr %n
	%_8 = icmp slt i32 %_7, 1
	br i1 %_8, label %l1, label %l2
	; ... then/else blocks, recursive call, ret ...
}
```

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

The compiler runs three semantic passes, an offset-computation pass, and the
code generator over the JTB AST:

```
STBuilder -> STValidator -> TypeChecker -> OffsetPrinter (compute) -> Codegen
```

1. **STBuilder** walks the AST and populates the symbol table.
2. **STValidator** resolves type names to `Type` objects and classifies
   method overloads/overrides (needs the full ancestor chain, so it is a
   separate pass).
3. **TypeChecker** walks method bodies and type-checks every statement and
   expression, recording each call's resolved method for codegen.
4. **OffsetPrinter** computes field and method offsets, annotating the symbol
   table (and prints the human-readable offset table).
5. **Codegen** walks the type-checked AST and emits LLVM IR, reading the
   offsets computed above as data.

### Project layout

- `Main.java`: entry point; drives the phases per input file.
- `STBuilder.java`: builds the symbol table (JTB `DepthFirstVisitor`).
- `symboltable/` package:
  - `GlobalTable.java`: top-level table mapping class names to class symbols.
  - `ClassSymbol.java`: a class (fields, methods, parent reference, offsets,
    vtable layout).
  - `MethodSymbol.java`: a method (parameters, locals, return type, owner,
    vtable slot).
  - `VarSymbol.java`: a variable (field, local, or parameter) with its offset.
  - `Type.java`: MiniJava types with assignability and byte-size logic.
  - `SemanticError.java`: the single checked exception used across passes.
  - `STValidator.java`: type-name resolution and overload/override checking.
  - `State.java`: per-file context (current class/method, register/label
    counters, and the IR buffer) shared by `TypeChecker` and `Codegen`.
  - `TypeChecker.java`: the type-checking visitor (JTB `GJDepthFirst`).
  - `OffsetPrinter.java`: computes field/method offsets and prints the table.
  - `Codegen.java`: the LLVM IR back end (JTB `GJDepthFirst`).
- `util/Util.java`: shared AST helpers.

## Credits & third-party components

- **MiniJava** is a teaching subset of Java from Andrew Appel's
  *Modern Compiler Implementation in Java*.
- **JTB** (Java Tree Builder), from the UCLA Compilers Group, is licensed
  BSD-3-Clause. The bundled build carries DIT@UoA patches (see the license
  inside `lib/jtb133di.jar`).
- **JavaCC** is also licensed BSD-3-Clause.

The MIT license below covers the compiler source written for this project. The
vendored tools under `lib/` and the grammar keep their own respective terms.

## License

[MIT](LICENSE)
