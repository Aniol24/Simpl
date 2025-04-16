package FrontEnd.Syntax;

import java.util.*;
import FrontEnd.Lexicon.Scanner;
import FrontEnd.Lexicon.Token;

public class Parser {

    private Scanner scanner;
    private final Map<String, Map<String, List<String>>> parsingTable;
    private final Stack<Token> stack = new Stack<>();
    private final Stack<TreeNode> nodeStack = new Stack<>();
    private TreeNode parseTreeRoot;

    public Parser(Scanner scanner) {
        ParsingTable pt = new ParsingTable();
        this.scanner = scanner;
        this.parsingTable = pt.getParsingTable();
    }

    public void parse() throws Exception {
        stack.push(new Token("INICIAL", 0));
        parseTreeRoot = new TreeNode(new Token("ROOT", 0));
        parseTreeRoot.setRoot(true);
        TreeNode initialNode = new TreeNode(new Token("INICIAL", 0));
        parseTreeRoot.addChild(initialNode);
        nodeStack.push(initialNode);

        Token token = scanner.nextToken();
        System.out.println("Token value: " + token.getValue());
        System.out.println("Token attribute: " + token.getAttribute());
        System.out.println("Token line: " + token.getLine());

        while (!stack.isEmpty()) {
            printStack(stack);
            System.out.printf("Token actual: %s\n", token.getValue());
            Token top = stack.peek();

            if (top.getValue().equals(token.getValue())) {
                // Agafem el node abans del pop i actualitzem les dades
                TreeNode currentNode = nodeStack.peek();
                currentNode.setToken(new Token(token));

                stack.pop();
                nodeStack.pop();
                token = scanner.nextToken();
                System.out.println("Token value: " + token.getValue());
                System.out.println("Token attribute: " + token.getAttribute());
                System.out.println("Token line: " + token.getLine());
                continue;
            }

            if (isTerminal(top.getValue())) {
                error("Esperaba '" + top.getValue() + "', pero se encontró '" + token.getValue() + "'");
                return;
            }

            List<String> production = getProduction(top.getValue(), token.getValue());
            if (production == null) {
                error("No existe producción para [" + top.getValue() + ", " + token.getValue() + "] para " + token.getAttribute() + " en la línea " + token.getLine());
                return;
            }

            stack.pop();
            TreeNode parentNode = nodeStack.pop();

            // No duplicamos el símbolo si ya lo contiene el nodo padre
            TreeNode nonTerminalNode = parentNode.getValue().equals(top.getValue()) ? parentNode : new TreeNode(top);
            if (nonTerminalNode != parentNode) {
                parentNode.addChild(nonTerminalNode);
            }

            List<TreeNode> childrenNodes = new ArrayList<>();
            for (String symbol : production) {
                TreeNode child = new TreeNode(new Token(symbol, token.getLine()));
                nonTerminalNode.addChild(child);
                childrenNodes.add(child);
            }

            Collections.reverse(childrenNodes);
            for (TreeNode child : childrenNodes) {
                if (!child.getValue().equals("EPSILON")) {
                    stack.push(child.getToken());
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
        System.out.print("\n" +prefix);
        System.out.print(isLast ? "└── " : "├── ");
        System.out.print(node.getValue());
        if (!Objects.equals(node.getAttribute(), "NO_ATTRIBUTE")) {
            System.out.print(" (" + node.getAttribute() + ")");
        }

        List<TreeNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            printTree(children.get(i), prefix + (isLast ? "    " : "│   "), i == children.size() - 1);
        }
    }

    public TreeNode getParseTreeRoot() {
        return parseTreeRoot;
    }

    private void printStack(Stack<Token> stack) {
        System.out.print("Stack: [");
        boolean first = true;
        for (Token td : stack) {
            if (!first) {
                System.out.print(", ");
            }
            System.out.print(td.getValue());
            first = false;
        }
        System.out.println("]");
    }
}