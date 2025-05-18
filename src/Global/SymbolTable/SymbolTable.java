package Global.SymbolTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class SymbolTable {
    private Scope currentScope;
    private Stack<Scope> scopeStack;
    private Scope globalScope;
    private final List<Scope> allScopes;

    public SymbolTable() {
        scopeStack = new Stack<>();
        allScopes = new ArrayList<>();
        globalScope = new Scope("global", null);
        allScopes.add(globalScope);
        currentScope = globalScope;
        scopeStack.push(currentScope);
    }

    public void enterScope(String scopeName) {
        Scope newScope = new Scope(scopeName, currentScope);
        scopeStack.push(newScope);
        currentScope = newScope;
        allScopes.add(newScope);
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

    public List<Scope> getAllScopes() {
        return allScopes;
    }

    public void printAllScopesDetails() {
        System.out.println("\n--- Symbol Table Dump ---");
        if (allScopes.isEmpty()) {
            System.out.println("No scopes found.");
            return;
        }
        for (Scope scope : allScopes) {
            System.out.println("\nScope: " + scope.getScopeName() + 
                               (scope.getParent() != null ? " (Parent: " + scope.getParent().getScopeName() + ")" : " (Global Scope)"));
            if (scope.getSymbols().isEmpty()) {
                System.out.println("  No symbols in this scope.");
            } else {
                for (Symbol symbol : scope.getSymbols().values()) {
                    System.out.println("  " + symbol.toString() + " (Line: " + symbol.getLineNumber() + ")");
                }
            }
        }
        System.out.println("--- End of Symbol Table Dump ---");
    }
}