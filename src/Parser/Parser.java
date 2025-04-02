package Parser;

import java.util.*;

public class Parser {

    private final Map<String, Map<String, List<String>>> parsingTable;
    private final Stack<String> stack = new Stack<>();
    private final List<String> inputTokens;

    public Parser(List<String> inputTokens) {
        ParsingTable pt = new ParsingTable();

        this.parsingTable = pt.getParsingTable();
        this.inputTokens = new ArrayList<>(inputTokens);
        this.inputTokens.add("EOF");

        parse();
    }

    private void parse() {

        stack.push("START");

        int currentIndex = 0;

        while (!stack.isEmpty()) {
            String top = stack.peek();
            String currentToken = inputTokens.get(currentIndex);

            if (top.equals(currentToken)) {
                stack.pop();
                currentIndex++;
                continue;
            }

            if (isTerminal(top)) {
                error("Esperava " + top + " pero tenim " + currentToken);
                return;
            }

            List<String> production = getProduction(top, currentToken);

            if (production == null) {
                error("No hi ha producció per [" + top + ", " + currentToken + "]");
                return;
            }

            stack.pop();
            List<String> reversed = new ArrayList<>(production);
            Collections.reverse(reversed);
            for (String symbol : reversed) {
                if (!symbol.equals("EPSILON")) {
                    stack.push(symbol);
                }
            }
        }

        System.out.println("✅ INPUT CORRECTE ✅");
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
