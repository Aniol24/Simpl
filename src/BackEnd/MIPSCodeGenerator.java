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
        for (List<TACInstruction> body : funcs.values()) {
            for (TACInstruction ins : body) {
                if ("=".equals(ins.getOp())) {
                    String arg1Val = ins.getArg1();
                    if (arg1Val != null && arg1Val.matches("^-?\\d+\\.\\d+$")
                            && !floatConstants.containsKey(arg1Val)) {
                        floatConstants.put(arg1Val, "LC" + (floatConstCount++));
                    }
                }
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
        
        Symbol functionSymbol = symbolTable.getGlobalScope().lookupSymbol(fnName);
        if (functionSymbol == null || !functionSymbol.isFunction()) {
            System.err.println("MIPS Gen Error: Function symbol not found for " + fnName);
            setupFrame(body, null); 
        } else {
            setupFrame(body, functionSymbol);
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

        switch (op) {
            case "label":
                out.println(res + ":");
                break;

            case "param":
                loadTo(a1);
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
                if (res != null) {
                    out.println("\tmove $t0, $v0");
                    storeLocal(res);
                }
                if (paramCount > 4) {
                    out.printf("\taddi $sp, $sp, %d\n", (paramCount - 4) * 4);
                }
                paramCount = 0;
                break;

            case "=":
                if (a1 != null && a1.matches("^'.'$")) {
                    out.printf("\tli   $t0, %d\n", (int)a1.charAt(1));
                } else if (a1 != null && floatConstants.containsKey(a1)) {
                    String lbl = floatConstants.get(a1);
                    out.printf("\tla   $t0, %s\n", lbl);
                    out.println("\tlwc1 $f0, 0($t0)");
                } else {
                    loadTo(a1);
                }
                storeLocal(res);
                break;

            case "SUM":
                loadTo(a1);
                loadToInto(a2, "$t1");
                out.println("\tadd  $t2, $t0, $t1");
                storeLocalTo(res, "$t2");
                break;

            case "MULT":
                loadTo(a1);
                loadToInto(a2, "$t1");
                out.println("\tmul  $t2, $t0, $t1");
                storeLocalTo(res, "$t2");
                break;

            case "SUB":
                loadTo(a1);
                loadToInto(a2, "$t1");
                out.println("\tsub  $t2, $t0, $t1");
                storeLocalTo(res, "$t2");
                break;

            case "DIV":
                loadTo(a1);
                loadToInto(a2, "$t1");
                out.println("\tdiv  $t0, $t1");
                out.println("\tmflo $t2");        // <-- Cociente aquí
                storeLocalTo(res, "$t2");
                break;

            case "LOWER":
                loadTo(a1);
                loadToInto(a2, "$t1");
                out.println("\tslt  $t2, $t0, $t1");
                storeLocalTo(res, "$t2");
                break;
            
            case "LOWER_EQUAL":
                loadTo(a1);
                loadToInto(a2, "$t1");
                out.println("\tsle  $t2, $t0, $t1");
                storeLocalTo(res, "$t2");
                break;

            case "NOT":
                loadTo(a1);
                out.println("\tseq  $t2, $t0, $zero");
                storeLocalTo(res, "$t2");
                break;

            case "AND":
                loadTo(a1);
                out.println("\tsne  $t0, $t0, $zero");
                loadToInto(a2, "$t1");
                out.println("\tsne  $t1, $t1, $zero");
                out.println("\tand  $t2, $t0, $t1");
                storeLocalTo(res, "$t2");
                break;

            case "GREATER":
                loadTo(a1);
                loadToInto(a2, "$t1");
                out.println("\tslt  $t2, $t1, $t0");
                storeLocalTo(res, "$t2");
                break;

            case "GREATER_EQUAL":
                loadTo(a1);
                loadToInto(a2, "$t1");
                out.println("\tslt  $t2, $t0, $t1");
                out.println("\tseq  $t2, $t2, $zero");
                storeLocalTo(res, "$t2");
                break;

            case "EQUALS":
                loadTo(a1);
                loadToInto(a2, "$t1");
                out.println("\tseq  $t2, $t0, $t1");
                storeLocalTo(res, "$t2");
                break;

            case "NOT_EQUAL":
                loadTo(a1);
                loadToInto(a2, "$t1");
                out.println("\tsne  $t2, $t0, $t1");
                storeLocalTo(res, "$t2");
                break;

            case "ifFalse":
                loadTo(a1);
                out.println("\tbeq  $t0, $zero, " + res);
                break;

            case "goto":
                out.println("\tj    " + res);
                break;

            case "print":
                String type = varType.get(a1);
                if ("flt".equals(type)) {
                    int offF = localOffset.get(a1);
                    out.printf("\tlwc1 $f12, %d($fp)\n", offF);
                    out.println("\tli   $v0, 2");
                    out.println("\tsyscall");
                } else if ("chr".equals(type)) {
                    int offC = localOffset.get(a1);
                    out.printf("\tlb   $a0, %d($fp)\n", offC);
                    out.println("\tli   $v0, 11");
                    out.println("\tsyscall");
                } else {
                    loadTo(a1);
                    out.println("\tmove $a0, $t0");
                    out.println("\tli   $v0, 1");
                    out.println("\tsyscall");
                }
                break;

            case "return":
                if (ins.getArg1() != null) {
                    loadTo(ins.getArg1());
                    out.println("\tmove $v0, $t0");
                }
                out.println("\tj   " + currentFunction + "_exit");
                out.println("\tnop");
                break;

            default:
                out.printf("\t# UNHANDLED: %s %s,%s -> %s\n",
                        op, a1, a2, res);
        }
    }

    private void setupFrame(List<TACInstruction> body, Symbol functionSymbol) {
        localOffset = new LinkedHashMap<>();
        varType = new LinkedHashMap<>(); // To store types of TAC vars (temps, user vars, params)
        Set<String> frameVariables = new LinkedHashSet<>(); // All vars needing stack space

        // 1. Process parameters from functionSymbol
        if (functionSymbol != null && functionSymbol.isFunction() && functionSymbol.getParameters() != null) {
            List<Symbol> params = functionSymbol.getParameters();
            for (int i = 0; i < params.size(); i++) {
                Symbol paramSymbol = params.get(i);
                // The TAC generator assigns "paramN" to the actual param name: e.g., "n = param1"
                // So, 'n' will be treated as a local variable.
                // We still record the type of the formal parameter name.
                varType.put(paramSymbol.getName(), paramSymbol.getType());
                // frameVariables.add(paramSymbol.getName()); // It will be added if used as a result of "="
            }
        }

        // 2. Scan TAC to find all variables (results, and args if they are vars) and infer types
        for (TACInstruction ins : body) {
            String res = ins.getResult();
            String a1 = ins.getArg1();
            String a2 = ins.getArg2();

            // Add results that are not labels to frameVariables
            if (res != null && !"label".equals(ins.getOp()) && !res.matches("^L\\d+$")) {
                frameVariables.add(res);
                Symbol s = symbolTable.lookupSymbol(res); // Lookup in appropriate scope
                if (s != null && !s.isFunction()) {
                    varType.put(res, s.getType());
                } else if (varType.get(res) == null) { // Fallback for temps if not in symbol table
                    if ("=".equals(ins.getOp()) && a1 != null) {
                         if (a1.matches("^'.'$")) varType.put(res, "chr");
                         else if (a1.matches("^-?\\d+\\.\\d+$")) varType.put(res, "flt");
                         else if (a1.matches("^-?\\d+$")) varType.put(res, "int");
                         else { // If a1 is another variable
                             String typeOfA1 = varType.get(a1);
                             if (typeOfA1 != null) varType.put(res, typeOfA1);
                             else varType.put(res, "int"); // Default assumption
                         }
                    } else {
                        varType.put(res, "int"); // Default for other ops if type unknown
                    }
                }
            }

            // For arg1 and arg2, if they are variables and their type isn't known, try to find it.
            // This is mostly for ensuring varType map is complete for 'print' or other type-dependent ops.
            if (a1 != null && !a1.matches("^-?\\d+(\\.\\d+)?$") && !a1.matches("^'.'$") && !a1.startsWith("param") && !a1.matches("^L\\d+$")) {
                if (varType.get(a1) == null) {
                    Symbol s = symbolTable.lookupSymbol(a1);
                    if (s != null && !s.isFunction()) varType.put(a1, s.getType());
                    else varType.put(a1, "int"); // Default
                }
            }
            if (a2 != null && !a2.matches("^-?\\d+(\\.\\d+)?$") && !a2.matches("^'.'$") && !a2.startsWith("param") && !a2.matches("^L\\d+$")) {
                 if (varType.get(a2) == null) {
                    Symbol s = symbolTable.lookupSymbol(a2);
                    if (s != null && !s.isFunction()) varType.put(a2, s.getType());
                    else varType.put(a2, "int"); // Default
                }
            }
        }
        
        // 3. Assign offsets (4 bytes each for simplicity, assuming all are word-sized for now)
        int currentOffset = -8; // Start after saved $ra and $fp
        for (String varName : frameVariables) {
            currentOffset -= 4; // Allocate 4 bytes for each local/temp
            localOffset.put(varName, currentOffset);
        }

        // 4. Calculate total frameSize, rounded up to be multiple of 8 for alignment (optional but good practice)
        int totalBytesForVars = frameVariables.size() * 4;
        frameSize = 8 + totalBytesForVars; // 8 for $ra, $fp
        if (frameSize % 8 != 0) {
            frameSize = ((frameSize / 8) + 1) * 8;
        }
        if (frameSize < 8) frameSize = 8; // Ensure minimum size if no locals
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
            if ("flt".equals(type)) {
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
             if ("flt".equals(type)) {
                out.printf("\tswc1 %s, %d($fp) # Assuming %s is an FPU register for float type\n", reg, offset, reg);
            } else {
                out.printf("\tsw   %s, %d($fp)\n", reg, offset);
            }
        }
    }
}
