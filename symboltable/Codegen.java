package symboltable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import syntaxtree.*;
import util.Util;
import visitor.GJDepthFirst;

public class Codegen extends GJDepthFirst<String, State> {

    private final GlobalTable globalTable;

    public Codegen(GlobalTable globalTable) {
        this.globalTable = globalTable;
    }

    private record Pointer (Type type, String reg) {}

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

        argu.emit("define i32 @main() {\nentry:");
        argu.currentBlock = "%entry";

        String mainClassName = n.f1.f0.tokenImage;
        ClassSymbol mainClass = globalTable.lookupClass(mainClassName);
        argu.currentClass = mainClass;
        argu.currentMethod = mainClass.mainMethod;

        for (VarSymbol local : argu.currentMethod.locals.values()) {
            argu.emit("%s = alloca %s", "%" + local.name, convertType(local.type));
        }

        super.visit(n, argu);

        argu.emit("ret i32 0\n}");

        argu.currentClass = savedClass;
        argu.currentMethod = savedMethod;

        return null;
    }

    @Override
    public String visit(ClassDeclaration n, State argu) throws Exception {
        ClassSymbol savedClass = argu.currentClass;

        String vClassName = n.f1.f0.tokenImage;
        ClassSymbol vClass = globalTable.lookupClass(vClassName);
        argu.currentClass = vClass;
        super.visit(n, argu);

        argu.currentClass = savedClass;

        return null;
    }

    @Override
    public String visit(ClassExtendsDeclaration n, State argu) throws Exception {
        ClassSymbol savedClass = argu.currentClass;

        String vClassName = n.f1.f0.tokenImage;
        ClassSymbol vClass = globalTable.lookupClass(vClassName);
        argu.currentClass = vClass;

        super.visit(n, argu);

        argu.currentClass = savedClass;

        return null;
    }

    @Override
    public String visit(MethodDeclaration n, State argu) throws Exception {
        MethodSymbol savedMethod = argu.currentMethod;
        String vmethodName = n.f2.f0.tokenImage;
        List<VarSymbol> vparams = Util.nodeoptToVarSymbolList(n.f4);

        List<String> vParamStrings = new ArrayList<>();
        for (VarSymbol v : vparams) {
            vParamStrings.add(v.typeName);
        }

        MethodSymbol found = null;
        for (MethodSymbol vmethod : argu.currentClass.methods.get(vmethodName)) {
            if (vmethod.paramList.equals(vParamStrings)) {
                found = vmethod;
                break;
            }
        }
        if (found == null) {
            throw new IllegalStateException("no MethodSymbol for " + vmethodName +
                                            " in " + argu.currentClass.name);
        }
        argu.currentMethod = found;

        List<VarSymbol> locals = new ArrayList<>(argu.currentMethod.locals.values());

        int paramsEndIndex = argu.currentMethod.paramList.size();
        String llvmParams = "i8* %this";
        List<String> paramList = new ArrayList<>();
        for (VarSymbol param : locals.subList(0, paramsEndIndex)) {
            paramList.add(convertType(param.type) + " %." + param.name);
        }
        if (!paramList.isEmpty()) {
            llvmParams += ", " + String.join(", ", paramList);
        }

        argu.emit("define %s @%s.%s(%s) {\nentry:",
                convertType(argu.currentMethod.returnType),
                argu.currentClass.name, argu.currentMethod.name,
                llvmParams);

        argu.currentBlock = "%entry";

        for (VarSymbol param : locals.subList(0, paramsEndIndex)) {
            argu.emit("%s = alloca %s", "%" + param.name, convertType(param.type));
            argu.emit("store %s %s, ptr %s", convertType(param.type), "%." + param.name, "%" + param.name);
        }

        for (VarSymbol local : locals.subList(paramsEndIndex, locals.size())) {
            argu.emit("%s = alloca %s", "%" + local.name, convertType(local.type));
        }

        visit(n.f8, argu);

        String retOp = visit(n.f10, argu);
        argu.emit("ret %s %s", convertType(argu.currentMethod.returnType), retOp);
        argu.emit("}");

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
        argu.emit("call void @print_int(i32 %s)", expr);

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
        return visit(n.f1, argu);
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

    @Override
    public String visit(ThisExpression n, State argu) {
        return "%this";
    }

    @Override
    public String visit(AssignmentStatement n, State argu) throws Exception {
        Pointer pointer = resolveIdentifier(n.f0, argu);
        String rhs = visit(n.f2, argu);
        argu.emit("store %s %s, ptr %s", convertType(pointer.type), rhs, pointer.reg);

        return null;
    }

    @Override
    public String visit(PrimaryExpression n, State argu) throws Exception {
        return switch (n.f0.choice) {
            case Identifier id -> {
                Pointer pointer = resolveIdentifier(id, argu);
                String dst = argu.newReg();
                argu.emit("%s = load %s, ptr %s", dst, convertType(pointer.type), pointer.reg);
                yield dst;
            }
            default -> n.f0.choice.accept(this, argu);
        };
    }

    @Override
    public String visit(IfStatement n, State argu) throws Exception {
        String expr = visit(n.f2, argu);

        String ifBlock = argu.newLabel();
        String elseBlock = argu.newLabel();
        String nextBlock = argu.newLabel();

        argu.emit("br i1 %s, label %s, label %s", expr, ifBlock, elseBlock);

        argu.emit("%s:", ifBlock.substring(1));
        argu.currentBlock = ifBlock;
        visit(n.f4, argu);
        argu.emit("br label %s", nextBlock);

        argu.emit("%s:", elseBlock.substring(1));
        argu.currentBlock = elseBlock;
        visit(n.f6, argu);
        argu.emit("br label %s", nextBlock);

        argu.emit("%s:", nextBlock.substring(1));
        argu.currentBlock = nextBlock;

        return null;
    }

    @Override
    public String visit(WhileStatement n, State argu) throws Exception {
        String condBlock = argu.newLabel();
        String bodyBlock = argu.newLabel();
        String nextBlock = argu.newLabel();

        argu.emit("br label %s", condBlock);

        argu.emit("%s:", condBlock.substring(1));
        argu.currentBlock = condBlock;
        String expr = visit(n.f2, argu);
        argu.emit("br i1 %s, label %s, label %s", expr, bodyBlock, nextBlock);

        argu.emit("%s:", bodyBlock.substring(1));
        argu.currentBlock = bodyBlock;
        visit(n.f4, argu);
        argu.emit("br label %s", condBlock);

        argu.emit("%s:", nextBlock.substring(1));
        argu.currentBlock = nextBlock;

        return null;
    }

    private Pointer resolveIdentifier(Identifier id, State state) throws Exception {
        VarSymbol vs = state.currentMethod.locals.get(id.f0.tokenImage);
        if (vs != null) {
            return new Pointer(vs.type, "%" + vs.name);
        }

        for (ClassSymbol c = state.currentClass; c != null && vs == null; c = c.parent) {
            vs = c.fields.get(id.f0.tokenImage);
        }

        if (vs == null) {
            throw new IllegalStateException("undeclared identifier '" + id.f0.tokenImage + "'");
        }

        String dst = state.newReg();
        state.emit("%s = getelementptr i8, ptr %%this, i32 %s", dst, 8 + vs.byteOffset);

        return new Pointer(vs.type, dst);
    }

    private String convertType(Type type) {
        return switch (type) {
            case IntType i -> "i32";
            case BooleanType b -> "i1";
            default -> "i8*";
        };
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
