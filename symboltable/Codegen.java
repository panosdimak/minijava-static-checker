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

        argu.emitRaw("");
        argu.emitRaw("define i32 @main() {");
        argu.emitRaw("entry:");
        argu.currentBlock = "%entry";

        String mainClassName = n.f1.f0.tokenImage;
        ClassSymbol mainClass = globalTable.lookupClass(mainClassName);
        argu.currentClass = mainClass;
        argu.currentMethod = mainClass.mainMethod;

        for (VarSymbol local : argu.currentMethod.locals.values()) {
            argu.emit("%s = alloca %s", "%" + local.name, convertType(local.type));
        }

        super.visit(n, argu);

        argu.emit("ret i32 0");
        argu.emitRaw("}");

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

        argu.emitRaw("");
        argu.emitRaw("define %s @%s.%s(%s) {",
                convertType(argu.currentMethod.returnType),
                argu.currentClass.name, argu.currentMethod.name,
                llvmParams);
        argu.emitRaw("entry:");

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
        argu.emitRaw("}");

        argu.currentMethod = savedMethod;

        return null;
    }

    @Override
    public String visit(IntegerLiteral n, State argu) throws Exception {
        return n.f0.tokenImage;
    }

    @Override
    public String visit(TrueLiteral n, State argu) throws Exception {
        return "true";
    }

    @Override
    public String visit(FalseLiteral n, State argu) throws Exception {
        return "false";
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

        argu.emitRaw("%s:", l1.substring(1));
        argu.currentBlock = l1;
        String b = visit(n.f2, argu);
        String bBlock = argu.currentBlock;
        argu.emit("br label %s", l2);

        argu.emitRaw("%s:", l2.substring(1));
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
    public String visit(AllocationExpression n, State argu) {
        String dst = argu.newReg();
        ClassSymbol classSymbol = globalTable.lookupClass(n.f1.f0.tokenImage);

        argu.emit("%s = call i8* @calloc(i32 1, i32 %s)", dst, 8 + classSymbol.fieldBlockSize);

        String vtable = argu.newReg();
        argu.emit("%s = getelementptr [%s x ptr], ptr @.%s_vtable, i32 0, i32 0",
            vtable,
            classSymbol.vtableSize / 8,
            classSymbol.name);

        argu.emit("store ptr %s, ptr %s", vtable, dst);

        return dst;
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
    public String visit(ArrayAllocationExpression n, State argu) throws Exception {
        String sizeReg = visit(n.f3, argu);

        String guardreg = argu.newReg();
        argu.emit("%s = icmp slt i32 %s, 0", guardreg, sizeReg);

        String throwBlock = argu.newLabel();
        String arrayBlock = argu.newLabel();
        argu.emit("br i1 %s, label %s, label %s", guardreg, throwBlock, arrayBlock);

        argu.emitRaw("%s:", throwBlock.substring(1));
        argu.currentBlock = throwBlock;
        argu.emit("call void @throw_oob()");
        argu.emit("unreachable");

        argu.emitRaw("%s:", arrayBlock.substring(1));
        argu.currentBlock = arrayBlock;

        String newSizeReg = argu.newReg();
        argu.emit("%s = add i32 %s, 1", newSizeReg, sizeReg);
        String allocReg = argu.newReg();
        argu.emit("%s = call i8* @calloc(i32 4, i32 %s)", allocReg, newSizeReg);
        argu.emit("store i32 %s, ptr %s", sizeReg, allocReg);

        return allocReg;
    }

    @Override
    public String visit(ArrayLength n, State argu) throws Exception {
        String arrayReg = visit(n.f0, argu);
        String lengthReg = argu.newReg();
        argu.emit("%s = load i32, ptr %s", lengthReg, arrayReg);

        return lengthReg;
    }

    @Override
    public String visit(ArrayLookup n, State argu) throws Exception {
        String arrayReg = visit(n.f0, argu);
        String idxReg = visit(n.f2, argu);
        String lengthReg = argu.newReg();
        argu.emit("%s = load i32, ptr %s", lengthReg, arrayReg);

        String boundscheckReg = argu.newReg();
        argu.emit("%s = icmp ult i32 %s, %s", boundscheckReg, idxReg, lengthReg);

        String inBoundsBlock = argu.newLabel();
        String oobBlock = argu.newLabel();
        argu.emit("br i1 %s, label %s, label %s", boundscheckReg, inBoundsBlock, oobBlock);

        argu.emitRaw("%s:", oobBlock.substring(1));
        argu.currentBlock = oobBlock;
        argu.emit("call void @throw_oob()");
        argu.emit("unreachable");

        argu.emitRaw("%s:", inBoundsBlock.substring(1));
        argu.currentBlock = inBoundsBlock;

        String newIdxReg = argu.newReg();
        argu.emit("%s = add i32 %s, 1", newIdxReg, idxReg);

        String ptrReg = argu.newReg();
        argu.emit("%s = getelementptr i32, ptr %s, i32 %s", ptrReg, arrayReg, newIdxReg);
        String loadReg = argu.newReg();
        argu.emit("%s = load i32, ptr %s", loadReg, ptrReg);

        return loadReg;
    }

    @Override
    public String visit(ArrayAssignmentStatement n, State argu) throws Exception {
        String id = resolveIdentifier(n.f0, argu).reg;
        String arrReg = argu.newReg();
        argu.emit("%s = load ptr, ptr %s", arrReg, id);
        String idxReg = visit(n.f2, argu);
        String rhs = visit(n.f5, argu);

        String lengthReg = argu.newReg();
        argu.emit("%s = load i32, ptr %s", lengthReg, arrReg);

        String boundscheckReg = argu.newReg();
        argu.emit("%s = icmp ult i32 %s, %s", boundscheckReg, idxReg, lengthReg);

        String inBoundsBlock = argu.newLabel();
        String oobBlock = argu.newLabel();
        argu.emit("br i1 %s, label %s, label %s", boundscheckReg, inBoundsBlock, oobBlock);

        argu.emitRaw("%s:", oobBlock.substring(1));
        argu.currentBlock = oobBlock;
        argu.emit("call void @throw_oob()");
        argu.emit("unreachable");

        argu.emitRaw("%s:", inBoundsBlock.substring(1));
        argu.currentBlock = inBoundsBlock;

        String newIdxReg = argu.newReg();
        argu.emit("%s = add i32 %s, 1", newIdxReg, idxReg);

        String ptrReg = argu.newReg();
        argu.emit("%s = getelementptr i32, ptr %s, i32 %s", ptrReg, arrReg, newIdxReg);
        argu.emit("store i32 %s, ptr %s", rhs, ptrReg);

        return null;
    }

    @Override
    public String visit(IfStatement n, State argu) throws Exception {
        String expr = visit(n.f2, argu);

        String ifBlock = argu.newLabel();
        String elseBlock = argu.newLabel();
        String nextBlock = argu.newLabel();

        argu.emit("br i1 %s, label %s, label %s", expr, ifBlock, elseBlock);

        argu.emitRaw("%s:", ifBlock.substring(1));
        argu.currentBlock = ifBlock;
        visit(n.f4, argu);
        argu.emit("br label %s", nextBlock);

        argu.emitRaw("%s:", elseBlock.substring(1));
        argu.currentBlock = elseBlock;
        visit(n.f6, argu);
        argu.emit("br label %s", nextBlock);

        argu.emitRaw("%s:", nextBlock.substring(1));
        argu.currentBlock = nextBlock;

        return null;
    }

    @Override
    public String visit(WhileStatement n, State argu) throws Exception {
        String condBlock = argu.newLabel();
        String bodyBlock = argu.newLabel();
        String nextBlock = argu.newLabel();

        argu.emit("br label %s", condBlock);

        argu.emitRaw("%s:", condBlock.substring(1));
        argu.currentBlock = condBlock;
        String expr = visit(n.f2, argu);
        argu.emit("br i1 %s, label %s, label %s", expr, bodyBlock, nextBlock);

        argu.emitRaw("%s:", bodyBlock.substring(1));
        argu.currentBlock = bodyBlock;
        visit(n.f4, argu);
        argu.emit("br label %s", condBlock);

        argu.emitRaw("%s:", nextBlock.substring(1));
        argu.currentBlock = nextBlock;

        return null;
    }

    @Override
    public String visit(MessageSend n, State argu) throws Exception {
        MethodSymbol method = argu.resolvedCalls.get(n);
        String objReg = visit(n.f0, argu);

        String vtReg = argu.newReg();
        argu.emit("%s = load ptr, ptr %s", vtReg, objReg);

        String slotReg = argu.newReg();
        argu.emit("%s = getelementptr ptr, ptr %s, i32 %s", slotReg, vtReg, method.slot);

        String fpReg = argu.newReg();
        argu.emit("%s = load ptr, ptr %s", fpReg, slotReg);

        String callReg = argu.newReg();
        List<String> pairs = new ArrayList<>();
        List<String> operands = exprListToOperandList(n.f4, argu);

        List<VarSymbol> locals = new ArrayList<>(method.locals.values());
        int paramsSize = method.paramList.size();

        List<VarSymbol> params = new ArrayList<>(locals.subList(0, paramsSize));
        for (int i = 0; i < paramsSize; i++) {
            pairs.add(convertType(params.get(i).type) + " " + operands.get(i));
        }

        String args = pairs.isEmpty() ? "" : ", " + String.join(", ", pairs);
        argu.emit("%s = call %s %s(i8* %s%s)",
            callReg,
            convertType(method.returnType),
            fpReg,
            objReg,
            args);

        return callReg;
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

    private List<String> exprListToOperandList(NodeOptional nodeopt, State state) throws Exception {
        List<String> operands = new ArrayList<>();

        if (nodeopt.present()) {
            ExpressionList exprList = ((ExpressionList) nodeopt.node);

            operands.add(exprList.f0.f0.accept(this, state));

            for (Node node : exprList.f1.f0.nodes) {
                ExpressionTerm exprTerm = ((ExpressionTerm) node);
                String op = exprTerm.f1.accept(this, state);
                operands.add(op);
            }
        }
        return operands;
    }

    private void emitVtable(State state) {
        boolean first = true;
        for (ClassSymbol classSymbol : globalTable.classes.values()) {
            if (classSymbol.mainMethod != null) {
                continue;
            }

            if (first) {
                state.emitRaw("");
                first = false;
            }

            List<String> vtableList = new ArrayList<>();
            for (MethodSymbol method : classSymbol.vtableList) {
                vtableList.add("ptr @" + method.ownerClass.name + "." + method.name);
            }

            int methodCount = classSymbol.vtableSize / 8;
            state.emitRaw("@.%s_vtable = global [ %s x ptr ] [ %s ]",
                classSymbol.name,
                methodCount,
                methodCount > 0 ? String.join(", ", vtableList) : "");
        }
    }

    public void generate(Goal root, String inputFileName, State state) throws Exception {
        state.irText = new StringBuilder();
        state.irText.append(helpers);

        emitVtable(state);

        root.accept(this, state);

        String outputFileName = inputFileName.substring(0, inputFileName.lastIndexOf(".java"));
        outputFileName += ".ll";

        Path filePath = Paths.get(outputFileName);

        try {
            Files.writeString(filePath, state.irText.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
