package FrontEnd.Lexicon;

public class Token {
    /**
     * Valor del token (exemple: ID, INT; LITERAL, etc.)
     */
    private String value;
    /**
     * Atribut del token (exemple: 33, x, int, etc.)
     */
    private String attribute;
    /**
     * Linia on es troba el token
     */
    private int line;

    /**
     * Constructor de la classe Token
     *
     * @param value     Valor del token (exemple: ID, INT; LITERAL, etc.)
     * @param attribute Atribut del token (exemple: 33, x, int, etc.)
     * @param line      Linia on es troba el token
     */
    public Token(String value, String attribute, int line) {
        this.value = value;
        this.attribute = attribute;
        this.line = line;
    }

    /**
     * Constructor de la classe Token per a tokens sense atribut
     *
     * @param value Valor del token (exemple: ID, INT; LITERAL, etc.)
     * @param line  Linia on es troba el token
     */
    public Token(String value, int line) {
        this.value = value;
        this.attribute = "NO_ATTRIBUTE";
        this.line = line;
    }

    /**
     * Constructor de la classe Token
     *
     * @param token Token a copiar
     */
    public Token(Token token) {
        this.value = token.getValue();
        this.attribute = token.getAttribute();
        this.line = token.getLine();
    }

    /**
     * Retorna el valor del token
     * @return Valor del token
     */
    public String getValue() {
        return value;
    }

    /**
     * Retorna l'atribut del token
     * @return Atribut del token
     */
    public String getAttribute() {
        return attribute;
    }

    /**
     * Retorna la linia on es troba el token
     * @return Linia on es troba el token
     */
    public int getLine() {
        return line;
    }
}
