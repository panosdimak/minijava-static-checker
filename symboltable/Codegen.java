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
        argu.currentBlock = "%entry";

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

    @Override
    public String visit(IntegerLiteral n, State argu) throws Exception {
        return n.f0.tokenImage;
    }

    @Override
    public String visit(PrintStatement n, State argu) throws Exception {
        String expr = visit(n.f2, argu);
        argu.irText.append("call void @print_int(i32 ").append(expr).append(")\n");

        return null;
    }

    @Override
    public String visit(PlusExpression n, State argu) throws Exception {
        String a = visit(n.f0, argu);
        String b = visit(n.f2, argu);
        String dst = argu.newReg();

        argu.emit("%s = add i32 %s, %s", dst, a, b);

        return dst;
    }

    @Override
    public String visit(MinusExpression n, State argu) throws Exception {
        String a = visit(n.f0, argu);
        String b = visit(n.f2, argu);
        String dst = argu.newReg();

        argu.emit("%s = sub i32 %s, %s", dst, a, b);

        return dst;
    }

    @Override
    public String visit(TimesExpression n, State argu) throws Exception {
        String a = visit(n.f0, argu);
        String b = visit(n.f2, argu);
        String dst = argu.newReg();

        argu.emit("%s = mul i32 %s, %s", dst, a, b);

        return dst;
    }

    @Override
    public String visit(CompareExpression n, State argu) throws Exception {
        String a = visit(n.f0, argu);
        String b = visit(n.f2, argu);
        String dst = argu.newReg();

        argu.emit("%s = icmp slt i32 %s, %s", dst, a, b);

        return dst;
    }

    @Override
    public String visit(BracketExpression n, State argu) throws Exception {
        String dst = visit(n.f1, argu);

        return dst;
    }

    @Override
    public String visit(NotExpression n, State argu) throws Exception {
        String a = visit(n.f1, argu);
        String dst = argu.newReg();

        argu.emit("%s = xor i1 %s, true", dst, a);

        return dst;
    }

    @Override
    public String visit(AndExpression n, State argu) throws Exception {
        String a = visit(n.f0, argu);
        String aBlock = argu.currentBlock;

        String l1 = argu.newLabel();
        String l2 = argu.newLabel();

        argu.emit("br i1 %s, label %s, label %s", a, l1, l2);

        argu.emit("%s:", l1.substring(1));
        argu.currentBlock = l1;
        String b = visit(n.f2, argu);
        String bBlock = argu.currentBlock;
        argu.emit("br label %s", l2);

        argu.emit("%s:", l2.substring(1));
        argu.currentBlock = l2;
        String dst = argu.newReg();
        argu.emit("%s = phi i1 [ false, %s ], [ %s, %s ]", dst, aBlock, b, bBlock);

        return dst;
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
