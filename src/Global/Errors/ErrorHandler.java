package Global.Errors;

public class ErrorHandler {
    public static void reportError(int line, String message) {
        System.err.println("Semantic Error at line " + line + ": " + message);
    }

    public static void reportError(String message) {
        System.err.println("[Error] " + message);
    }

    public static void reportWarning(int line, String message) {
        System.out.println("Warning at line " + line + ": " + message);
    }
}