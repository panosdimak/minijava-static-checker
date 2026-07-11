package symboltable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import syntaxtree.*;
import visitor.GJDepthFirst;

public class Codegen extends GJDepthFirst<String, State> {

    private final GlobalTable globalTable;

    public Codegen(GlobalTable globalTable) {
        this.globalTable = globalTable;
    }

    public static final String helpers =
    """
    declare i8* @calloc(i32, i32)
    declare i32 @printf(i8*, ...)
    declare void @exit(i32)

    @_cint = constant [4 x i8] c"%d\\0a\\00"
    @_cOOB = constant [15 x i8] c"Out of bounds\\0a\\00"
    define void @print_int(i32 %i) {
        %_str = bitcast [4 x i8]* @_cint to i8*
        call i32 (i8*, ...) @printf(i8* %_str, i32 %i)
        ret void
    }

    define void @throw_oob() {
        %_str = bitcast [15 x i8]* @_cOOB to i8*
        call i32 (i8*, ...) @printf(i8* %_str)
        call void @exit(i32 1)
        ret void
    }

    """;

    @Override
    public String visit(MainClass n, State argu) throws Exception {
        ClassSymbol savedClass = argu.currentClass;
        MethodSymbol savedMethod = argu.currentMethod;

        argu.irText.append("define i32 @main() {\nentry:\n");

        String mainClassName = n.f1.f0.tokenImage;
        ClassSymbol mainClass = globalTable.lookupClass(mainClassName);
        argu.currentClass = mainClass;
        argu.currentMethod = mainClass.mainMethod;

        super.visit(n, argu);

        argu.irText.append("\tret i32 0\n}\n");

        argu.currentClass = savedClass;
        argu.currentMethod = savedMethod;

        return null;
    }

    public void generate(Goal root, String inputFileName, State state) throws Exception {
        state.irText = new StringBuilder();
        state.irText.append(helpers);

        root.accept(this, state);

        String outputFileName = inputFileName.substring(0, inputFileName.indexOf(".java"));
        outputFileName += ".ll";

        Path filePath = Paths.get(outputFileName);

        try {
            Files.writeString(filePath, state.irText);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
