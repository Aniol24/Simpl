package Syntax;

import java.util.ArrayList;
import java.util.List;

public class TreeNode {
    private String value;
    private TreeNode parent;
    private List<TreeNode> children;
    private boolean isRoot;

    public TreeNode(String value) {
        this.value = value;
        this.children = new ArrayList<>();
        this.isRoot = false;
    }

    public void setRoot(boolean isRoot) {
        this.isRoot = isRoot;
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