package Global.Errors;

import java.util.Objects;

class Error {
    private final String message;
    private final int line;

    public Error(String message, int line) {
        this.message = message;
        this.line = line;
    }

    public String getError() {
        return message;
    }

    public int getLine() {
        return line;
    }
}