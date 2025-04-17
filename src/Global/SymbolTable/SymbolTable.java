package Global.SymbolTable;

import java.util.Stack;

public class SymbolTable {
    private Scope currentScope;
    private Stack<Scope> scopeStack;
    private Scope globalScope;

    public SymbolTable() {
        scopeStack = new Stack<>();
        globalScope = new Scope("global", null);
        currentScope = globalScope;
        scopeStack.push(currentScope);
    }

    public void enterScope(String scopeName) {
        Scope newScope = new Scope(scopeName, currentScope);
        scopeStack.push(newScope);
        currentScope = newScope;
        System.out.println("Entered scope: " + scopeName);
    }

    public void exitScope() {
        if (scopeStack.size() > 1) { // Evitamos salir del scope global
            String scopeName = currentScope.getScopeName();
            scopeStack.pop();
            currentScope = scopeStack.peek();
            System.out.println("Exited scope: " + scopeName);
        }
    }

    public boolean declareSymbol(Symbol symbol) {
        // Declaramos en el scope actual
        boolean result = currentScope.declareSymbol(symbol);
        if (result) {
            System.out.println("Declared " + symbol.toString() + " in scope '" + currentScope.getScopeName() + "'");
        }
        return result;
    }

    public Symbol lookupSymbol(String name) {
        return currentScope.lookupSymbol(name);
    }

    public Scope getCurrentScope() {
        return currentScope;
    }

    public Scope getGlobalScope() {
        return globalScope;
    }
}