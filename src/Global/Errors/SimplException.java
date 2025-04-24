package Global.Errors;

public class SimplException extends Exception{
    public enum Type { FILE_READ, SYNTAX, GRAMMAR, SEMANTIC, INTERNAL }

    private Type type;
    private final int line;

    public SimplException(Type type, int line, String message) {
        super(message);
        this.type = type;
        this.line = line;
    }

    public Type getType() {
        return type;
    }

    public int getLine() {
        return line;
    }
}
