package Syntax;

import java.util.*;
import Lexicon.Scanner;

public class Parser {

    private Scanner scanner;
    private final Map<String, Map<String, List<String>>> parsingTable;
    private final Stack<String> stack = new Stack<>();

    public Parser(Scanner scanner) {
        ParsingTable pt = new ParsingTable();
        this.scanner = scanner;
        this.parsingTable = pt.getParsingTable();
    }

    public void newParse() throws Exception {
        String token;
        while (!(token = scanner.nextToken()).equals("EOF")) {
            System.out.print(token + " ");

            if (token.equals("EOL") || token.equals("START") || token.equals("END")) {
                System.out.println();
            }
        }
        System.out.println("EOF");
    }


    public void newParse() throws Exception {
        String token;
        while (!(token = scanner.nextToken()).equals("EOF")) {
            System.out.print(token + " ");

            if (token.equals("EOL") || token.equals("START") || token.equals("END")) {
                System.out.println();
            }
        }
        System.out.println("EOF");
    }

    public void parse() throws Exception {

        stack.push("INICIAL");

        String token = scanner.nextToken();

        while (!stack.isEmpty()) {
            System.out.println(Arrays.toString(stack.toArray()));
            System.out.printf("Token actual: %s\n", token);
            String top = stack.peek();

            // Si el tope del stack es igual al token actual, lo consumimos.
            if (top.equals(token)) {
                stack.pop();
                token = scanner.nextToken();
                continue;
            }

            // Si el tope es terminal pero no coincide, hay error.
            if (isTerminal(top)) {
                error("Esperaba '" + top + "', pero se encontró '" + token + "'");
                return;
            }

            // Buscamos la producción en la tabla de análisis.
            List<String> production = getProduction(top, token);
            if (production == null) {
                error("No existe producción para [" + top + ", " + token + "]");
                return;
            }

            // Quitamos el no terminal y añadimos los símbolos de la producción en orden inverso.
            stack.pop();
            List<String> reversed = new ArrayList<>(production);
            Collections.reverse(reversed);
            for (String symbol : reversed) {
                if (!symbol.equals("EPSILON")) {
                    stack.push(symbol);
                }
            }
        }

        // Si se ha vaciado el stack y se consumieron correctamente los tokens, la entrada es correcta.
        System.out.println("✅ INPUT CORRECTO ✅");
    }

    private List<String> getProduction(String nonTerminal, String terminal) {
        Map<String, List<String>> row = parsingTable.get(nonTerminal);
        return row != null ? row.get(terminal) : null;
    }

    private boolean isTerminal(String symbol) {
        return !parsingTable.containsKey(symbol);
    }

    private void error(String msg) {
        System.err.println("ERROR: " + msg);
    }
}
