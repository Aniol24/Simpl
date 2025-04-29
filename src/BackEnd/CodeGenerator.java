package BackEnd;

import FrontEnd.Syntax.TreeNode;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class CodeGenerator {
    private int tempCount = 0;
    private int labelCount = 0;
    private final List<TACInstruction> code = new ArrayList<>();
    private final List<String[]> quads = new ArrayList<>();

    public List<TACInstruction> generate(TreeNode root) throws IOException {
        TreeNode inicial = root.getChildren().get(0);
        TreeNode firstFunc = inicial.getChildren().get(0);
        TreeNode funcsNode = inicial.getChildren().get(1);
        List<TreeNode> functions = new ArrayList<>();
        functions.add(firstFunc);
        collectFunctions(funcsNode, functions);

        for (TreeNode func : functions) {
            processFunction(func);
        }
        writeQuadruplesToFile();
        return code;
    }

    private void collectFunctions(TreeNode node, List<TreeNode> funcs) {
        if (node.getChildren().isEmpty()) return;
        TreeNode first = node.getChildren().get(0);
        if ("EPSILON".equals(first.getValue())) return;
        funcs.add(first);
        collectFunctions(node.getChildren().get(1), funcs);
    }

    private void collectParamNodes(TreeNode node, List<TreeNode> out) {
        if (node == null) return;
        String v = node.getValue();
        switch (v) {
            case "PARAMS":

                out.add(node.getChildren().get(0));
                collectParamNodes(node.getChildren().get(1), out);
                break;

            case "NEXT_PARAM":
                if (node.getChildren().isEmpty()) {
                    break;
                }
                if ("EPSILON".equals(node.getChildren().get(0).getValue())) {
                    break;
                }
                if (node.getChildren().size() >= 3) {
                    out.add(node.getChildren().get(1));
                    collectParamNodes(node.getChildren().get(2), out);
                }
                break;

            default:
                break;
        }
    }

    private List<String> extractParamNames(TreeNode paramDef) {
        List<String> names = new ArrayList<>();
        if (paramDef.getChildren().size() < 2) return names;
        TreeNode params = paramDef.getChildren().get(1);
        List<TreeNode> paramNodes = new ArrayList<>();
        collectParamNodes(params, paramNodes);
        for (TreeNode p : paramNodes) {
            names.add(p.getChildren().get(2).getAttribute());
        }
        return names;
    }

    private void processFunction(TreeNode function) {
        String fnName = function.getChildren().get(1)
                .getChildren().get(0)
                .getAttribute();

        emit("label", null, null, fnName);


        TreeNode paramDef = function.getChildren().get(2);
        List<String> formals = extractParamNames(paramDef);
        for (int i = 0; i < formals.size(); i++) {
            String paramName = formals.get(i);
            String paramSrc   = "param" + (i + 1);
            emit("=", paramSrc, null, paramName);
        }
        int quadStart = quads.size();

        function.getChildren().stream()
                .filter(n -> "CODE".equals(n.getValue()))
                .findFirst()
                .ifPresent(this::processCode);

        boolean hasReturn = quads.stream()
                .anyMatch(q -> "return".equals(q[3]));
        if (!"main".equals(fnName) && !hasReturn) {
            String retVar = null;
            for (int i = quads.size() - 1; i >= quadStart; i--) {
                String[] q   = quads.get(i);
                String  op   = q[3];
                String  res  = q[0];
                if ("=".equals(op) && !res.isEmpty() && !res.startsWith("t")) {
                    retVar = res;
                    break;
                }
            }
            if (retVar == null) retVar = "0";
            emit("return", retVar, null, null);
        }

        emit("label", null, null, "end_" + fnName);
    }

    private void processCode(TreeNode node) {
        while (!node.getChildren().isEmpty()) {
            TreeNode inst = node.getChildren().get(0);
            if ("EPSILON".equals(inst.getValue())) break;
            generateInstruction(inst);
            node = node.getChildren().get(1);
        }
    }

    private void generateInstruction(TreeNode inst) {
        if (inst.getChildren().isEmpty()) return;
        String kind = inst.getChildren().get(0).getValue();
        switch (kind) {
            case "DECLARATION":
                generateDeclaration(inst.getChildren().get(0));
                break;
            case "CONDITIONAL":
                generateIf(inst.getChildren().get(0));
                break;
            case "ITERATIVE":
                generateWhile(inst.getChildren().get(0));
                break;
            case "ID": {
                TreeNode prime = inst.getChildren().get(1);
                if (!prime.getChildren().isEmpty()) {
                    String action = prime.getChildren().get(0).getValue();
                    if ("ASSIGNMENT".equals(action)) {
                        generateAssignment(inst);
                    } else if ("FUNCTION_CALL".equals(action)) {
                        generateFunctionCall(inst);
                    }
                }
                break;
            }
            case "RETURN_STATEMENT": {
                TreeNode returnStmt = inst.getChildren().get(0);
                TreeNode optEval = returnStmt.getChildren().get(1);
                if (!optEval.getChildren().isEmpty()) {
                    TreeNode evalNode = optEval.getChildren().get(0);
                    generateReturn(evalNode);
                } else {
                    emit("return", null, null, null);
                }
                break;
            }
            default:
                break;
        }
    }

    private void generateWhile(TreeNode iterNode) {
        TreeNode whileLoop = iterNode.getChildren().stream()
                .filter(c -> "WHILE_LOOP".equals(c.getValue()))
                .findFirst().orElse(iterNode);
        String startLbl = newLabel();
        String endLbl = newLabel();
        emit("label", null, null, startLbl);
        TreeNode evalNode = whileLoop.getChildren().get(2);
        String condTemp = generateCondition(evalNode);
        emit("ifFalse", condTemp, null, endLbl);
        TreeNode codeNode = whileLoop.getChildren().get(5);
        processCode(codeNode);
        emit("goto", null, null, startLbl);
        emit("label", null, null, endLbl);
    }

    private void generateIf(TreeNode condNode) {
        TreeNode ifStmt = condNode.getChildren().stream()
                .filter(c -> "IF_STATEMENT".equals(c.getValue()))
                .findFirst().orElse(null);
        if (ifStmt == null) return;

        TreeNode evalNode = ifStmt.getChildren().stream()
                .filter(c -> "EVAL".equals(c.getValue()))
                .findFirst().orElse(null);
        String condTemp = generateCondition(evalNode);

        TreeNode elseBlock = condNode.getChildren().stream()
                .filter(c -> "ELSE_BLOCK".equals(c.getValue()))
                .findFirst().orElse(null);
        boolean hasElse = elseBlock != null
                && !elseBlock.getChildren().isEmpty()
                && !"EPSILON".equals(elseBlock.getChildren().get(0).getValue());

        if (hasElse) {
            String elseLbl = newLabel();
            String endLbl  = newLabel();

            emit("ifFalse", condTemp, null, elseLbl);
            ifStmt.getChildren().stream()
                    .filter(c -> "CODE".equals(c.getValue()))
                    .findFirst().ifPresent(this::processCode);
            emit("goto", null, null, endLbl);

            emit("label", null, null, elseLbl);
            elseBlock.getChildren().stream()
                    .filter(c -> "CODE".equals(c.getValue()))
                    .findFirst().ifPresent(this::processCode);

            emit("label", null, null, endLbl);
        } else {
            String endLbl = newLabel();
            emit("ifFalse", condTemp, null, endLbl);
            ifStmt.getChildren().stream()
                    .filter(c -> "CODE".equals(c.getValue()))
                    .findFirst().ifPresent(this::processCode);
            emit("label", null, null, endLbl);
        }
    }


    private String generateCondition(TreeNode eval) {
        if (eval == null) return "";
        String left = generateExpr(eval.getChildren().get(0));
        TreeNode prime = eval.getChildren().get(1);
        String op = prime.getChildren().get(0).getValue();
        String right = generateExpr(prime.getChildren().get(1));
        String temp = newTemp();
        emit(op, left, right, temp);
        return temp;
    }

    private TreeNode findCallFactor(TreeNode node) {
        if ("FACTOR".equals(node.getValue())
                && node.getChildren().size()>1
                && "FACTOR_PRIME".equals(node.getChildren().get(1).getValue())) {
            TreeNode fp = node.getChildren().get(1);
            if (!fp.getChildren().isEmpty()
                    && "FUNCTION_CALL".equals(fp.getChildren().get(0).getValue())) {
                return node;
            }
        }
        for (TreeNode ch: node.getChildren()) {
            TreeNode found = findCallFactor(ch);
            if (found!=null) return found;
        }
        return null;
    }

    private void emitParams(TreeNode argList) {
        for (TreeNode c: argList.getChildren()) {
            if ("EVAL".equals(c.getValue())) {
                String place = generateExpr(c.getChildren().get(0));
                emit("param", place, null, null);
            }
        }
    }

    private void generateDeclaration(TreeNode decl) {
        String var  = decl.getChildren().get(2).getAttribute();
        TreeNode init = decl.getChildren().get(3);

        if (!init.getChildren().isEmpty()
                && !"EPSILON".equals(init.getChildren().get(0).getValue())) {

            TreeNode evalNode = init.getChildren().get(1);

            TreeNode factor = findCallFactor(evalNode);
            if (factor != null) {
                String fn = factor.getChildren().get(0).getAttribute();
                TreeNode funcCall = factor
                        .getChildren().get(1)
                        .getChildren().get(0);

                TreeNode argList = funcCall.getChildren().get(1);

                emitParams(argList);

                String tmp = newTemp();
                emit("call", fn,    null, tmp);
                emit("=",    tmp,   null, var);
                return;
            }

            TreeNode expr = evalNode.getChildren().get(0);
            String place = generateExpr(expr);
            emit("=", place, null, var);
        }
    }

    private void generateAssignment(TreeNode inst) {
        String var = inst.getChildren().get(0).getAttribute();
        TreeNode assignmentNode = inst.getChildren().get(1)
                .getChildren().get(0);
        if (assignmentNode.getChildren().isEmpty()) return;

        String kind = assignmentNode.getChildren().get(0).getValue();
        switch (kind) {
            case "EQ": {
                TreeNode exprNode = assignmentNode.getChildren().get(1);

                TreeNode callFactor = findCallFactor(exprNode);
                if (callFactor != null) {
                    String fn = callFactor.getChildren().get(0).getAttribute();
                    TreeNode argList = callFactor
                            .getChildren().get(1)
                            .getChildren().get(0)
                            .getChildren().get(0);

                    for (TreeNode c : argList.getChildren()) {
                        if ("EVAL".equals(c.getValue())) {
                            String place = generateExpr(c.getChildren().get(0));
                            emit("param", place, null, null);
                        }
                    }

                    String temp = newTemp();
                    emit("call", fn, null, temp);
                    emit("=", temp, null, var);
                    return;
                }

                String place = generateExpr(exprNode.getChildren().get(0));
                emit("=", place, null, var);
                break;
            }

            case "INC": {
                String tInc = newTemp();
                emit("SUM", var, "1", tInc);
                emit("=", tInc, null, var);
                break;
            }

            default:
                break;
        }
    }

    private void generateFunctionCall(TreeNode inst) {
        String fn = inst.getChildren().get(0).getAttribute();

        TreeNode callNode = inst.getChildren()
                .get(1)
                .getChildren().get(0);

        TreeNode argList = callNode.getChildren().get(1);


        for (TreeNode child : argList.getChildren()) {
            if ("EVAL".equals(child.getValue())) {
                TreeNode expr = child.getChildren().get(0);
                emit("param", generateExpr(expr), null, null);
            }
        }

        emit("call", fn, null, null);
    }

    private void generateReturn(TreeNode evalNode) {
        String place = generateExpr(evalNode);
        emit("return", place, null, null);
    }

    private String generateExpr(TreeNode expr) {
        String place = expr.getChildren().isEmpty()
                ? ""
                : generateTerm(expr.getChildren().get(0));
        TreeNode prime = expr.getChildren().size() > 1 ? expr.getChildren().get(1) : null;
        while (prime != null
                && !prime.getChildren().isEmpty()
                && !"EPSILON".equals(prime.getChildren().get(0).getValue())) {
            String op = prime.getChildren().get(0).getValue();
            String right = generateTerm(prime.getChildren().get(1));
            String temp = newTemp();
            emit(op, place, right, temp);
            place = temp;
            prime = prime.getChildren().size() > 2 ? prime.getChildren().get(2) : null;
        }
        return place;
    }

    private String generateTerm(TreeNode term) {
        String place = generateFactor(term.getChildren().get(0));
        TreeNode prime = term.getChildren().size() > 1 ? term.getChildren().get(1) : null;
        while (prime != null
                && !prime.getChildren().isEmpty()
                && !"EPSILON".equals(prime.getChildren().get(0).getValue())) {
            String op = prime.getChildren().get(0).getValue();
            String right = generateFactor(prime.getChildren().get(1));
            String temp = newTemp();
            emit(op, place, right, temp);
            place = temp;
            prime = prime.getChildren().size() > 2 ? prime.getChildren().get(2) : null;
        }
        return place;
    }

    private String generateFactor(TreeNode f) {
        // NUEVO: si no hay hijos, devolvemos cadena vacía
        if (f.getChildren() == null || f.getChildren().isEmpty()) {
            return "";
        }

        // 1) Detección de llamada a función anidada
        if ("FACTOR".equals(f.getValue())
                && f.getChildren().size() > 1
                && "FACTOR_PRIME".equals(f.getChildren().get(1).getValue())) {
            TreeNode fp = f.getChildren().get(1);
            if (!fp.getChildren().isEmpty()
                    && "FUNCTION_CALL".equals(fp.getChildren().get(0).getValue())) {
                // …emitParams + call…
                TreeNode fc      = fp.getChildren().get(0);
                String   fn      = f.getChildren().get(0).getAttribute();
                TreeNode argList = fc.getChildren().get(1);
                emitParams(argList);
                String temp = newTemp();
                emit("call", fn, null, temp);
                return temp;
            }
        }

        // 2) Ahora ya sabemos que f tiene al menos un hijo
        TreeNode first = f.getChildren().get(0);
        String kind    = first.getValue();

        if ("LITERAL".equals(kind)) {
            String lit = first.getChildren().get(0).getAttribute();
            return lit.length()==1 ? "'" + lit + "'" : lit;
        }
        else if ("ID".equals(kind)) {
            return first.getAttribute();
        }
        else if ("PO".equals(kind)) {
            // ( EVAL )
            for (TreeNode ch : f.getChildren()) {
                if ("EVAL".equals(ch.getValue())) {
                    return generateExpr(ch.getChildren().get(0));
                }
            }
        }

        // 3) Caso genérico: deshacemos un nivel
        return generateFactor(first);
    }


    private void emit(String op, String arg1, String arg2, String result) {
        code.add(new TACInstruction(op, arg1, arg2, result));
        quads.add(new String[]{
                result != null ? result : "",
                arg1   != null ? arg1   : "",
                arg2   != null ? arg2   : "",
                op     != null ? op     : ""
        });
    }

    private void writeQuadruplesToFile() throws IOException {
        String dirPath = "src/Files/TAC/";
        File dir = new File(dirPath);
        if (!dir.exists()) dir.mkdirs();
        String filePath = dirPath + File.separator + "TAC.txt";
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            for (String[] q : quads) {
                pw.printf("Result: %s Arg1: %s Arg2: %s Op: %s%n",
                        q[0], q[1], q[2], q[3]);
            }
        }
    }

    private String newTemp() {
        return "t" + (tempCount++);
    }

    private String newLabel() {
        return "L" + (labelCount++);
    }
}