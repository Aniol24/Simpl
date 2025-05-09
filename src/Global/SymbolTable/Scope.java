package Global.SymbolTable;

import java.util.HashMap;
import java.util.Map;

public class Scope {
    private Map<String, Symbol> symbols;
    private Scope parent;
    private String scopeName;

    public Scope(String name, Scope parent) {
        this.scopeName = name;
        this.parent = parent;
        this.symbols = new HashMap<>();
    }

    public boolean declareSymbol(Symbol symbol) {
        if (symbols.containsKey(symbol.getName())) {
            return false; // Símbolo ya declarado
        }
        symbols.put(symbol.getName(), symbol);
        return true;
    }

    public Symbol lookupSymbol(String name) {

        Symbol symbol = symbols.get(name);
        System.out.println("Hello");
        if (symbol != null) {
            return symbol;
        }

        // Comprueba si el símbolo está en el parent scope
        if (parent != null) {
            return parent.lookupSymbol(name);
        }

        return null; // Symbol no encontrado
    }

    public Scope getParent() { return parent; }
    public String getScopeName() { return scopeName; }
    public Map<String, Symbol> getSymbols() { return symbols; }
}