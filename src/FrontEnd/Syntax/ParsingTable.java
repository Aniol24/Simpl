package FrontEnd.Syntax;

import com.google.gson.*;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

public class ParsingTable {

    /**
     * Classe que representa una regla de la gramática
     */
    static class Rule {
        String name;
        List<List<String>> tokens;
    }

    /**
     * La taula de parsing
     */
    public Map<String, Map<String, List<String>>> parsingTable;

    /**
     * Constructor de la classe ParsingTable
     */
    public ParsingTable() {
        parsingTable = new HashMap<>();
        try {
            createTable();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            System.exit(1);
        }

        //printTable(parsingTable); // Debug
    }

    /**
     * Crea la taula de parsing a partir de la gramàtica i les funcions FIRST i FOLLOW
     *
     * @throws Exception si hi ha un error llegint els fitxers
     */
    public void createTable() throws Exception {
        try {
            Gson gson = new Gson();
            Rule[] grammar = gson.fromJson(new FileReader("src/Files/JSON/gramatica.json"), Rule[].class);
            JsonObject firstFollow = gson.fromJson(new FileReader("src/Files/JSON/first_and_follow.json"), JsonObject.class);
            JsonObject firstMap = firstFollow.getAsJsonObject("first");
            JsonObject followMap = firstFollow.getAsJsonObject("follow");

            for (Rule rule : grammar) {
                for (List<String> production : rule.tokens) {
                    Set<String> firstSet = computeFirst(production, firstMap);

                    for (String terminal : firstSet) {
                        if (!terminal.equals("EPSILON")) {
                            insert(parsingTable, rule.name, terminal, production);
                        }
                    }

                    if (firstSet.contains("EPSILON")) {
                        JsonArray followArray = followMap.getAsJsonArray(rule.name);
                        for (JsonElement el : followArray) {
                            String follow = el.getAsString();
                            insert(parsingTable, rule.name, follow, production);
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new Exception("Grammar and/or First & Follow files not found: " + e.getMessage());
        }
    }

    /**
     * Computa el conjunt FIRST d'una producció
     *
     * @param sequence la producció
     * @param firstMap el mapa FIRST
     * @return el conjunt FIRST
     */
    private static Set<String> computeFirst(List<String> sequence, JsonObject firstMap) {
        Set<String> result = new HashSet<>();
        boolean allNullable = true;

        for (String symbol : sequence) {
            Set<String> firstSymbol = new HashSet<>();

            if (firstMap.has(symbol)) {
                JsonArray arr = firstMap.getAsJsonArray(symbol);
                for (JsonElement el : arr) {
                    firstSymbol.add(el.getAsString());
                }
                for (String s : firstSymbol) {
                    if (!s.equals("EPSILON")) {
                        result.add(s);
                    }
                }
                if (!firstSymbol.contains("EPSILON")) {
                    allNullable = false;
                    break;
                }
            } else {
                result.add(symbol);
                allNullable = false;
                break;
            }
        }

        if (allNullable) {
            result.add("EPSILON");
        }
        return result;
    }

    /**
     * Insereix una producció a la taula de parsing
     *
     * @param table       la taula de parsing
     * @param nonTerminal el no terminal
     * @param terminal    el terminal
     * @param production  la producció
     */
    private static void insert(Map<String, Map<String, List<String>>> table, String nonTerminal, String terminal, List<String> production) {
        table.computeIfAbsent(nonTerminal, k -> new HashMap<>()).put(terminal, new ArrayList<>(production));
    }

    /**
     * Imprimeix la taula de parsing
     *
     * @param table la taula de parsing
     */
    public static void printTable(Map<String, Map<String, List<String>>> table) {
        for (String nonTerm : table.keySet()) {
            System.out.println("NonTerminal: " + nonTerm);
            for (String term : table.get(nonTerm).keySet()) {
                System.out.println("  " + term + " → " + table.get(nonTerm).get(term));
            }
        }
    }

    /**
     * Retorna la taula de parsing
     *
     * @return the parsing table
     */
    public Map<String, Map<String, List<String>>> getParsingTable() {
        return parsingTable;
    }
}
