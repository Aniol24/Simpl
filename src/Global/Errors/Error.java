package Global.Errors;

import java.util.Objects;

class Error {
    /**
     * El missatge d'error
     */
    private final String message;
    /**
     * La línia on es troba l'error
     */
    private final int line;

    /**
     * Constructor
     *
     * @param message El missatge d'error
     * @param line    La línia on es troba l'error
     */
    public Error(String message, int line) {
        this.message = message;
        this.line = line;
    }

    /**
     * Retorna l'error
     */
    public String getError() {
        return message;
    }

    /**
     * Retorna la línia
     */
    public int getLine() {
        return line;
    }
}