package FrontEnd.Syntax;

import FrontEnd.Lexicon.Token;

import java.util.ArrayList;
import java.util.List;

public class TreeNode {
    private String value;
    private String attribute;
    private int line;
    private TreeNode parent;
    private List<TreeNode> children;
    private boolean isRoot;

    public TreeNode(Token token) {
        this.value = token.getValue();
        this.attribute = token.getAttribute();
        this.line = token.getLine();
        this.children = new ArrayList<>();
        this.isRoot = false;
    }

    public void setRoot(boolean isRoot) {
        this.isRoot = isRoot;
    }

    public Token getToken() {
        return new Token(value, attribute, line);
    }

    public void setToken(Token token) {
        this.value = token.getValue();
        this.attribute = token.getAttribute();
        this.line = token.getLine();
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public String getAttribute() {
        return attribute;
    }

    public boolean isRoot() {
        return isRoot;
    }

    public void addChild(TreeNode child) {
        child.parent = this;
        this.children.add(child);
    }

    public TreeNode getParent() {
        return parent;
    }

    public List<TreeNode> getChildren() {
        return children;
    }

    public String getValue() {
        return value;
    }
}