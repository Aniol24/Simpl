package FrontEnd.Semantics;

import Global.SymbolTable.SymbolTable;
import FrontEnd.Syntax.TreeNode;

public class SemanticAnalyzer {

    private SymbolTable symbolTable;
    private TreeNode parseTreeRoot;

    public SemanticAnalyzer(SymbolTable symbolTable, TreeNode parseTreeRoot) {
        this.parseTreeRoot = parseTreeRoot;
    }

    private void analyze(TreeNode node) {
        // Perform semantic analysis on the node
        // This is a placeholder for the actual semantic analysis logic
        System.out.println("Analyzing node: " + node.getValue());

        // Recursively analyze child nodes
        for (TreeNode child : node.getChildren()) {
            analyze(child);
        }
    }

    public TreeNode getParseTreeRoot() {
        return parseTreeRoot;
    }
}
