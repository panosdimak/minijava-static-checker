# Compilers-2

```
Panagiotis Dimakakos sdi2000268
```

## Usage

### Build

Requires Java 21+ (uses pattern matching for `switch`).  
First build requires `curl` and internet access (auto-fetches the `.jar`s and JavaCC grammar from the course site).

```bash
make
```

### Run
```bash
java Main <inputFile1> [<inputFile2> ...]
```

Offset table is printed to `stdout`, errors and the `=== <path> ===` headers to `stderr`.

### Clean
```bash
make clean
```

Removes everything JTB, JavaCC and javac generated.

```bash
make fetchclean
```

`make clean` + removes the fetched files.

## Structure

### Pipeline

```
STBuilder -> STValidator -> TypeChecker -> OffsetPrinter
```

### Project Layout

- `Main.java`: Entry point  
- `STBuilder.java`: Builds the symbol table by walking class, method and variable declarations (visitor)
- `symboltable` package:
  - `GlobalTable.java`: Top level symbol table class, maps class names to class symbols
  - `ClassSymbol.java`: Symbol table representation of a class, holds fields, methods and base offset sizes. Has reference to its parent class
  - `MethodSymbol.java`: Symbol table representation of a method, holds parameters, locals and return type. Has reference to its owning class
  - `VarSymbol.java`: Symbol table representation of a variable, holds its name and type
  - `Type.java`: Class representation of MiniJava types with assignability checks for each one
  - `SemanticError.java`: Custom exception for semantic errors
  - `STValidator.java`: Completes the symboltable construction by resolving typenames to `Type` objects, and checks method overloads and overrides
  - `State.java`: Tracks current class and current method context, used in `TypeChecker`
  - `TypeChecker.java`: The type-checking visitor
  - `OffsetPrinter.java`: Walks the symbol table and prints field and method offsets per the assignment spec
- `util` package:
  - `Util.java`: Contains shared AST helpers used in `STBuilder` and `TypeChecker`
