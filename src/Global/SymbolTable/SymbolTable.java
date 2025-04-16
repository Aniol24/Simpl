package Global.SymbolTable;

public class SymbolTable {

    private Scope rootScope;

    private Scope currentScope;

    public SymbolTable() {
        this.rootScope = new Scope();
        this.currentScope = rootScope;
    }

    public Scope getRootScope() {
        return this.rootScope;
    }

    public Scope getCurrentScope() {
        return this.currentScope;
    }
}
