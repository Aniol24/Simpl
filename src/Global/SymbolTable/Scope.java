package Global.SymbolTable;

import java.util.HashMap;
import java.util.Map;

public class Scope {
    /**
     * Símbols de la taula de símbols
     */
    private Map<String, Symbol> symbols;
    /**
     * Scope pare
     */
    private Scope parent;
    /**
     * Nom del scope
     */
    private String scopeName;

    /**
     * Constructor de la classe Scope
     */
    public Scope(String name, Scope parent) {
        this.scopeName = name;
        this.parent = parent;
        this.symbols = new HashMap<>();
    }

    /**
     * Funció per declarar un nou símbol
     * @param symbol Símbol a declarar
     * @return true si el símbol s'ha declarat correctament, false si ja existeix
     */
    public boolean declareSymbol(Symbol symbol) {
        if (symbols.containsKey(symbol.getName())) {
            return false; // Símbol ja declarat
        }
        symbols.put(symbol.getName(), symbol);
        return true;
    }

    /**
     * Funció per a buscar un símbol
     * @param name Nom del símbol a buscar
     * @return Símbol si existeix, null si no existeix
     */
    public Symbol lookupSymbol(String name) {
        Symbol symbol = symbols.get(name);

        if (symbol != null) {
            return symbol;
        }

        // Comprova si el símbol existeix en un scope pare
        if (parent != null) {
            return parent.lookupSymbol(name);
        }

        return null; // Símbol no trobat
    }

    /**
     * Retorna el scope pare
     * @return Scope pare
     */
    public Scope getParent() {
        return parent;
    }

    /**
     * Retorna el nom del scope
     * @return Nom del scope
     */
    public String getScopeName() {
        return scopeName;
    }

    /**
     * Retorna els símbols del scope
     * @return Mapa de símbols
     */
    public Map<String, Symbol> getSymbols() {
        return symbols;
    }
}