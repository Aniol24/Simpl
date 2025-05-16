package BackEnd;

import FrontEnd.TAC.TACInstruction;
import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class MIPSCodeGenerator {
    private String currentFunction = null;
    private PrintWriter out;
    private Map<String,Integer> localOffset;
    private Map<String,String> varType;        // "int", "char" o "float"
    private Map<String,String> floatConstants; // literales float a etiquetas
    private int floatConstCount;
    private int frameSize;
    private int paramCount;

    public void generate(List<TACInstruction> code) throws Exception {
        Map<String,List<TACInstruction>> funcs = groupByFunction(code);

        // recolectar literales float
        floatConstants = new LinkedHashMap<>();
        floatConstCount = 0;
        for (List<TACInstruction> body : funcs.values()) {
            for (TACInstruction ins : body) {
                if ("=".equals(ins.getOp())) {
                    String a1 = ins.getArg1();
                    if (a1 != null && a1.matches("^-?\\d+\\.\\d+$") && !floatConstants.containsKey(a1)) {
                        floatConstants.put(a1, "LC" + (floatConstCount++));
                    }
                }
            }
        }

        File dir = new File("out"); if (!dir.exists()) dir.mkdirs();
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
                    current = lbl; map.put(current, new ArrayList<>()); continue;
                }
                if (current != null && lbl.equals("end_" + current)) { current = null; continue; }
            }
            if (current != null) map.get(current).add(ins);
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
        out.println("\t.globl __start\n");
        out.println("__start:");
        out.println("\tjal main\n");

        if (funcs.containsKey("main")) { emitFunction("main", funcs.get("main")); out.println(); }
        for (String fn : funcs.keySet()) {
            if ("main".equals(fn)) continue;
            emitFunction(fn, funcs.get(fn)); out.println();
        }
    }

    private void emitFunction(String fn, List<TACInstruction> body) {
        this.currentFunction = fn;
        setupFrame(body);
        out.println(fn + ":");
        out.printf("\taddi $sp, $sp, -%d\n", frameSize);
        out.printf("\tsw   $ra, %d($sp)\n", frameSize - 4);
        out.printf("\tsw   $fp, %d($sp)\n", frameSize - 8);
        out.printf("\taddi $fp, $sp, %d\n", frameSize);
        out.println();

        paramCount = 0;
        for (TACInstruction ins : body) emitInstruction(ins);

        if ("main".equals(fn)) {
            out.println();
            out.println("\tmove $sp, $fp");
            out.printf("\tlw   $ra, -4($fp)\n");
            out.printf("\tlw   $fp, -8($fp)\n");
            out.printf("\taddi $sp, $sp, %d\n", frameSize);
            out.println("\tli   $v0, 10");
            out.println("\tsyscall\n");
        }
    }

    private void setupFrame(List<TACInstruction> body) {
        localOffset = new LinkedHashMap<>();
        varType     = new LinkedHashMap<>();
        Set<String> locals = new LinkedHashSet<>();
        for (TACInstruction ins : body) {
            if ("=".equals(ins.getOp())) {
                String res = ins.getResult();
                if (res != null && !res.matches("t\\d+")) {
                    locals.add(res);
                    String a1 = ins.getArg1();
                    if (a1 != null && a1.matches("^'.'$")) varType.put(res, "char");
                    else if (a1 != null && a1.matches("^-?\\d+\\.\\d+$")) varType.put(res, "float");
                    else varType.put(res, "int");
                }
            }
        }
        int off = 8;
        for (String v : locals) { off += 4; localOffset.put(v, -off); }
        frameSize = ((off + 7) / 8) * 8;
    }

    private void emitInstruction(TACInstruction ins) {
        String op = ins.getOp(), a1 = ins.getArg1(), a2 = ins.getArg2(), res = ins.getResult();
        switch (op) {
            case "label":
                if (res != null && res.matches("L\\d+")) out.println(res + ":");
                break;
            case "=":
                // asignación
                if (a1.matches("^'.'$")) {
                    int ascii = a1.charAt(1);
                    out.printf("\tli   $t0, %d     # '%c'\n", ascii, ascii);
                } else if (floatConstants.containsKey(a1)) {
                    String lbl = floatConstants.get(a1);
                    out.printf("\tla   $t0, %s\n", lbl);
                    out.println("\tlwc1 $f0, 0($t0)");
                } else {
                    loadTo(a1);
                }
                storeLocal(res);
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
                    loadTo(a1); // en $t0
                    out.println("\tmove $a0, $t0");
                    out.println("\tli   $v0, 1");
                    out.println("\tsyscall");
                }
                break;
            case "return":
                // manejo return
                String rt = varType.get(res);
                if ("float".equals(rt)) {
                    int offF = localOffset.get(res);
                    out.printf("\tlwc1 $f0, %d($fp)\n", offF);
                } else {
                    loadTo(res);
                    out.println("\tmove $v0, $t0");
                }
                if (!"main".equals(currentFunction)) {
                    out.println("\tlw   $ra, -4($fp)");
                    out.println("\tlw   $fp, -8($fp)");
                    out.printf("\taddi $sp, $sp, %d\n", frameSize);
                    out.println("\tjr   $ra");
                }
                break;
            default:
                out.printf("\t# UNHANDLED: %s %s,%s -> %s\n", op, a1, a2, res);
        }
    }

    private void loadTo(String var) {
        if (var == null) return;
        if (var.matches("^-?\\d+$")) {
            out.printf("\tli   $t0, %s\n", var);
        } else if (var.matches("^'.'$")) {
            int ascii = var.charAt(1);
            out.printf("\tli   $t0, %d     # '%c'\n", ascii, ascii);
        } else {
            int off = localOffset.getOrDefault(var, 0);
            if ("char".equals(varType.get(var))) out.printf("\tlb   $t0, %d($fp)   # char %s\n", off, var);
            else out.printf("\tlw   $t0, %d($fp)   # int %s\n", off, var);
        }
    }

    private void storeLocal(String var) {
        if (var == null) return;
        int off = localOffset.getOrDefault(var, 0);
        String type = varType.get(var);
        if ("char".equals(type)) out.printf("\tsb   $t0, %d($fp)   # char %s\n", off, var);
        else if ("float".equals(type)) out.printf("\tswc1 $f0, %d($fp)   # float %s\n", off, var);
        else out.printf("\tsw   $t0, %d($fp)   # int %s\n", off, var);
    }
}