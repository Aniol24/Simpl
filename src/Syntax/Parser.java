package Syntax;

import java.util.*;
import Lexicon.Scanner;

public class Parser {

    private Scanner scanner;
    private final Map<String, Map<String, List<String>>> parsingTable;
    private final Stack<String> stack = new Stack<>();
    private final Stack<TreeNode> nodeStack = new Stack<>();
    private TreeNode parseTreeRoot;

    public Parser(Scanner scanner) {
        ParsingTable pt = new ParsingTable();
        this.scanner = scanner;
        this.parsingTable = pt.getParsingTable();
    }

    public void parse() throws Exception {
        stack.push("INICIAL");
        parseTreeRoot = new TreeNode("ROOT");
        parseTreeRoot.setRoot(true);
        TreeNode initialNode = new TreeNode("INICIAL");
        parseTreeRoot.addChild(initialNode);
        nodeStack.push(initialNode);

        String token = scanner.nextToken();

        while (!stack.isEmpty()) {
            System.out.println(Arrays.toString(stack.toArray()));
            System.out.printf("Token actual: %s\n", token);
            String top = stack.peek();

            if (top.equals(token)) {
                stack.pop();
                nodeStack.pop();
                token = scanner.nextToken();
                continue;
            }

            if (isTerminal(top)) {
                error("Esperaba '" + top + "', pero se encontró '" + token + "'");
                return;
            }

            List<String> production = getProduction(top, token);
            if (production == null) {
                error("No existe producción para [" + top + ", " + token + "]");
                return;
            }

            stack.pop();
            TreeNode parentNode = nodeStack.pop();

            // No duplicamos el símbolo si ya lo contiene el nodo padre
            TreeNode nonTerminalNode = parentNode.getValue().equals(top) ? parentNode : new TreeNode(top);
            if (nonTerminalNode != parentNode) {
                parentNode.addChild(nonTerminalNode);
            }

            List<TreeNode> childrenNodes = new ArrayList<>();
            for (String symbol : production) {
                TreeNode child = new TreeNode(symbol);
                nonTerminalNode.addChild(child);
                childrenNodes.add(child);
            }

            Collections.reverse(childrenNodes);
            for (TreeNode child : childrenNodes) {
                if (!child.getValue().equals("EPSILON")) {
                    stack.push(child.getValue());
                    nodeStack.push(child);
                }
            }
        }

        System.out.println("✅ INPUT CORRECTO ✅");
        System.out.println("\nÁrbol de derivación:");
        printTree(parseTreeRoot, "", true);
    }

    private List<String> getProduction(String nonTerminal, String terminal) {
        Map<String, List<String>> row = parsingTable.get(nonTerminal);
        return row != null ? row.get(terminal) : null;
    }

    private boolean isTerminal(String symbol) {
        return !parsingTable.containsKey(symbol);
    }

    private void error(String msg) {
        System.err.println("ERROR: " + msg);
    }

    private void printTree(TreeNode node, String prefix, boolean isLast) {
        System.out.print(prefix);
        System.out.print(isLast ? "└── " : "├── ");
        System.out.println(node.getValue());

        List<TreeNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            printTree(children.get(i), prefix + (isLast ? "    " : "│   "), i == children.size() - 1);
        }
    }
}