package BackEnd;

import FrontEnd.TAC.TACInstruction;
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

    public void generate(List<TACInstruction> code) throws Exception {
        Map<String,List<TACInstruction>> funcs = groupByFunction(code);

        // Recolectar literales float
        floatConstants = new LinkedHashMap<>();
        floatConstCount = 0;
        for (List<TACInstruction> body : funcs.values()) {
            for (TACInstruction ins : body) {
                if ("=".equals(ins.getOp())) {
                    String a1 = ins.getArg1();
                    if (a1 != null && a1.matches("^-?\\d+\\.\\d+$")
                            && !floatConstants.containsKey(a1)) {
                        floatConstants.put(a1, "LC" + (floatConstCount++));
                    }
                }
            }
        }

        // Preparar fichero de salida
        File dir = new File("out");
        if (!dir.exists()) dir.mkdirs();
        out = new PrintWriter(new File(dir, "program.s"));

        emitData();
        emitText(funcs);
        out.close();
    }

    private Map<String,List<TACInstruction>> groupByFunction(List<TACInstruction> code) {
        Map<String,List<TACInstruction>> map = new LinkedHashMap<>();
        String current = null;
        for (TACInstruction ins : code) {
            if ("label".equals(ins.getOp())) {
                String lbl = ins.getResult();
                if (current == null && lbl != null && !lbl.startsWith("end_")) {
                    current = lbl;
                    map.put(current, new ArrayList<>());
                } else if (current != null && lbl.equals("end_" + current)) {
                    current = null;
                } else if (current != null) {
                    map.get(current).add(ins);
                }
            } else if (current != null) {
                map.get(current).add(ins);
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
        out.println("\tjal main\n");

        // Emitimos main primero
        if (funcs.containsKey("main")) {
            emitFunction("main", funcs.get("main"));
            out.println();
        }
        // Luego el resto de funciones
        for (String fn : funcs.keySet()) {
            if (!"main".equals(fn)) {
                emitFunction(fn, funcs.get(fn));
                out.println();
            }
        }
    }

    private void emitFunction(String fn, List<TACInstruction> body) {
        this.currentFunction = fn;
        setupFrame(body);

        // Prologue
        out.println(fn + ":");
        out.printf("\taddi $sp, $sp, -%d\n", frameSize);
        out.printf("\tsw   $ra, %d($sp)\n", frameSize - 4);
        out.printf("\tsw   $fp, %d($sp)\n", frameSize - 8);
        out.printf("\taddi $fp, $sp, %d\n", frameSize);
        out.println();

        paramCount = 0;
        for (TACInstruction ins : body) {
            emitInstruction(ins);
        }

        // Epílogo unificado (sin volver a ajustar $sp)
        String exitLabel = fn + "_exit";
        out.println(exitLabel + ":");
        out.println("\tmove $sp, $fp");
        out.println("\tlw   $ra, -4($fp)");
        out.println("\tlw   $fp, -8($fp)");

        if ("main".equals(fn)) {
            out.println("\tli   $v0, 10");
            out.println("\tsyscall");
        } else {
            out.println("\tjr   $ra");
            out.println("\tnop");
        }
    }

    private void setupFrame(List<TACInstruction> body) {
        localOffset = new LinkedHashMap<>();
        varType     = new LinkedHashMap<>();
        Set<String> locals = new LinkedHashSet<>();

        // 1) Reunimos todos los resultados (skip labels L0, L1…)
        for (TACInstruction ins : body) {
            String res = ins.getResult();
            if (res != null
                    && !"label".equals(ins.getOp())
                    && !res.matches("^L\\d+$")) {
                locals.add(res);
                if ("=".equals(ins.getOp())) {
                    String a1 = ins.getArg1();
                    if (a1 != null && a1.matches("^'.'$"))
                        varType.put(res, "char");
                    else if (a1 != null && a1.matches("^-?\\d+\\.\\d+$"))
                        varType.put(res, "float");
                    else
                        varType.put(res, "int");
                }
            }
        }

        // 2) Asignamos offset (4 bytes cada uno)
        int off = 8;
        for (String v : locals) {
            off += 4;
            localOffset.put(v, -off);
        }
        // 3) Redondeamos frameSize a múltiplo de 8
        frameSize = ((off + 7) / 8) * 8;
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
                if ("float".equals(type)) {
                    int offF = localOffset.get(a1);
                    out.printf("\tlwc1 $f12, %d($fp)\n", offF);
                    out.println("\tli   $v0, 2");
                    out.println("\tsyscall");
                } else if ("char".equals(type)) {
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

    private void loadTo(String var) {
        if (var == null) return;
        if (var.matches("param\\d+")) {
            int idx = Integer.parseInt(var.substring(5)) - 1;
            out.printf("\tmove $t0, $a%d\n", idx);
        } else if (var.matches("^-?\\d+$")) {
            out.printf("\tli   $t0, %s\n", var);
        } else if (var.matches("^'.'$")) {
            out.printf("\tli   $t0, %d\n", (int)var.charAt(1));
        } else {
            out.printf("\tlw   $t0, %d($fp)\n",
                    localOffset.getOrDefault(var, 0));
        }
    }

    private void loadToInto(String var, String reg) {
        if (var == null) return;
        if (var.matches("param\\d+")) {
            int idx = Integer.parseInt(var.substring(5)) - 1;
            out.printf("\tmove %s, $a%d\n", reg, idx);
        } else if (var.matches("^-?\\d+$")) {
            out.printf("\tli   %s, %s\n", reg, var);
        } else {
            out.printf("\tlw   %s, %d($fp)\n",
                    reg, localOffset.getOrDefault(var, 0));
        }
    }

    private void storeLocal(String var) {
        out.printf("\tsw   $t0, %d($fp)\n",
                localOffset.getOrDefault(var, 0));
    }

    private void storeLocalTo(String var, String reg) {
        out.printf("\tsw   %s, %d($fp)\n",
                reg, localOffset.getOrDefault(var, 0));
    }
}
