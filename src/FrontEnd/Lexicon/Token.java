package FrontEnd.Lexicon;

public class Token {
    private String value;
    private String attribute;
    private int line;

    public Token(String value, String attribute, int line) {
        this.value = value;
        this.attribute = attribute;
        this.line = line;
    }

    public Token(String value, int line) {
        this.value = value;
        this.attribute = "NO_ATTRIBUTE";
        this.line = line;
    }

    public Token(Token token) {
        this.value = token.getValue();
        this.attribute = token.getAttribute();
        this.line = token.getLine();
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }
}
