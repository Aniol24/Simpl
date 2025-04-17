package Global.SymbolTable;

import java.util.ArrayList;
import java.util.List;

public class Symbol {
    private String name;
    private String type; // "int", "flt", "chr", "function"
    private boolean initialized;
    private int lineNumber;
    private String returnType; // for functions
    private List<Symbol> parameters; // for functions

    // Constructor for variables
    public Symbol(String name, String type, int lineNumber) {
        this.name = name;
        this.type = type;
        this.initialized = false;
        this.lineNumber = lineNumber;
        this.returnType = null;
        this.parameters = null;
    }

    // Constructor for functions
    public Symbol(String name, String returnType, int lineNumber, List<Symbol> parameters) {
        this.name = name;
        this.type = "function";
        this.initialized = true; // Functions are always initialized
        this.lineNumber = lineNumber;
        this.returnType = returnType;
        this.parameters = parameters;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public boolean isInitialized() { return initialized; }
    public void setInitialized(boolean initialized) { this.initialized = initialized; }
    public int getLineNumber() { return lineNumber; }
    public String getReturnType() { return returnType; }
    public List<Symbol> getParameters() { return parameters; }
    public boolean isFunction() { return "function".equals(type); }

    @Override
    public String toString() {
        if (isFunction()) {
            return "Function " + name + " returns " + returnType;
        } else {
            return type + " " + name + (initialized ? " (initialized)" : " (not initialized)");
        }
    }
}