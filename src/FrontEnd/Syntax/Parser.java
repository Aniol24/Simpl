package FrontEnd.Syntax;

import java.util.*;
import FrontEnd.Lexicon.Scanner;
import FrontEnd.Lexicon.Token;
import Global.Errors.ErrorHandler;

public class Parser {

    /**
     * ErrorHandler: Error handler
     */
    private final ErrorHandler errorHandler;

    /**
     * Scanner per a passar els tokens al parser
     */
    private final Scanner scanner;
    /**
     * Taula de parsing
     */
    private final Map<String, Map<String, List<String>>> parsingTable;
    /**
     * Stack per a guardar els tokens
     */
    private final Stack<Token> stack = new Stack<>();
    /**
     * Stack per a guardar els nodes
     */
    private final Stack<TreeNode> nodeStack = new Stack<>();
    /**
     * Arbre de parsing
     */
    private TreeNode parseTreeRoot;

    /**
     * Constructor del parser
     *
     * @param scanner       Scanner per a passar els tokens al parser
     * @param errorHandler  Error handler
     */
    public Parser(Scanner scanner, ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;

        ParsingTable pt = new ParsingTable();
        this.scanner = scanner;
        this.parsingTable = pt.getParsingTable();
    }

    /**
     * Inicia el parser
     */
    public void parse() {
        stack.push(new Token("INICIAL", 0));
        parseTreeRoot = new TreeNode(new Token("ROOT", 0));
        parseTreeRoot.setRoot(true);
        TreeNode initialNode = new TreeNode(new Token("INICIAL", 0));
        parseTreeRoot.addChild(initialNode);
        nodeStack.push(initialNode);

        Token token = scanner.nextToken();

        while (!stack.isEmpty()) {
            // printStack(stack); // Mostrem el stack (debug)
            Token top = stack.peek();

            if (top.getValue().equals(token.getValue())) {
                // Agafem el node abans del pop i actualitzem les dades
                TreeNode currentNode = nodeStack.peek();
                currentNode.setToken(new Token(token));

                stack.pop();
                nodeStack.pop();
                token = scanner.nextToken();
                continue;
            }

            if (isTerminal(top.getValue())) {
                errorHandler.recordError("Expected '" + top.getValue() + "', but found '" + token.getValue() + "'", token.getLine());
                return;
            }

            // TODO: Si es tabula malament i surt un int, mostra error incorrecte. FN END INT. S'hauria de millorar la gestió d'errrors
            List<String> production = getProduction(top.getValue(), token.getValue());
            if (production == null) {
                errorHandler.recordError("There is no production for " + token.getAttribute(), token.getLine());
                return;
            }

            stack.pop();
            TreeNode parentNode = nodeStack.pop();

            // No dupliquem el símbol si ja té node pare
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
    }

    /**
     * Obté la producció de la taula de parsing
     *
     * @param nonTerminal Non terminal
     * @param terminal    Terminal
     * @return Producció
     */
    private List<String> getProduction(String nonTerminal, String terminal) {
        Map<String, List<String>> row = parsingTable.get(nonTerminal);
        return row != null ? row.get(terminal) : null;
    }

    /**
     * Comprova si un símbol és terminal
     *
     * @param symbol Símbol
     * @return true si és terminal, false si no
     */
    private boolean isTerminal(String symbol) {
        return !parsingTable.containsKey(symbol);
    }

    /**
     * Mostra l'arbre de parsing
     */
    public void printParseTree() {
        System.out.println("--- Parse Tree ---");
        printTree(parseTreeRoot, "", true);
        System.out.println("--- End of Parse Tree ---\n\n");
    }

    /**
     * Mostra l'arbre de parsing (intern)
     *
     * @param node   Node
     * @param prefix Prefix
     * @param isLast Si és l'últim node
     */
    private void printTree(TreeNode node, String prefix, boolean isLast) {
        System.out.print(prefix);
        System.out.print(isLast ? "└── " : "├── ");
        System.out.print(node.getValue());
        if (!Objects.equals(node.getAttribute(), "NO_ATTRIBUTE")) {
            System.out.print(" (" + node.getAttribute() + ")");
        }
        System.out.println();

        List<TreeNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            printTree(children.get(i), prefix + (isLast ? "    " : "│   "), i == children.size() - 1);
        }
    }

    /**
     * Obté l'arbre de parsing
     *
     * @return Arbre de parsing
     */
    public TreeNode getParseTreeRoot() {
        return parseTreeRoot;
    }

    /**
     * Mostra el stack (debug)
     */
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