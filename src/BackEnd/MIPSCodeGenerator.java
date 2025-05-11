package BackEnd;

import FrontEnd.TAC.TACInstruction;
import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class MIPSCodeGenerator {
    private PrintWriter out;
    private Map<String,Integer> localOffset;
    private int frameSize;
    private int paramCount;

    public void generate(List<TACInstruction> code) throws Exception {
        Map<String,List<TACInstruction>> funcs = groupByFunction(code);
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
                    continue;
                }
                if (current != null && lbl.equals("end_" + current)) {
                    current = null;
                    continue;
                }
            }
            if (current != null) {
                map.get(current).add(ins);
            }
        }
        return map;
    }

    private void emitData() {
        out.println("\t.data\n");
    }

    private void emitText(Map<String,List<TACInstruction>> funcs) {
        out.println("\t.text");
        out.println("\t.globl main\n");

        if (funcs.containsKey("main")) {
            emitFunction("main", funcs.get("main"));
            out.println();
        }

        for (String fn : funcs.keySet()) {
            if ("main".equals(fn)) continue;
            emitFunction(fn, funcs.get(fn));
            out.println();
        }
    }

    private void emitFunction(String fn, List<TACInstruction> body) {
        setupFrame(body);

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
        Set<String> locals = new LinkedHashSet<>();
        for (TACInstruction ins : body) {
            if ("=".equals(ins.getOp())) {
                String res = ins.getResult();
                if (res != null && !res.matches("t\\d+")) {
                    locals.add(res);
                }
            }
        }
        int offset = 8;
        for (String var : locals) {
            offset += 4;
            localOffset.put(var, -offset);
        }
        frameSize = ((offset + 7) / 8) * 8;
    }

    private void emitInstruction(TACInstruction ins) {
        String op = ins.getOp();
        String a1 = ins.getArg1();
        String a2 = ins.getArg2();
        String res = ins.getResult();

        switch (op) {
            case "label":
                if (res != null && res.matches("L\\d+")) {
                    out.println(res + ":");
                }
                break;
            case "=":
                loadTo("$t0", a1);
                storeLocal(res, "$t0");
                break;
            case "SUM":
                loadTo("$t0", a1);
                loadTo("$t1", a2);
                out.println("\tadd  $t2, $t0, $t1");
                storeLocal(res, "$t2");
                break;
            case "SUB":
                loadTo("$t0", a1);
                loadTo("$t1", a2);
                out.println("\tsub  $t2, $t0, $t1");
                storeLocal(res, "$t2");
                break;
            case "MULT":
                loadTo("$t0", a1);
                loadTo("$t1", a2);
                out.println("\tmul  $t2, $t0, $t1");
                storeLocal(res, "$t2");
                break;
            case "DIV":
                loadTo("$t0", a1);
                loadTo("$t1", a2);
                out.println("\tdiv  $t2, $t0, $t1");
                storeLocal(res, "$t2");
                break;
            case "LOWER":
                loadTo("$t0", a1);
                loadTo("$t1", a2);
                out.println("\tslt  $t2, $t0, $t1");
                storeLocal(res, "$t2");
                break;
            case "EQ":
            case "EQUALS":
                loadTo("$t0", a1);
                loadTo("$t1", a2);
                out.println("\tseq  $t2, $t0, $t1");
                storeLocal(res, "$t2");
                break;
            case "param":
                loadTo("$t0", a1);
                if (paramCount < 4) {
                    out.printf("\tmove $a%d, $t0\n", paramCount);
                } else {
                    out.println("\taddi $sp, $sp, -4");
                    out.println("\tsw   $t0, 0($sp)");
                }
                paramCount++;
                break;
            case "call":
                out.println("\tjal  " + a1);
                if (res != null) {
                    out.println("\tmove $t0, $v0");
                    storeLocal(res, "$t0");
                }
                if (paramCount > 4) {
                    out.printf("\taddi $sp, $sp, %d\n", (paramCount - 4) * 4);
                }
                paramCount = 0;
                break;
            case "return":
                loadTo("$v0", a1);
                out.println("\tlw   $ra, -4($fp)");
                out.println("\tlw   $fp, -8($fp)");
                out.printf("\taddi $sp, $sp, %d\n", frameSize);
                out.println("\tjr   $ra");
                break;
            case "ifFalse":
                loadTo("$t0", a1);
                out.println("\tbeq  $t0, $zero, " + res);
                break;
            case "goto":
                out.println("\tj    " + res);
                break;
            default:
                out.printf("\t# UNHANDLED: %s %s,%s -> %s\n", op, a1, a2, res);
        }
    }

    private void loadTo(String reg, String opnd) {
        if (opnd == null) return;
        if (opnd.matches("^-?\\d+$")) {
            out.printf("\tli   %s, %s\n", reg, opnd);
        } else if (opnd.matches("param(\\d+)$")) {
            int idx = Integer.parseInt(opnd.substring(5)) - 1;
            out.printf("\tmove %s, $a%d\n", reg, idx);
        } else if (opnd.matches("t(\\d+)$")) {
            out.printf("\tmove %s, $%s\n", reg, opnd);
        } else {
            int off = localOffset.getOrDefault(opnd, 0);
            out.printf("\tlw   %s, %d($fp)\n", reg, off);
        }
    }

    private void storeLocal(String var, String reg) {
        if (var.matches("t(\\d+)$")) {
            out.printf("\tmove $%s, %s\n", var, reg);
        } else {
            int off = localOffset.getOrDefault(var, 0);
            out.printf("\tsw   %s, %d($fp)\n", reg, off);
        }
    }
}
