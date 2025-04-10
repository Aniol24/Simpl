package SymbolTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scope {

    private Map<String, SymbolEntry> symbolTable;
    private Scope parentScope;
    private List<Scope> childScopes;

    public Scope() {
        this.parentScope = null;
        this.childScopes = new ArrayList<>();
    }

    public Scope(Scope parentScope) {
        this.symbolTable = new HashMap<>();
        this.parentScope = parentScope;
        this.childScopes = new ArrayList<>();
    }

    public void addEntry(SymbolEntry e) {
        symbolTable.put(e.)
    }
}
