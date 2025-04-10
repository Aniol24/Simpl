package SymbolTable;

import java.util.UUID;

public abstract class SymbolEntry {
    private UUID id;
    private String name;

    public SymbolEntry (UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getName (){
        return name;
    }
    public UUID getId(){
        return id;
    }

    public abstract String printEntry();
}
