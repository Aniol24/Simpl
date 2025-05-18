package Global.SymbolTable;

import java.util.ArrayList;
import java.util.List;

public class Symbol {
    /**
     * Nom del símbol
     */
    private final String name;
    /**
     * Tipus del símbol
     */
    private final String type; // "int", "flt", "chr", "function"
    /**
     * Indica si el símbol ha estat inicialitzat
     */
    private boolean initialized;
    /**
     * Número de línia on es troba el símbol
     */
    private final int lineNumber;
    /**
     * Tipus de retorn del símbol (només per a funcions)
     */
    private final String returnType;
    /**
     * Paràmetres del símbol (només per a funcions)
     */
    private final List<Symbol> parameters;

    /**
     * Constructor per a variables
     * @param name Nom de la variable
     * @param type Tipus de la variable
     * @param lineNumber Número de línia on es troba la variable
     */
    public Symbol(String name, String type, int lineNumber) {
        this.name = name;
        this.type = type;
        this.initialized = false;
        this.lineNumber = lineNumber;
        this.returnType = null;
        this.parameters = null;
    }

    /**
     * Constructor per a funcions
     * @param name Nom de la funció
     * @param returnType Tipus de retorn de la funció
     * @param lineNumber Número de línia on es troba la funció
     * @param parameters Paràmetres de la funció
     */
    public Symbol(String name, String returnType, int lineNumber, List<Symbol> parameters) {
        this.name = name;
        this.type = "function";
        this.initialized = true;
        this.lineNumber = lineNumber;
        this.returnType = returnType;
        this.parameters = parameters;
    }

    /**
     * Retorna el nom del símbol
     * @return Nom del símbol
     */
    public String getName() {
        return name;
    }

    /**
     * Retorna el tipus del símbol
     * @return Tipus del símbol
     */
    public String getType() {
        return type;
    }

    /**
     * Retorna si el símbol ha estat inicialitzat
     * @return true si el símbol ha estat inicialitzat, false en cas contrari
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Marca el símbol com a inicialitzat / no inicialitzat
     */
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    /**
     * Retorna el número de línia on es troba el símbol
     * @return Número de línia
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Retorna el tipus de retorn del símbol (només per a funcions)
     * @return Tipus de retorn
     */
    public String getReturnType() {
        return returnType;
    }

    /**
     * Retorna els paràmetres del símbol (només per a funcions)
     * @return Paràmetres
     */
    public List<Symbol> getParameters() {
        return parameters;
    }

    /**
     * Retorna si el símbol és una funció
     * @return true si el símbol és una funció, false en cas contrari
     */
    public boolean isFunction() {
        return "function".equals(type);
    }

    @Override
    public String toString() {
        if (isFunction()) {
            return "Function " + name + " returns " + returnType;
        } else {
            return type + " " + name + (initialized ? " (initialized)" : " (not initialized)");
        }
    }
}