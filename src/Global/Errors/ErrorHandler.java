package Global.Errors;

public class ErrorHandler {
    public void reportError(int line, String message) {
        System.err.println("Semantic Error at line " + line + ": " + message);
    }

    public void reportWarning(int line, String message) {
        System.out.println("Warning at line " + line + ": " + message);
    }
}