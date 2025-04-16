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
        System.out.println("Analyzing node: " + node.getValue());

        for (TreeNode child : node.getChildren()) {
            analyze(child);
        }
    }

    public TreeNode getParseTreeRoot() {
        return parseTreeRoot;
    }
}
