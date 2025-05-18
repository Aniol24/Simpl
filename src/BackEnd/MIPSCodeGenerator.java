package BackEnd;

import FrontEnd.TAC.TACInstruction;
import Global.SymbolTable.Symbol;
import Global.SymbolTable.SymbolTable;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class MIPSCodeGenerator {
    private String currentFunction = null;
    private Symbol currentFunctionSym = null;
    private PrintWriter out;
    private Map<String,Integer> localOffset;
    private Map<String,String> varType;
    private Map<String,String> floatConstants;
    private int frameSize;
    private int paramCount;
    private boolean commentTAC = false;

    private final List<TACInstruction> TACCode;
    private final SymbolTable symbolTable;

    public MIPSCodeGenerator( List<TACInstruction> TACCode, SymbolTable symbolTable) {
        this.TACCode = TACCode;
        this.symbolTable = symbolTable;
    }

    public void setCommentTAC(boolean commentTAC) {
        this.commentTAC = commentTAC;
    }

    public void generate() {
        Map<String,List<TACInstruction>> funcs = groupByFunction(TACCode);

        floatConstants = new LinkedHashMap<>();
        int floatConstCount = 0;
        for (TACInstruction ins : TACCode) {
            String a1Val = ins.getArg1();
            if (a1Val != null && a1Val.matches("^-?\\d+\\.\\d+$") && !floatConstants.containsKey(a1Val)) {
                floatConstants.put(a1Val, "LC" + (floatConstCount++));
            }

            String a2Val = ins.getArg2();
            if (a2Val != null && a2Val.matches("^-?\\d+\\.\\d+$") && !floatConstants.containsKey(a2Val)) {
                floatConstants.put(a2Val, "LC" + (floatConstCount++));
            }
        }

        File dir = new File("out");
        if (!dir.exists()) dir.mkdirs();
        try {
            out = new PrintWriter(new File(dir, "program.asm"));

            emitData();
            emitText(funcs);
            out.close();
        } catch (Exception e) {
            System.err.println("Error al escribir el fichero de salida: " + e.getMessage());
        }
    }

    private Map<String,List<TACInstruction>> groupByFunction(List<TACInstruction> code) {
        Map<String,List<TACInstruction>> map = new LinkedHashMap<>();
        String currentFuncLabel = null;

        for (TACInstruction ins : code) {

            if ("label".equals(ins.getOp())) {
                String lbl = ins.getResult();
                if (lbl == null) continue;

                if (!lbl.matches("^L\\d+$")) {
                    currentFuncLabel = lbl;
                    map.put(currentFuncLabel, new ArrayList<>());
                }
            }

            if (currentFuncLabel != null) {
                if (map.containsKey(currentFuncLabel)) {
                    map.get(currentFuncLabel).add(ins);
                } else {
                    System.err.println("MIPSCodeGenerator: Instruction or label '" + ins + "' encountered before a function-defining label.");
                }
            }
        }
        return map;
    }

    private void emitData() {
        out.println("\t.data\n");
        for (Map.Entry<String,String> e : floatConstants.entrySet()) {
            out.printf("%s: .float %s\n", e.getValue(), e.getKey());
        }
        if (!floatConstants.isEmpty()) out.println();
    }

    private void emitText(Map<String,List<TACInstruction>> funcs) {
        out.println("\t.text");
        out.println("\t.globl __start");
        out.println("__start:");
        out.println("\tjal main");
        out.println();

        if (funcs.containsKey("main")) {
            emitFunction("main", funcs.get("main"));
            out.println();
        }
        for (String fnName : funcs.keySet()) {
            if (!"main".equals(fnName)) {
                emitFunction(fnName, funcs.get(fnName));
                out.println();
            }
        }
    }

    private void emitFunction(String fnName, List<TACInstruction> body) {
        this.currentFunction = fnName;
        this.currentFunctionSym = symbolTable.getGlobalScope().lookupSymbol(fnName);

        if (this.currentFunctionSym == null || !this.currentFunctionSym.isFunction()) {
            System.err.println("MIPS Gen Error: Function symbol not found for " + fnName);
            setupFrame(body, null);
        } else {
            setupFrame(body, this.currentFunctionSym);
        }

        out.println(fnName + ":");
        out.printf("\taddi $sp, $sp, -%d\n", frameSize);
        out.printf("\tsw   $ra, %d($sp)\n", frameSize - 4);
        out.printf("\tsw   $fp, %d($sp)\n", frameSize - 8);
        out.printf("\taddi $fp, $sp, %d\n", frameSize);
        out.println();

        paramCount = 0;
        for (TACInstruction ins : body) {
            if ("label".equals(ins.getOp()) && ins.getResult().equals(fnName)) continue;
            emitInstruction(ins);
        }

        out.println(fnName + "_exit:");
        out.println("\tmove $sp, $fp");
        out.println("\tlw   $ra, -4($fp)");
        out.println("\tlw   $fp, -8($fp)");

        if ("main".equals(fnName)) {
            out.println("\tli   $v0, 10");
            out.println("\tsyscall");
        } else {
            out.println("\tjr   $ra");
            out.println("\tnop");
        }
    }

    private void emitInstruction(TACInstruction ins) {

        if (commentTAC)
            out.printf("\n\t# TAC: %s\n", ins.toString());

        String opKey = ins.getOp();
        switch (opKey) {
            case "<":   opKey = "LOWER";          break;
            case ">":   opKey = "GREATER";        break;
            case "<=":  opKey = "LOWER_EQUAL";    break;
            case ">=":  opKey = "GREATER_EQUAL";  break;
            case "==":  opKey = "EQUALS";         break;
            case "!=":  opKey = "NOT_EQUAL";      break;
            case "&&":  opKey = "AND";            break;
            case "||":  opKey = "OR";             break;
            default: break;
        }

        String op  = opKey;
        String a1  = ins.getArg1();
        String a2  = ins.getArg2();
        String res = ins.getResult();

        if (this.currentFunctionSym == null && this.currentFunction != null) {
            this.currentFunctionSym = symbolTable.getGlobalScope().lookupSymbol(this.currentFunction);
        }


        switch (op) {
            case "label":
                out.println(res + ":");
                break;

            case "param":
                String paramType = getVarOrLiteralType(a1);
                if ("flt".equals(paramType)) {
                    loadOperandToFPU(a1, "$f12");
                    out.println("\tmfc1 $t0, $f12  # Move float bits to GPR for param passing");
                } else {
                    loadOperandToGPR(a1, "$t0");
                }
                if (paramCount < 4) {
                    out.printf("\tmove $a%d, $t0\n", paramCount);
                } else {
                    out.println("\taddi $sp, $sp, -4");
                    out.println("\tsw   $t0, 0($sp)");
                }
                paramCount++;
                break;

            case "call":
                out.println("\tjal " + a1);
                if (paramCount > 4) {
                    out.printf("\taddi $sp, $sp, %d\n", (paramCount - 4) * 4);
                }
                paramCount = 0;

                if (res != null) {
                    Symbol calledFuncSym = symbolTable.getGlobalScope().lookupSymbol(a1);
                    String funcActualReturnType = "int";
                    if (calledFuncSym != null && calledFuncSym.isFunction()) {
                        funcActualReturnType = calledFuncSym.getReturnType();
                    }

                    if ("flt".equals(funcActualReturnType)) {
                        storeFPUResult(res, "$f0");
                    } else {
                        out.println("\tmove $t0, $v0");
                        storeGPRResult(res, "$t0");
                    }
                }
                break;

            case "=":
                String assignResType = getVarOrLiteralType(res);
                if ("flt".equals(assignResType)) {
                    loadOperandToFPU(a1, "$f16");
                    storeFPUResult(res, "$f16");
                } else {
                    loadOperandToGPR(a1, "$t2");
                    storeGPRResult(res, "$t2");
                }
                break;

            case "SUM":
            case "SUB":
            case "MULT":
            case "DIV":
                String typeResArith = getVarOrLiteralType(res);
                String typeA1Arith = getVarOrLiteralType(a1);
                String typeA2Arith = getVarOrLiteralType(a2);

                boolean isFloatOp = "flt".equals(typeResArith) || "flt".equals(typeA1Arith) || "flt".equals(typeA2Arith);

                if (isFloatOp) {
                    loadOperandToFPU(a1, "$f12");
                    loadOperandToFPU(a2, "$f14");
                    String fpuOp = "";
                    switch (op) {
                        case "SUM": fpuOp = "add.s"; break;
                        case "SUB": fpuOp = "sub.s"; break;
                        case "MULT": fpuOp = "mul.s"; break;
                        case "DIV": fpuOp = "div.s"; break;
                    }
                    out.printf("\t%s $f16, $f12, $f14\n", fpuOp);
                    storeFPUResult(res, "$f16");
                } else {
                    loadOperandToGPR(a1, "$t0");
                    loadOperandToGPR(a2, "$t1");
                    String gprOp = "";
                    switch (op) {
                        case "SUM": gprOp = "add"; break;
                        case "SUB": gprOp = "sub"; break;
                        case "MULT": gprOp = "mul"; break;
                        case "DIV": break;
                    }
                    if ("DIV".equals(op)) {
                        out.println("\tdiv  $t0, $t1");
                        out.println("\tmflo $t2");
                    } else {
                        out.printf("\t%s  $t2, $t0, $t1\n", gprOp);
                    }
                    storeGPRResult(res, "$t2");
                }
                break;

            case "LOWER":
                loadOperandToGPR(a1, "$t0");
                loadOperandToGPR(a2, "$t1");
                out.println("\tslt  $t2, $t0, $t1");
                storeGPRResult(res, "$t2");
                break;

            case "LOWER_EQUAL":
                loadOperandToGPR(a1, "$t0");
                loadOperandToGPR(a2, "$t1");
                out.println("\tsle  $t2, $t0, $t1");
                storeGPRResult(res, "$t2");
                break;

            case "NOT":
                loadOperandToGPR(a1, "$t0");
                out.println("\tseq  $t2, $t0, $zero");
                storeGPRResult(res, "$t2");
                break;

            case "AND":
                loadOperandToGPR(a1, "$t0");
                out.println("\tsne  $t0, $t0, $zero");
                loadOperandToGPR(a2, "$t1");
                out.println("\tsne  $t1, $t1, $zero");
                out.println("\tand  $t2, $t0, $t1");
                storeGPRResult(res, "$t2");
                break;

            case "GREATER":
                loadOperandToGPR(a1, "$t0");
                loadOperandToGPR(a2, "$t1");
                out.println("\tslt  $t2, $t1, $t0");
                storeGPRResult(res, "$t2");
                break;

            case "GREATER_EQUAL":
                loadOperandToGPR(a1, "$t0");
                loadOperandToGPR(a2, "$t1");
                out.println("\tsge  $t2, $t0, $t1");
                storeGPRResult(res, "$t2");
                break;

            case "EQUALS":
                loadOperandToGPR(a1, "$t0");
                loadOperandToGPR(a2, "$t1");
                out.println("\tseq  $t2, $t0, $t1");
                storeGPRResult(res, "$t2");
                break;

            case "NOT_EQUAL":
                loadOperandToGPR(a1, "$t0");
                loadOperandToGPR(a2, "$t1");
                out.println("\tsne  $t2, $t0, $t1");
                storeGPRResult(res, "$t2");
                break;

            case "ifFalse":
                loadOperandToGPR(a1, "$t0");
                out.println("\tbeq  $t0, $zero, " + res);
                break;

            case "goto":
                out.println("\tj    " + res);
                break;

            case "return":
                if (ins.getArg1() != null) {
                    String returnValName = ins.getArg1();
                    String funcDeclaredReturnType = "int";
                    if (this.currentFunctionSym != null) {
                        funcDeclaredReturnType = this.currentFunctionSym.getReturnType();
                    }

                    if ("flt".equals(funcDeclaredReturnType)) {
                        loadOperandToFPU(returnValName, "$f0");
                    } else {
                        loadOperandToGPR(returnValName, "$v0");
                    }
                }
                out.println("\tj   " + currentFunction + "_exit");
                out.println("\tnop");
                break;

            case "OR":
                loadOperandToGPR(a1, "$t0");
                out.println("\tsne  $t0, $t0, $zero");
                loadOperandToGPR(a2, "$t1");
                out.println("\tsne  $t1, $t1, $zero");
                out.println("\tor   $t2, $t0, $t1");
                storeGPRResult(res, "$t2");
                break;

            default:
                out.printf("\t# UNHANDLED: %s %s,%s -> %s\n",
                        op, a1, a2, res);
        }
    }

    private String getVarOrLiteralType(String operand) {
        if (operand == null) return "int";

        if (varType.containsKey(operand)) return varType.get(operand);

        if (operand.matches("^-?\\d+\\.\\d+$") || floatConstants.containsKey(operand)) return "flt";
        if (operand.matches("^-?\\d+$")) return "int";
        if (operand.matches("^'.'$")) return "chr";

        Symbol s = symbolTable.lookupSymbol(operand);
        if (s != null && !s.isFunction()) {
            varType.put(operand, s.getType());
            return s.getType();
        }
        return "int";
    }

    private void loadOperandToGPR(String operand, String targetGPR) {
        if (operand == null) {
            out.printf("\tli   %s, 0 # loadOperandToGPR: null operand\n", targetGPR);
            return;
        }
        String type = getVarOrLiteralType(operand);

        if (operand.matches("param\\d+")) {
            int idx = Integer.parseInt(operand.substring(5)) - 1;
            if (idx >= 0 && idx < 4) {
                out.printf("\tmove %s, $a%d\n", targetGPR, idx);
            } else {
                out.printf("\tlw   %s, %d($fp) # Accessing stacked param %s\n", targetGPR, 8 + (idx - 4) * 4, operand);
            }
        } else if (operand.matches("^-?\\d+$")) {
            out.printf("\tli   %s, %s\n", targetGPR, operand);
        } else if (operand.matches("^'.'$")) {
            out.printf("\tli   %s, %d\n", targetGPR, (int) operand.charAt(1));
        } else if ("flt".equals(type)) {
            String tempFPR = "$f18";
            loadOperandToFPU(operand, tempFPR);
            out.printf("\tcvt.w.s %s, %s\n", tempFPR, tempFPR);
            out.printf("\tmfc1 %s, %s\n", targetGPR, tempFPR);
        } else {
            Integer offset = localOffset.get(operand);
            if (offset == null) {
                out.printf("\tli   %s, 0 # Error: %s not in localOffset\n", targetGPR, operand);
            } else {
                out.printf("\tlw   %s, %d($fp)\n", targetGPR, offset);
            }
        }
    }

    private void loadOperandToFPU(String operand, String targetFPR) {
        if (operand == null) {
            out.printf("\tli   $t9, 0 # loadOperandToFPU: null operand\n");
            out.printf("\tmtc1 $t9, %s\n", targetFPR);
            out.printf("\tcvt.s.w %s, %s\n", targetFPR, targetFPR);
            return;
        }
        String type = getVarOrLiteralType(operand);

        if (floatConstants.containsKey(operand)) {
            String lbl = floatConstants.get(operand);
            out.printf("\tlwc1 %s, %s\n", targetFPR, lbl);
        } else if ("flt".equals(type)) {
            Integer offset = localOffset.get(operand);
            if (offset == null) {
                out.printf("\tli   $t9, 0 # Error: %s not in localOffset for FPU load\n", operand);
                out.printf("\tmtc1 $t9, %s\n", targetFPR);
                out.printf("\tcvt.s.w %s, %s\n", targetFPR, targetFPR);
            } else {
                out.printf("\tlwc1 %s, %d($fp)\n", targetFPR, offset);
            }
        } else {
            String tempGPR = "$t9";
            if (operand.matches("^-?\\d+$")) {
                out.printf("\tli   %s, %s\n", tempGPR, operand);
            } else if (operand.matches("^'.'$")) {
                out.printf("\tli   %s, %d\n", tempGPR, (int) operand.charAt(1));
            } else if (operand.matches("param\\d+")) {
                int idx = Integer.parseInt(operand.substring(5)) - 1;
                if (idx >= 0 && idx < 4) { out.printf("\tmove %s, $a%d\n", tempGPR, idx); }
                else { out.printf("\tlw   %s, %d($fp) # Accessing stacked param %s for FPU conv\n", tempGPR, 8 + (idx - 4) * 4, operand); }
            }
            else {
                Integer offset = localOffset.get(operand);
                if (offset != null) out.printf("\tlw   %s, %d($fp)\n", tempGPR, offset);
                else out.printf("\tli   %s, 0 # Error: %s not in localOffset for GPR->FPU conv\n", tempGPR, operand);
            }
            out.printf("\tmtc1 %s, %s\n", tempGPR, targetFPR);
            out.printf("\tcvt.s.w %s, %s\n", targetFPR, targetFPR);
        }
    }

    private void storeGPRResult(String varName, String sourceGPR) {
        Integer offset = localOffset.get(varName);
        if (offset == null) {
            out.printf("\t# Error: %s not in localOffset for GPR store. Store ignored.\n", varName);
            return;
        }
        out.printf("\tsw   %s, %d($fp)\n", sourceGPR, offset);
    }

    private void storeFPUResult(String varName, String sourceFPR) {
        Integer offset = localOffset.get(varName);
        if (offset == null) {
            out.printf("\t# Error: %s not in localOffset for FPU store. Store ignored.\n", varName);
            return;
        }
        out.printf("\tswc1 %s, %d($fp)\n", sourceFPR, offset);
    }

    private void setupFrame(List<TACInstruction> body, Symbol functionSymbol) {
        localOffset = new LinkedHashMap<>();
        varType = new LinkedHashMap<>();
        Set<String> frameVariables = new LinkedHashSet<>();

        if (functionSymbol != null && functionSymbol.isFunction() && functionSymbol.getParameters() != null) {
            List<Symbol> params = functionSymbol.getParameters();
            for (Symbol paramSymbol : params) {
                varType.put(paramSymbol.getName(), paramSymbol.getType());
                frameVariables.add(paramSymbol.getName());
            }
        }

        for (TACInstruction ins : body) {
            String res = ins.getResult();
            String a1 = ins.getArg1();
            String a2 = ins.getArg2();
            String op = ins.getOp();

            getVarOrLiteralType(a1, symbolTable, varType, floatConstants, functionSymbol);
            getVarOrLiteralType(a2, symbolTable, varType, floatConstants, functionSymbol);

            if (res != null && !"label".equals(op) && !res.matches("^L\\d+$")) {
                frameVariables.add(res);

                if (!varType.containsKey(res)) {
                    Symbol sRes = symbolTable.lookupSymbol(res);
                    if (sRes != null && !sRes.isFunction()) {
                        varType.put(res, sRes.getType());
                    } else {
                        String inferredType = "int";

                        if ("=".equals(op)) {
                            if (a1 != null) {
                                inferredType = getVarOrLiteralType(a1, symbolTable, varType, floatConstants, functionSymbol);
                            }
                        } else if (List.of("SUM", "SUB", "MULT", "DIV").contains(op)) {
                            String typeOfA1 = getVarOrLiteralType(a1, symbolTable, varType, floatConstants, functionSymbol);
                            String typeOfA2 = getVarOrLiteralType(a2, symbolTable, varType, floatConstants, functionSymbol);
                            if ("flt".equals(typeOfA1) || "flt".equals(typeOfA2)) {
                                inferredType = "flt";
                            } else {
                                inferredType = "int";
                            }
                        } else if (List.of("LOWER", "LOWER_EQUAL", "NOT", "AND", "GREATER", "GREATER_EQUAL", "EQUALS", "NOT_EQUAL").contains(op)) {
                            inferredType = "int";
                        } else if ("call".equals(op) && a1 != null) {
                            Symbol calledFuncSymbol = symbolTable.getGlobalScope().lookupSymbol(a1);
                            if (calledFuncSymbol != null && calledFuncSymbol.isFunction()) {
                                String retType = calledFuncSymbol.getReturnType();
                                if (retType != null && !retType.equalsIgnoreCase("void")) {
                                    inferredType = retType;
                                } else {
                                    inferredType = "int";
                                }
                            }
                        }
                        varType.put(res, inferredType);
                    }
                }
            }
        }

        for (String varInFrame : new LinkedHashSet<>(frameVariables)) {
            if (!varType.containsKey(varInFrame)) {
                Symbol s = symbolTable.lookupSymbol(varInFrame);
                if (s != null && !s.isFunction()) {
                    varType.put(varInFrame, s.getType());
                } else {
                    varType.put(varInFrame, "int");
                }
            }
        }

        int currentOffset = -8;
        for (String varName : frameVariables) {
            currentOffset -= 4;
            localOffset.put(varName, currentOffset);
        }

        int totalBytesForVars = frameVariables.size() * 4;
        frameSize = 8 + totalBytesForVars;
        if (frameSize % 8 != 0) {
            frameSize = ((frameSize / 8) + 1) * 8;
        }
        if (frameSize < 8) frameSize = 8;
    }

    private String getVarOrLiteralType(String operand, SymbolTable symTable, Map<String, String> varTypeMap,
                                       Map<String, String> floatLits, Symbol currentFunctionSym) {
        if (operand == null) return "int";

        if (operand.matches("^-?\\d+\\.\\d+$") || (floatLits != null && floatLits.containsKey(operand))) return "flt";
        if (operand.matches("^-?\\d+$")) return "int";
        if (operand.matches("^'.'$")) return "chr";

        if (varTypeMap.containsKey(operand)) return varTypeMap.get(operand);

        Symbol s = symTable.lookupSymbol(operand);
        if (s != null && !s.isFunction()) {
            varTypeMap.put(operand, s.getType());
            return s.getType();
        }

        if (operand.startsWith("param") && currentFunctionSym != null && currentFunctionSym.isFunction()) {
            try {
                int paramIndex = Integer.parseInt(operand.substring(5)) - 1;
                List<Symbol> formalParams = currentFunctionSym.getParameters();
                if (formalParams != null && paramIndex >= 0 && paramIndex < formalParams.size()) {
                    return formalParams.get(paramIndex).getType();
                }
            } catch (NumberFormatException e) {
                // Ignorar
            }
        }

        return "int";
    }
}
