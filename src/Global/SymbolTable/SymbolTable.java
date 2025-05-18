package Global.SymbolTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class SymbolTable {
    /**
     * El scope actual
     */
    private Scope currentScope;
    /**
     * Stack amb els scopes
     */
    private Stack<Scope> scopeStack;
    /**
     * El scope global
     */
    private Scope globalScope;
    /**
     * La llista de tots els scopes
     */
    private final List<Scope> allScopes;

    /**
     * Constructor de la taula de símbols.
     */
    public SymbolTable() {
        scopeStack = new Stack<>();
        allScopes = new ArrayList<>();
        globalScope = new Scope("global", null);
        allScopes.add(globalScope);
        currentScope = globalScope;
        scopeStack.push(currentScope);
    }

    /**
     * Entra en un nou scope
     *
     * @param scopeName Nom del nou scope
     */
    public void enterScope(String scopeName) {
        Scope newScope = new Scope(scopeName, currentScope);
        scopeStack.push(newScope);
        currentScope = newScope;
        allScopes.add(newScope);
    }

    /**
     * Surt del scope actual
     */
    public void exitScope() {
        if (scopeStack.size() > 1) { // Evitem sortir del scope global
            String scopeName = currentScope.getScopeName();
            scopeStack.pop();
            currentScope = scopeStack.peek();
        }
    }

    /**
     * Declara un símbol al scope actual
     *
     * @param symbol El símbol a declarar
     * @return true si s'ha declarat correctament, false si ja existeix
     */
    public boolean declareSymbol(Symbol symbol) {
        return currentScope.declareSymbol(symbol);
    }

    /**
     * Busca un símbol al scope actual
     * @param name Nom del símbol a buscar
     * @return El símbol si existeix, null si no existeix
     */
    public Symbol lookupSymbol(String name) {
        return currentScope.lookupSymbol(name);
    }

    /**
     * Retorna el scope actual
     * @return El scope actual
     */
    public Scope getCurrentScope() {
        return currentScope;
    }

    /**
     * Retorna el scope global
     * @return El scope global
     */
    public Scope getGlobalScope() {
        return globalScope;
    }

    /**
     * Mostra tots els scopes i símbols de la taula de símbols
     */
    public void printAllScopesDetails() {
        System.out.println("--- Symbol Table Dump ---");
        if (allScopes.isEmpty()) {
            System.out.println("No scopes found.");
            return;
        }
        for (Scope scope : allScopes) {
            System.out.println("Scope: " + scope.getScopeName() +
                               (scope.getParent() != null ? " (Parent: " + scope.getParent().getScopeName() + ")" : " (Global Scope)"));
            if (scope.getSymbols().isEmpty()) {
                System.out.println("  No symbols in this scope.");
            } else {
                for (Symbol symbol : scope.getSymbols().values()) {
                    System.out.println("  " + symbol.toString() + " (Line: " + symbol.getLineNumber() + ")");
                }
            }
        }
        System.out.println("--- End of Symbol Table Dump ---\n\n");
    }
}