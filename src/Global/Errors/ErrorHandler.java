package Global.Errors;

import FrontEnd.Lexicon.Token;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ErrorHandler {

    private final List<Error> errors;

    public ErrorHandler() {
        this.errors = new LinkedList<>();
    }

    public void recordError(String message, int line) {
        errors.add(new Error(message, line));
    }

    public void recordTypeMismatchError(String location, String expectedType, int line) {
        errors.add(new Error("Type mismatch: expected '" + expectedType + "'", line));
    }

    public void recordParameterMismatchError(String funcName, int expectedParams, int gotParams, int line) {
        errors.add(new Error("Parameter count mismatch for function '" + funcName + "': expected " + expectedParams + ", got " + gotParams, line));
    }

    public void recordMissingReturnError(String funcName, int line) {
        errors.add(new Error("Missing return value in function '" + funcName + "'", line));
    }

    /**
     * Records invalid return error.
     *
     * @param funcName the function name
     * @param line     the line number
     */
    public void recordInvalidReturnError(String funcName, int line) {
        errors.add(new Error("Invalid return value in function '" + funcName + "'", line));
    }

    public void recordConditionError(int line) {
        errors.add(new Error("Condition types do not match", line));
    }

    public void recordFunctionIsNotDeclared(Token token) {
        errors.add(new Error("Function '" + token.getValue() + "' not declared", token.getLine()));
    }

    public void recordVariableDoesntExist(Token token) {
        errors.add(new Error("Variable '" + token.getValue() + "' not declared", token.getLine()));
    }

    public void recordVariableAlreadyDeclared(Token token) {
        errors.add(new Error("Variable '" + token.getValue() + "' already declared", token.getLine()));
    }

    public void printErrors() {
        if (errors.isEmpty()) {
            return;
        }

        tidyErrors();

        String ANSI_RED = "\u001B[31m";
        String ANSI_RESET = "\u001B[0m";
        String lineSeparator = "_______________________________________________________________________________________";

        System.out.println(ANSI_RED + lineSeparator + ANSI_RESET);
        for (Error e : errors) {
            System.out.printf(ANSI_RED + "ERROR: %s at line %d.\n" + ANSI_RESET, e.getError(), e.getLine());
        }
        System.out.println(ANSI_RED + lineSeparator + ANSI_RESET);
    }

    private void tidyErrors() {
        Set<Error> uniqueErrors = new HashSet<>(errors);
        errors.clear();
        errors.addAll(uniqueErrors);
        // Sort errors by line number
        errors.sort(Comparator.comparingInt(Error::getLine));
    }

    public Boolean hasErrors() {
        return !errors.isEmpty();
    }

    public void recordFunctionAlreadyExists(Token token) {
        errors.add(new Error("Function '" + token.getValue() + "' already exists", token.getLine()));
    }
}