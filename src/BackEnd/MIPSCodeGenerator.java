package BackEnd;

import FrontEnd.TAC.TACInstruction;
import Global.SymbolTable.Symbol;
import Global.SymbolTable.SymbolTable;
import Global.SymbolTable.Scope;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class MIPSCodeGenerator {
    private String currentFunction = null;
    private Symbol currentFunctionSym = null; // Added
    private PrintWriter out;
    private Map<String,Integer> localOffset;
    private Map<String,String> varType;
    private Map<String,String> floatConstants; // literales float a etiquetas
    private int floatConstCount;
    private int frameSize;
    private int paramCount;

    private List<TACInstruction> TACCode;
    private SymbolTable symbolTable;

    public MIPSCodeGenerator( List<TACInstruction> TACCode, SymbolTable symbolTable) {
        this.TACCode = TACCode;
        this.symbolTable = symbolTable;
    }

    public void generate() {
        Map<String,List<TACInstruction>> funcs = groupByFunction(TACCode);

        floatConstants = new LinkedHashMap<>();
        floatConstCount = 0;
        for (TACInstruction ins : TACCode) {
            String a1Val = ins.getArg1();
            // Check arg1 for float literal
            if (a1Val != null && a1Val.matches("^-?\\d+\\.\\d+$") && !floatConstants.containsKey(a1Val)) {
                floatConstants.put(a1Val, "LC" + (floatConstCount++));
            }
            // Check arg2 for float literal (if applicable for some ops)
            String a2Val = ins.getArg2();
            if (a2Val != null && a2Val.matches("^-?\\d+\\.\\d+$") && !floatConstants.containsKey(a2Val)) {
                floatConstants.put(a2Val, "LC" + (floatConstCount++));
            }
        }

        // Preparar fichero de salida
        File dir = new File("out");
        if (!dir.exists()) dir.mkdirs();
        try {
            out = new PrintWriter(new File(dir, "program.s"));

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
                    currentFuncLabel = lbl; // This label starts a new function
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
        this.currentFunction = fnName; // Set current function name for context
        this.currentFunctionSym = symbolTable.getGlobalScope().lookupSymbol(fnName); // Store current function symbol

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
            // Skip the initial function label as it's already handled by out.println(fnName + ":");
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
        String op  = ins.getOp();
        String a1  = ins.getArg1();
        String a2  = ins.getArg2();
        String res = ins.getResult();

        // Make sure currentFunctionSym is set if not already (e.g. if emitInstruction is called outside emitFunction context)
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
                    loadOperandToFPU(a1, "$f12"); // Load float param into a standard FPU arg reg (or temp)
                    // Assuming float params are passed via GPRs (raw bits) for simplicity with current stack logic
                    out.println("\tmfc1 $t0, $f12  # Move float bits to GPR for param passing");
                } else {
                    loadOperandToGPR(a1, "$t0"); // Load int/char param into $t0
                }
                // Existing logic for $a0-$a3 or stack using $t0
                if (paramCount < 4) {
                    out.printf("\tmove $a%d, $t0\n", paramCount);
                } else {
                    out.println("\taddi $sp, $sp, -4");
                    out.println("\tsw   $t0, 0($sp)");
                }
                paramCount++;
                break;

            case "call":
                out.println("\tjal " + a1); // a1 is function name
                if (paramCount > 4) { // Clean up stack space for params passed on stack
                    out.printf("\taddi $sp, $sp, %d\n", (paramCount - 4) * 4);
                }
                paramCount = 0; // Reset for next call

                if (res != null) { // If function has a return value
                    Symbol calledFuncSym = symbolTable.getGlobalScope().lookupSymbol(a1);
                    String funcActualReturnType = "int"; // Default
                    if (calledFuncSym != null && calledFuncSym.isFunction()) {
                        funcActualReturnType = calledFuncSym.getReturnType();
                    }

                    if ("flt".equals(funcActualReturnType)) { // Function returns a float (conventionally in $f0)
                        storeFPUResult(res, "$f0"); // Store from $f0 (FPU return reg) to var 'res'
                    } else { // Function returns int/char (conventionally in $v0)
                        out.println("\tmove $t0, $v0"); // Move from $v0 to $t0
                        storeGPRResult(res, "$t0");   // Store from $t0 to var 'res'
                    }
                }
                break;

            case "=":
                String assignResType = getVarOrLiteralType(res);
                if ("flt".equals(assignResType)) {
                    loadOperandToFPU(a1, "$f16"); // Load a1 into a temp FPU reg
                    storeFPUResult(res, "$f16");   // Store it to res
                } else { // int or char
                     // loadOperandToGPR handles char literals correctly if a1 is like 'c'
                    loadOperandToGPR(a1, "$t2"); // Load a1 into a temp GPR
                    storeGPRResult(res, "$t2");    // Store it to res
                }
                break;

            case "SUM":
            case "SUB":
            case "MULT":
            case "DIV":
                String typeResArith = getVarOrLiteralType(res);
                String typeA1Arith = getVarOrLiteralType(a1);
                String typeA2Arith = getVarOrLiteralType(a2);

                // Promote to float if any operand is float, or if result is float
                boolean isFloatOp = "flt".equals(typeResArith) || "flt".equals(typeA1Arith) || "flt".equals(typeA2Arith);

                if (isFloatOp) {
                    loadOperandToFPU(a1, "$f12"); // Arg1 in $f12
                    loadOperandToFPU(a2, "$f14"); // Arg2 in $f14
                    String fpuOp = "";
                    switch (op) {
                        case "SUM": fpuOp = "add.s"; break;
                        case "SUB": fpuOp = "sub.s"; break;
                        case "MULT": fpuOp = "mul.s"; break;
                        case "DIV": fpuOp = "div.s"; break;
                    }
                    out.printf("\t%s $f16, $f12, $f14\n", fpuOp); // Result in $f16
                    storeFPUResult(res, "$f16");
                } else { // Integer operation
                    loadOperandToGPR(a1, "$t0"); // Arg1 in $t0
                    loadOperandToGPR(a2, "$t1"); // Arg2 in $t1
                    String gprOp = "";
                    switch (op) {
                        case "SUM": gprOp = "add"; break;
                        case "SUB": gprOp = "sub"; break;
                        case "MULT": gprOp = "mul"; break;
                        case "DIV": /* DIV needs mflo */ break;
                    }
                    if ("DIV".equals(op)) {
                        out.println("\tdiv  $t0, $t1");
                        out.println("\tmflo $t2");
                    } else {
                        out.printf("\t%s  $t2, $t0, $t1\n", gprOp); // Result in $t2
                    }
                    storeGPRResult(res, "$t2");
                }
                break;

            // ... (LOWER, GREATER, EQUALS etc. also need type-aware float versions using c.xx.s and bc1t/f) ...
            // For now, keeping them as integer ops for brevity
            case "LOWER": // Example:
                // To do properly: check types. If float: c.lt.s, bc1t/f
                loadOperandToGPR(a1, "$t0");
                loadOperandToGPR(a2, "$t1");
                out.println("\tslt  $t2, $t0, $t1");
                storeGPRResult(res, "$t2");
                break;
            
            case "LOWER_EQUAL":
                loadOperandToGPR(a1, "$t0");
                loadOperandToGPR(a2, "$t1");
                out.println("\tsle  $t2, $t0, $t1"); // pseudo-instruction, often `slt` then `seq` with $zero
                storeGPRResult(res, "$t2");
                break;

            case "NOT":
                loadOperandToGPR(a1, "$t0");
                out.println("\tseq  $t2, $t0, $zero");
                storeGPRResult(res, "$t2");
                break;

            case "AND":
                loadOperandToGPR(a1, "$t0");
                out.println("\tsne  $t0, $t0, $zero"); // $t0 = (a1 != 0)
                loadOperandToGPR(a2, "$t1");
                out.println("\tsne  $t1, $t1, $zero"); // $t1 = (a2 != 0)
                out.println("\tand  $t2, $t0, $t1");
                storeGPRResult(res, "$t2");
                break;

            case "GREATER":
                loadOperandToGPR(a1, "$t0");
                loadOperandToGPR(a2, "$t1");
                out.println("\tslt  $t2, $t1, $t0"); // res = a2 < a1  <=> a1 > a2
                storeGPRResult(res, "$t2");
                break;

            case "GREATER_EQUAL":
                loadOperandToGPR(a1, "$t0");
                loadOperandToGPR(a2, "$t1");
                out.println("\tsge  $t2, $t0, $t1"); // pseudo-instruction
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
                loadOperandToGPR(a1, "$t0"); // Condition var/literal
                out.println("\tbeq  $t0, $zero, " + res); // res is label
                break;

            case "goto":
                out.println("\tj    " + res); // res is label
                break;

            case "print":
                String printVarType = getVarOrLiteralType(a1);
                if ("flt".equals(printVarType)) {
                    loadOperandToFPU(a1, "$f12"); // Syscall for print float expects value in $f12
                    out.println("\tli   $v0, 2");
                    out.println("\tsyscall");
                } else if ("chr".equals(printVarType)) {
                    loadOperandToGPR(a1, "$a0"); // Syscall for print char expects value in $a0
                    out.println("\tli   $v0, 11");
                    out.println("\tsyscall");
                } else { // Assume int
                    loadOperandToGPR(a1, "$a0"); // Syscall for print int expects value in $a0
                    out.println("\tli   $v0, 1");
                    out.println("\tsyscall");
                }
                break;

            case "return":
                if (ins.getArg1() != null) {
                    String returnValName = ins.getArg1();
                    String funcDeclaredReturnType = "int"; // Default
                    if (this.currentFunctionSym != null) { // Get actual declared return type
                        funcDeclaredReturnType = this.currentFunctionSym.getReturnType();
                    }

                    if ("flt".equals(funcDeclaredReturnType)) {
                        loadOperandToFPU(returnValName, "$f0"); // Return float in $f0
                    } else { // Return int/char in $v0
                        loadOperandToGPR(returnValName, "$v0");
                    }
                }
                out.println("\tj   " + currentFunction + "_exit");
                out.println("\tnop");
                break;

            default:
                out.printf("\t# UNHANDLED: %s %s,%s -> %s\n",
                        op, a1, a2, res);
        }
    }

    private String getVarOrLiteralType(String operand) {
        // Simplified: Assumes varType is already populated by setupFrame for variables
        // For literals, it deduces directly.
        if (operand == null) return "int"; // Should not happen ideally

        if (varType.containsKey(operand)) return varType.get(operand);

        if (operand.matches("^-?\\d+\\.\\d+$") || floatConstants.containsKey(operand)) return "flt";
        if (operand.matches("^-?\\d+$")) return "int";
        if (operand.matches("^'.'$")) return "chr";
        
        // Fallback if not in varType (e.g. direct param use, though setupFrame should cover most)
        Symbol s = symbolTable.lookupSymbol(operand); // General lookup
        if (s != null && !s.isFunction()) {
            varType.put(operand, s.getType()); // Cache if found
            return s.getType();
        }
        // If it's a temporary not yet in varType (should be rare after setupFrame)
        // or an undeclared variable, default to int and perhaps warn.
        // System.err.println("Warning: Type for '" + operand + "' not found, defaulting to int.");
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
            if (idx >= 0 && idx < 4) { // Params in $a0-$a3
                out.printf("\tmove %s, $a%d\n", targetGPR, idx);
            } else { // Params on stack relative to $fp
                 // This calculation needs to be correct based on your calling convention
                 // For parameters passed by caller *before* current frame is set up:
                 // They are at positive offsets from $fp if $fp points to old $fp.
                 // If $fp points to base of current frame, and params are above $ra, $fp:
                 // e.g. $fp+8, $fp+12 ...
                 // For now, assuming a convention where they are accessible; this needs review.
                out.printf("\tlw   %s, %d($fp) # Accessing stacked param %s\n", targetGPR, 8 + (idx - 4) * 4, operand);
            }
        } else if (operand.matches("^-?\\d+$")) { // Integer literal
            out.printf("\tli   %s, %s\n", targetGPR, operand);
        } else if (operand.matches("^'.'$")) { // Char literal
            out.printf("\tli   %s, %d\n", targetGPR, (int) operand.charAt(1));
        } else if ("flt".equals(type)) { // Float operand, convert to int for GPR
            String tempFPR = "$f18"; // dedicated FPU temp for conversion
            loadOperandToFPU(operand, tempFPR); // Load original float to FPU
            out.printf("\tcvt.w.s %s, %s\n", tempFPR, tempFPR); // Convert float to word (int) in FPU
            out.printf("\tmfc1 %s, %s\n", targetGPR, tempFPR);  // Move from FPU to GPR
        } else { // int/char variable
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
            // Load 0.0 into FPU register
            out.printf("\tli   $t9, 0 # loadOperandToFPU: null operand\n"); // Use a GPR temp
            out.printf("\tmtc1 $t9, %s\n", targetFPR);
            out.printf("\tcvt.s.w %s, %s\n", targetFPR, targetFPR); // Convert 0 to 0.0
            return;
        }
        String type = getVarOrLiteralType(operand);

        if (floatConstants.containsKey(operand)) { // Float literal
            String lbl = floatConstants.get(operand);
            out.printf("\tlwc1 %s, %s\n", targetFPR, lbl);
        } else if ("flt".equals(type)) { // Float variable
            Integer offset = localOffset.get(operand);
            if (offset == null) {
                 out.printf("\tli   $t9, 0 # Error: %s not in localOffset for FPU load\n", operand); // Use a GPR temp
                 out.printf("\tmtc1 $t9, %s\n", targetFPR);
                 out.printf("\tcvt.s.w %s, %s\n", targetFPR, targetFPR); 
            } else {
                out.printf("\tlwc1 %s, %d($fp)\n", targetFPR, offset);
            }
        } else { // int/char operand, convert to float for FPU
            String tempGPR = "$t9"; // Use a GPR temp for loading int/char
            // Load int/char to GPR first (simplified, not calling loadOperandToGPR to avoid recursion issues with param handling)
            if (operand.matches("^-?\\d+$")) {
                 out.printf("\tli   %s, %s\n", tempGPR, operand);
            } else if (operand.matches("^'.'$")) {
                 out.printf("\tli   %s, %d\n", tempGPR, (int) operand.charAt(1));
            } else if (operand.matches("param\\d+")) {
                // Simplified param handling for this path, assuming it's an int param
                int idx = Integer.parseInt(operand.substring(5)) - 1;
                if (idx >= 0 && idx < 4) { out.printf("\tmove %s, $a%d\n", tempGPR, idx); }
                else { out.printf("\tlw   %s, %d($fp) # Accessing stacked param %s for FPU conv\n", tempGPR, 8 + (idx - 4) * 4, operand); }
            }
            else { // variable
                Integer offset = localOffset.get(operand);
                if (offset != null) out.printf("\tlw   %s, %d($fp)\n", tempGPR, offset);
                else out.printf("\tli   %s, 0 # Error: %s not in localOffset for GPR->FPU conv\n", tempGPR, operand);
            }
            out.printf("\tmtc1 %s, %s\n", tempGPR, targetFPR);      // Move int from GPR to FPU
            out.printf("\tcvt.s.w %s, %s\n", targetFPR, targetFPR); // Convert word (int) in FPU to single (float) in FPU
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
        Set<String> frameVariables = new LinkedHashSet<>(); // All vars needing stack space

        // 1. Process parameters from functionSymbol
        if (functionSymbol != null && functionSymbol.isFunction() && functionSymbol.getParameters() != null) {
            List<Symbol> params = functionSymbol.getParameters();
            for (Symbol paramSymbol : params) {
                // The TAC generator assigns "formalParamName = paramN".
                // We store the type of the formalParamName.
                varType.put(paramSymbol.getName(), paramSymbol.getType());
                frameVariables.add(paramSymbol.getName()); // Formal parameters are treated as local vars
            }
        }

        // 2. Scan TAC to identify all variables, determine their types, and add to frameVariables
        for (TACInstruction ins : body) {
            String res = ins.getResult();
            String a1 = ins.getArg1();
            String a2 = ins.getArg2();
            String op = ins.getOp();

            // Ensure types of operands (a1, a2) are known before processing result.
            // getVarOrLiteralType will also populate varType if a symbol is found.
            getVarOrLiteralType(a1, symbolTable, varType, floatConstants, functionSymbol);
            getVarOrLiteralType(a2, symbolTable, varType, floatConstants, functionSymbol);

            if (res != null && !"label".equals(op) && !res.matches("^L\\d+$")) {
                frameVariables.add(res); // All results that are variables need stack space

                if (!varType.containsKey(res)) { // If type not already known (e.g., from params)
                    Symbol sRes = symbolTable.lookupSymbol(res);
                    if (sRes != null && !sRes.isFunction()) {
                        varType.put(res, sRes.getType());
                    } else {
                        // Infer type for temporary variables or implicitly typed variables
                        String inferredType = "int"; // Default

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
                            inferredType = "int"; // Boolean results are int
                        } else if ("call".equals(op) && a1 != null) { // a1 is function name
                            Symbol calledFuncSymbol = symbolTable.getGlobalScope().lookupSymbol(a1); // Functions are global
                            if (calledFuncSymbol != null && calledFuncSymbol.isFunction()) {
                                String retType = calledFuncSymbol.getReturnType();
                                // Assuming "void" is not a type that can be assigned, or semantic analysis prevents it.
                                // If a function returns "void", its result shouldn't be assigned in TAC.
                                // If it is, defaulting to "int" or the actual return type.
                                if (retType != null && !retType.equalsIgnoreCase("void")) {
                                   inferredType = retType;
                                } else {
                                   inferredType = "int"; // Default for void return or if not specified
                                }
                            }
                        }
                        varType.put(res, inferredType);
                    }
                }
            }
        }
        
        // Fallback: Ensure all variables that ended up in frameVariables have a type.
        // This typically covers variables that might be used but not explicitly assigned a type above
        // (e.g. parameters used directly without being a 'res' in an assignment from paramN, though handled by param processing).
        for (String varInFrame : new LinkedHashSet<>(frameVariables)) { // Iterate over a copy to avoid CME if varType.put adds to frameVars indirectly
            if (!varType.containsKey(varInFrame)) {
                Symbol s = symbolTable.lookupSymbol(varInFrame);
                if (s != null && !s.isFunction()) {
                    varType.put(varInFrame, s.getType());
                } else {
                    // This case should be rare if logic above is complete.
                    // It might indicate an undeclared variable or a temporary that wasn't a result.
                    varType.put(varInFrame, "int"); // Default if absolutely no other info
                }
            }
        }

        // 3. Assign offsets (4 bytes each, assuming all are word-sized for now)
        int currentOffset = -8; // Start after saved $ra and $fp
        for (String varName : frameVariables) {
            currentOffset -= 4; // Allocate 4 bytes for each local/temp
            localOffset.put(varName, currentOffset);
        }

        // 4. Calculate total frameSize, rounded up to be multiple of 8 for alignment
        int totalBytesForVars = frameVariables.size() * 4;
        frameSize = 8 + totalBytesForVars; // 8 for $ra, $fp
        if (frameSize % 8 != 0) {
            frameSize = ((frameSize / 8) + 1) * 8;
        }
        if (frameSize < 8) frameSize = 8; // Ensure minimum size if no locals
    }

    private String getVarOrLiteralType(String operand, SymbolTable symTable, Map<String, String> varTypeMap, 
                                       Map<String, String> floatLits, Symbol currentFunctionSym) {
        if (operand == null) return "int"; // Default for null operand, though ideally should not happen.

        // 1. Check for literals
        if (operand.matches("^-?\\d+\\.\\d+$") || (floatLits != null && floatLits.containsKey(operand))) return "flt";
        if (operand.matches("^-?\\d+$")) return "int";
        if (operand.matches("^'.'$")) return "chr";

        // 2. Check if type already inferred/stored for this variable in the current function scope
        if (varTypeMap.containsKey(operand)) return varTypeMap.get(operand);

        // 3. Check symbol table (for declared variables, parameters)
        Symbol s = symTable.lookupSymbol(operand);
        if (s != null && !s.isFunction()) {
            varTypeMap.put(operand, s.getType()); // Cache it for future lookups in this function
            return s.getType();
        }
        
        // 4. Handle direct use of "paramN" (e.g. if TAC was "t0 = param1 + ...")
        //    This is less likely if TAC convention is "formalName = paramN"
        if (operand.startsWith("param") && currentFunctionSym != null && currentFunctionSym.isFunction()) {
            try {
                int paramIndex = Integer.parseInt(operand.substring(5)) - 1; // "param1" -> index 0
                List<Symbol> formalParams = currentFunctionSym.getParameters();
                if (formalParams != null && paramIndex >= 0 && paramIndex < formalParams.size()) {
                    // Return the type of the formal parameter it corresponds to.
                    // Do not cache "paramN" in varTypeMap, as "paramN" is not a user variable/temporary.
                    return formalParams.get(paramIndex).getType();
                }
            } catch (NumberFormatException e) { /* Not a valid "paramN" format, ignore */ }
        }
        
        // 5. Fallback: If it's not a literal, not in varTypeMap, not in symbol table,
        //    and not a resolvable "paramN", it's likely an undefined variable or an
        //    issue with TAC generation (e.g., temporary used before definition).
        //    Defaulting to "int" for robustness, but this might hide errors.
        //    Consider adding a warning here if such cases are not expected.
        return "int"; 
    }

    private void loadTo(String var) {
        if (var == null) {
            out.println("\t# loadTo: var is null");
            out.println("\tli   $t0, 0"); // Load 0 for null var to avoid crash
            return;
        }

        if (var.matches("param\\d+")) {
            int idx = Integer.parseInt(var.substring(5)) - 1;
            if (idx >= 0 && idx < 4) { // Assuming up to 4 params in registers
                out.printf("\tmove $t0, $a%d\n", idx);
            } else {
                out.printf("\t# loadTo: Accessing stacked parameter %s (not fully implemented)\n", var);
                out.printf("\tlw $t0, %d($fp) # Placeholder for stacked param %s\n", (idx-4)*4 , var);
            }
        } else if (var.matches("^-?\\d+$")) { // Integer literal
            out.printf("\tli   $t0, %s\n", var);
        } else if (var.matches("^'.'$")) { // Char literal
            out.printf("\tli   $t0, %d\n", (int)var.charAt(1));
        } else if (floatConstants.containsKey(var)) { // Float literal (loading address then value)
             String lbl = floatConstants.get(var);
             out.printf("\tlwc1 $f0, %s\n", lbl); // Direct load from label if $f0 is target
             out.printf("\t# loadTo: float literal %s to $t0 (integer part or address)\n", var);
             out.printf("\tla $t0, %s # Load address of float\n", lbl);


        } else { // Local variable or TAC temporary
            Integer offset = localOffset.get(var);
            if (offset == null) {
                out.printf("\t# loadTo: Variable %s not found in localOffset, loading 0\n", var);
                out.println("\tli   $t0, 0"); // Default to 0 if var not found
            } else {
                out.printf("\tlw   $t0, %d($fp)\n", offset);
            }
        }
    }

    private void loadToInto(String var, String reg) {
        if (var == null) {
            out.printf("\t# loadToInto: var is null for reg %s\n", reg);
            out.printf("\tli   %s, 0\n", reg);
            return;
        }
         if (var.matches("param\\d+")) {
            int idx = Integer.parseInt(var.substring(5)) - 1;
             if (idx >= 0 && idx < 4) {
                out.printf("\tmove %s, $a%d\n", reg, idx);
            } else {
                out.printf("\t# loadToInto: Accessing stacked parameter %s into %s (not fully implemented)\n", var, reg);
                out.printf("\tlw %s, %d($fp) # Placeholder for stacked param %s\n", reg, (idx-4)*4, var);
            }
        } else if (var.matches("^-?\\d+$")) {
            out.printf("\tli   %s, %s\n", reg, var);
        } else if (var.matches("^'.'$")) {
            out.printf("\tli   %s, %d\n", reg, (int)var.charAt(1));
        } else if (floatConstants.containsKey(var)) {
            // Similar to loadTo, this is tricky for general purpose registers
            out.printf("\t# loadToInto: float literal %s to %s (integer part or address)\n", var, reg);
            String lbl = floatConstants.get(var);
            out.printf("\tla %s, %s # Load address of float\n", reg, lbl);
        }
        else {
            Integer offset = localOffset.get(var);
            if (offset == null) {
                out.printf("\t# loadToInto: Variable %s not found in localOffset, loading 0 into %s\n", var, reg);
                out.printf("\tli   %s, 0\n", reg);
            } else {
                out.printf("\tlw   %s, %d($fp)\n", reg, offset);
            }
        }
    }

    private void storeLocal(String var) { // Assumes value to store is in $t0
        Integer offset = localOffset.get(var);
        if (offset == null) {
            out.printf("\t# storeLocal: Variable %s not found in localOffset. Store ignored.\n", var);
        } else {
            // Check type for sw vs swc1
            String type = varType.get(var);
            if ("float".equals(type)) {
                // Value should be in $f0 if it was a float operation result
                out.printf("\tswc1 $f0, %d($fp) # Assuming value in $f0 for float\n", offset);
            } else {
                out.printf("\tsw   $t0, %d($fp)\n", offset);
            }
        }
    }

    private void storeLocalTo(String var, String reg) { // Value to store is in 'reg'
         Integer offset = localOffset.get(var);
        if (offset == null) {
            out.printf("\t# storeLocalTo: Variable %s not found in localOffset. Store from %s ignored.\n", var, reg);
        } else {
            String type = varType.get(var);
             if ("float".equals(type)) {
                out.printf("\tswc1 %s, %d($fp) # Assuming %s is an FPU register for float type\n", reg, offset, reg);
            } else {
                out.printf("\tsw   %s, %d($fp)\n", reg, offset);
            }
        }
    }
}
