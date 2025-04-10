package SymbolTable;

public class SymbolTable {

    private Scope rootScope;

    private Scope currentScope;

    public SymbolTable() {
        this.rootScope = new Scope();
        this.currentScope = rootScope;
    }
}
