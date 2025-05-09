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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Error error = (Error) o;
        return line == error.line && Objects.equals(message, error.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, line);
    }

    @Override
    public String toString() {
        return "Error{" +
                "message='" + message + '\'' +
                ", line=" + line +
                '}';
    }
}