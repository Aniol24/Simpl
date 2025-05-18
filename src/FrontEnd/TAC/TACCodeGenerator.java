package FrontEnd.TAC;

import FrontEnd.Syntax.TreeNode;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.HashMap;

public class TACCodeGenerator {
    private int tempCount = 0;
    private int labelCount = 0;
    private final List<TACInstruction> code = new ArrayList<>();
    private final List<String[]> quads = new ArrayList<>();

    private final TreeNode root;

    public TACCodeGenerator(TreeNode root) {
        this.root = root;
    }

    // Operator precedences: relational/equality > boolean
    private static final Map<String,Integer> PRECEDENCE = new HashMap<>();
    static {
        PRECEDENCE.put("LOWER", 2);
        PRECEDENCE.put("LOWER_EQUAL", 2);
        PRECEDENCE.put("GREATER", 2);
        PRECEDENCE.put("GREATER_EQUAL", 2);
        PRECEDENCE.put("EQUALS", 2);
        PRECEDENCE.put("NOT_EQUAL", 2);
        PRECEDENCE.put("AND", 1);
        PRECEDENCE.put("OR", 1);
    }

    public void generate() {
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
    }

    private void processCode(TreeNode node) {
        while (!node.getChildren().isEmpty()) {
            TreeNode inst = node.getChildren().get(0);
            if ("EPSILON".equals(inst.getValue())) break;
            generateInstruction(inst);
            node = node.getChildren().get(1);
        }
    }

    private void generateFor(TreeNode forLoopNode) {
        // 1) Inicialització: int -> i = 0
        TreeNode initDecl = forLoopNode.getChildren().get(2);
        generateDeclaration(initDecl);

        // 2) Etiqueta d'inici de condició
        String startLbl = newLabel();
        emit("label", null, null, startLbl);

        // 3) Condició
        TreeNode condNode = forLoopNode.getChildren().get(4);
        String condTemp  = generateEvalExpr(condNode);
        String endLbl    = newLabel();
        emit("ifFalse", condTemp, null, endLbl);

        // 4) Cos del for
        TreeNode codeNode = forLoopNode.getChildren().get(10);
        processCode(codeNode);

        // 5) Increment: i++ (o i--, o i = i + k)
        TreeNode idNode         = forLoopNode.getChildren().get(6);
        TreeNode assignmentNode = forLoopNode.getChildren().get(7);
        String   varName        = idNode.getAttribute();
        String   kind           = assignmentNode.getChildren().get(0).getValue();

        switch (kind) {
            case "INC": {
                String tmp = newTemp();
                emit("SUM", varName, "1", tmp);
                emit("=", tmp, null, varName);
                break;
            }
            case "DEC": {
                String tmp = newTemp();
                emit("SUB", varName, "1", tmp);
                emit("=", tmp, null, varName);
                break;
            }
            case "EQ": {
                // Per a un for del tipus i = i + k
                TreeNode exprNode = assignmentNode.getChildren().get(1);
                String rhs = generateExpr(exprNode);
                emit("=", rhs, null, varName);
                break;
            }
            default:
                System.err.println("Increase type not supported on For Loop: " + kind);
        }

        // 6) Torna a la condició
        emit("goto", null, null, startLbl);

        // 7) Etiqueta de sortida
        emit("label", null, null, endLbl);
    }

    private void generateUntil(TreeNode untilNode) {
        // 1) Etiqueta d'inici del bucle
        String startLbl = newLabel();
        emit("label", null, null, startLbl);

        // 2) Cos: el bloc CODE està a child[2]
        TreeNode codeNode = untilNode.getChildren().get(2);
        processCode(codeNode);

        // 3) Condició: el node EVAL està a child[6]
        TreeNode evalNode = untilNode.getChildren().get(6);
        String condTemp = generateEvalExpr(evalNode);

        // 4) Si la condició és falsa, repetir: ifFalse condTemp goto startLbl
        emit("ifFalse", condTemp, null, startLbl);
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
                TreeNode loopTypeNode = inst.getChildren().get(0).getChildren().get(0);
                if ("WHILE_LOOP".equals(loopTypeNode.getValue())) {
                    generateWhile(loopTypeNode);
                } else if ("FOR_LOOP".equals(loopTypeNode.getValue())) {
                    generateFor(loopTypeNode);
                } else if ("UNTIL_LOOP".equals(loopTypeNode.getValue())) {
                    generateUntil(loopTypeNode);
                }
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
                if (!optEval.getChildren().isEmpty() && !"EPSILON".equals(optEval.getChildren().get(0).getValue())) {
                    TreeNode evalNode = optEval.getChildren().get(0);
                    generateReturn(evalNode);
                } else {
                    emit("return", null, null, null); // Return sense valor
                }
                break;
            }
            default:
                break;
        }
    }



    private void generateWhile(TreeNode whileLoopNode) {
        String startLbl = newLabel();
        String endLbl = newLabel();
        emit("label", null, null, startLbl);

        TreeNode evalNode = whileLoopNode.getChildren().get(2); // WHILE PO EVAL PT START CODE END
        String condTemp = generateEvalExpr(evalNode);
        emit("ifFalse", condTemp, null, endLbl);

        TreeNode codeNode = whileLoopNode.getChildren().get(5);
        processCode(codeNode);
        emit("goto", null, null, startLbl);
        emit("label", null, null, endLbl);
    }

    private void generateIf(TreeNode condNode) {
        TreeNode ifStmt = condNode.getChildren().stream()
                .filter(c -> "IF_STATEMENT".equals(c.getValue()))
                .findFirst().orElse(null);
        if (ifStmt == null) return;

        TreeNode evalNode = ifStmt.getChildren().stream() // IF PO EVAL PT START CODE END
                .filter(c -> "EVAL".equals(c.getValue()))
                .findFirst().orElse(null);
        String condTemp = generateEvalExpr(evalNode);

        TreeNode elifBlocks = condNode.getChildren().stream()
                .filter(c -> "ELIF_BLOCKS".equals(c.getValue()))
                .findFirst().orElse(null);

        TreeNode elseBlock = condNode.getChildren().stream()
                .filter(c -> "ELSE_BLOCK".equals(c.getValue()))
                .findFirst().orElse(null);

        String nextLabel = newLabel();
        String endLabel = newLabel();

        emit("ifFalse", condTemp, null, nextLabel);
        ifStmt.getChildren().stream()
                .filter(c -> "CODE".equals(c.getValue()))
                .findFirst().ifPresent(this::processCode);
        emit("goto", null, null, endLabel);

        emit("label", null, null, nextLabel);

        // ELIF blocks
        TreeNode currentElif = elifBlocks;
        while (currentElif != null && !currentElif.getChildren().isEmpty() && !"EPSILON".equals(currentElif.getChildren().get(0).getValue())) {
            // ELIF_BLOCKS -> ELIF PO EVAL PT START CODE END ELIF_BLOCKS
            TreeNode elifEvalNode = currentElif.getChildren().get(2);
            String elifCondTemp = generateEvalExpr(elifEvalNode);

            String nextElifOrElseLabel = newLabel();
            emit("ifFalse", elifCondTemp, null, nextElifOrElseLabel);

            TreeNode elifCodeNode = currentElif.getChildren().get(5);
            processCode(elifCodeNode);
            emit("goto", null, null, endLabel);

            emit("label", null, null, nextElifOrElseLabel);
            currentElif = currentElif.getChildren().get(7); // Següents ELIF_BLOCKS
        }

        // ELSE block
        boolean hasElse = elseBlock != null
                && !elseBlock.getChildren().isEmpty()
                && !"EPSILON".equals(elseBlock.getChildren().get(0).getValue());

        if (hasElse) {
            // ELSE START CODE END
            elseBlock.getChildren().stream()
                    .filter(c -> "CODE".equals(c.getValue()))
                    .findFirst().ifPresent(this::processCode);
        }
        emit("label", null, null, endLabel);
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

    private String generateEvalExpr(TreeNode evalNode) {
        if (evalNode == null || evalNode.getChildren().isEmpty()) {
            System.err.println("Error: Empty EVAL node in generateEvalExpr.");
            return newTemp();
        }

        List<TreeNode> exprNodes = new ArrayList<>();
        List<String>    ops       = new ArrayList<>();
        exprNodes.add(evalNode.getChildren().get(0));
        TreeNode ep = evalNode.getChildren().size() > 1
                ? evalNode.getChildren().get(1)
                : null;
        while (ep != null
                && !ep.getChildren().isEmpty()
                && !"EPSILON".equals(ep.getChildren().get(0).getValue())) {
            ops.add(ep.getChildren().get(0).getValue());
            exprNodes.add(ep.getChildren().get(1));
            ep = ep.getChildren().size() > 2
                    ? ep.getChildren().get(2)
                    : null;
        }

        List<Object> outputQueue = new ArrayList<>();
        Deque<String> opStack    = new ArrayDeque<>();
        for (int i = 0; i < ops.size(); i++) {
            outputQueue.add(exprNodes.get(i));
            String op = ops.get(i);
            while (!opStack.isEmpty()
                    && PRECEDENCE.get(opStack.peek()) >= PRECEDENCE.get(op)) {
                outputQueue.add(opStack.pop());
            }
            opStack.push(op);
        }
        outputQueue.add(exprNodes.get(ops.size()));
        while (!opStack.isEmpty()) {
            outputQueue.add(opStack.pop());
        }

        Deque<String> evalStack = new ArrayDeque<>();
        for (Object token : outputQueue) {
            if (token instanceof TreeNode) {
                String val = generateExpr((TreeNode) token);
                evalStack.push(val);
            } else {
                String sym   = (String) token;
                String right = evalStack.pop();
                String left  = evalStack.pop();
                String tacOp = mapBooleanOp(sym);
                String tmp   = newTemp();
                emit(tacOp, left, right, tmp);
                evalStack.push(tmp);
            }
        }
        return evalStack.pop();
    }

    private String mapBooleanOp(String tok) {
        switch (tok) {
            case "AND":         return "&&";
            case "OR":          return "||";
            case "EQUALS":      return "==";
            case "NOT_EQUAL":   return "!=";
            case "LOWER":       return "<";
            case "LOWER_EQUAL": return "<=";
            case "GREATER":     return ">";
            case "GREATER_EQUAL":return ">=";
            default:
                throw new RuntimeException("Unknown operator: " + tok);
        }
    }


    private void emitParams(TreeNode argListNode) {
        if (argListNode == null || argListNode.getChildren().isEmpty() || "EPSILON".equals(argListNode.getChildren().get(0).getValue())) {
            return; // No té arguments
        }
        // ARG_LIST -> EVAL NEXT_ARG
        TreeNode evalNode = argListNode.getChildren().get(0);
        String place = generateEvalExpr(evalNode);
        emit("param", place, null, null);

        if (argListNode.getChildren().size() > 1) {
            TreeNode nextArgNode = argListNode.getChildren().get(1);
            emitNextParams(nextArgNode);
        }
    }

    private void emitNextParams(TreeNode nextArgNode) {
        if (nextArgNode == null || nextArgNode.getChildren().isEmpty() || "EPSILON".equals(nextArgNode.getChildren().get(0).getValue())) {
            return;
        }
        // NEXT_ARG -> COMA EVAL NEXT_ARG
        TreeNode evalNode = nextArgNode.getChildren().get(1);
        String place = generateEvalExpr(evalNode);
        emit("param", place, null, null);

        if (nextArgNode.getChildren().size() > 2) {
            TreeNode nextNextArgNode = nextArgNode.getChildren().get(2);
            emitNextParams(nextNextArgNode);
        }
    }

    private void generateDeclaration(TreeNode decl) {
        // VAR_TYPE ARROW ID INIT_OPT
        String varName = decl.getChildren().get(2).getAttribute();
        TreeNode initOptNode = decl.getChildren().get(3);

        if (!initOptNode.getChildren().isEmpty() && !"EPSILON".equals(initOptNode.getChildren().get(0).getValue())) {
            // INIT_OPT -> EQ EVAL
            TreeNode evalNode = initOptNode.getChildren().get(1);
            String place = generateEvalExpr(evalNode);
            emit("=", place, null, varName);
        }
    }

    private void generateAssignment(TreeNode inst) {
        String varName = inst.getChildren().get(0).getAttribute(); // ID
        TreeNode instructionPrimeNode = inst.getChildren().get(1);
        TreeNode assignmentNode = instructionPrimeNode.getChildren().get(0);

        if (assignmentNode.getChildren().isEmpty()) return;
        String kind = assignmentNode.getChildren().get(0).getValue(); // EQ, INC, DEC

        switch (kind) {
            case "EQ": {
                // ASSIGNMENT -> EQ EXPR
                TreeNode exprNode = assignmentNode.getChildren().get(1);
                String place = generateExpr(exprNode);
                emit("=", place, null, varName);
                break;
            }
            case "INC": {
                String temp = newTemp();
                emit("SUM", varName, "1", temp);
                emit("=", temp, null, varName);
                break;
            }
            case "DEC": {
                String temp = newTemp();
                emit("SUB", varName, "1", temp);
                emit("=", temp, null, varName);
                break;
            }
            default:
                System.err.println("Unhandled assignment kind: " + kind);
                break;
        }
    }

    private void generateFunctionCall(TreeNode inst) {
        String fnName = inst.getChildren().get(0).getAttribute(); // ID
        TreeNode instructionPrimeNode = inst.getChildren().get(1);
        TreeNode funcCallNode = instructionPrimeNode.getChildren().get(0); // FUNCTION_CALL
        // FUNCTION_CALL -> PO ARG_LIST PT
        TreeNode argListNode = funcCallNode.getChildren().get(1);
        emitParams(argListNode);
        emit("call", fnName, null, null);
    }

    private void generateReturn(TreeNode evalNode) {
        String place = generateEvalExpr(evalNode);
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

    private String generateFactor(TreeNode factorNode) {
        if (factorNode == null || factorNode.getChildren().isEmpty()) {
            System.err.println("Error: Empty FACTOR node in generateFactor.");
            return newTemp();
        }

        TreeNode firstChild = factorNode.getChildren().get(0);
        String kind = firstChild.getValue();

        switch (kind) {
            case "PO": // FACTOR -> PO EVAL PT
                TreeNode evalNode = factorNode.getChildren().get(1);
                return generateEvalExpr(evalNode);

            case "ID": // FACTOR -> ID FACTOR_PRIME
                String idName = firstChild.getAttribute();
                TreeNode factorPrimeNode = factorNode.getChildren().size() > 1 ? factorNode.getChildren().get(1) : null;

                if (factorPrimeNode != null && !factorPrimeNode.getChildren().isEmpty() &&
                    "FUNCTION_CALL".equals(factorPrimeNode.getChildren().get(0).getValue())) {
                    // FACTOR_PRIME -> FUNCTION_CALL
                    TreeNode funcCallNode = factorPrimeNode.getChildren().get(0);
                    // FUNCTION_CALL -> PO ARG_LIST PT
                    TreeNode argListNode = funcCallNode.getChildren().get(1);
                    emitParams(argListNode);
                    String temp = newTemp();
                    emit("call", idName, null, temp);
                    return temp;
                } else {
                    // FACTOR_PRIME -> EPSILON
                    return idName;
                }

            case "LITERAL": // FACTOR -> LITERAL
                TreeNode literalNode = firstChild.getChildren().get(0);
                String literalValue = literalNode.getAttribute();
                if ("CHAR_LITERAL".equals(literalNode.getValue())) {
                    return "'" + literalValue + "'";
                }
                return literalValue; // For INTEGER_LITERAL, FLOAT_LITERAL

            case "NOT": // FACTOR -> NOT FACTOR
                TreeNode operandFactorNode = factorNode.getChildren().get(1);
                String operandPlace = generateFactor(operandFactorNode);
                String tempNot = newTemp();
                emit("NOT", operandPlace, null, tempNot);
                return tempNot;

            default:
                System.err.println("Unhandled factor kind: " + kind + " in CodeGenerator.generateFactor");
                return newTemp();
        }
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

    private void writeQuadruplesToFile() {
        String dirPath = "src/Files/TAC/";
        File dir = new File(dirPath);
        if (!dir.exists()) dir.mkdirs();
        String filePath = dirPath + File.separator + "TAC.txt";
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            for (String[] q : quads) {
                pw.printf("Result: %s Arg1: %s Arg2: %s Op: %s%n",
                        q[0], q[1], q[2], q[3]);
            }
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    private String newTemp() {
        return "t" + (tempCount++);
    }

    private String newLabel() {
        return "L" + (labelCount++);
    }

    public List<TACInstruction> getCode() {
        return code;
    }

    public void printTACCode() {
        System.out.println("--- TAC Code ---");
        for (TACInstruction tacInstruction : code) {
            System.out.println(tacInstruction.toString());
        }
        System.out.println("--- End of TAC Code ---");
    }
}