package Global.Errors;

import FrontEnd.Lexicon.Token;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ErrorHandler {

    /**
     * Llista d'errors
     */
    private final List<Error> errors;

    /**
     * Constructor de la classe ErrorHandler
     */
    public ErrorHandler() {
        this.errors = new LinkedList<>();
    }

    /**
     * Registra un error
     *
     * @param message el missatge d'error
     * @param line    la línia on es troba l'error
     */
    public void recordError(String message, int line) {
        errors.add(new Error(message, line));
    }

    /**
     * Mostra els errors registrats
     */
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

    /**
     * Esborra els errors duplicats i els ordena
     */
    private void tidyErrors() {
        Set<Error> uniqueErrors = new HashSet<>(errors);
        errors.clear();
        errors.addAll(uniqueErrors);
        // Ordenem els errors per línia
        errors.sort(Comparator.comparingInt(Error::getLine));
    }

    /**
     * Retorna si hi ha errors
     *
     * @return true si hi ha errors, false si no
     */
    public Boolean hasErrors() {
        return !errors.isEmpty();
    }
}