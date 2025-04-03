package Syntax;

import com.google.gson.*;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

public class ParsingTable {

    static class Rule {
        String name;
        List<List<String>> tokens;
    }

    public Map<String, Map<String, List<String>>> parsingTable;


    public ParsingTable() {

        parsingTable = new HashMap<>();

        try {
            createTable();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

    }

    public void createTable() throws FileNotFoundException {

        Gson gson = new Gson();

        Rule[] grammar = gson.fromJson(new FileReader("src/JSON Files/gramatica.json"), Rule[].class);

        JsonObject firstFollow = gson.fromJson(new FileReader("src/JSON Files/first_and_follow.json"), JsonObject.class);
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

    }

    private static Set<String> computeFirst(List<String> sequence, JsonObject firstMap) {
        Set<String> result = new HashSet<>();

        for (String symbol : sequence) {
            if (firstMap.has(symbol)) {
                JsonArray arr = firstMap.getAsJsonArray(symbol);
                for (JsonElement el : arr) {
                    result.add(el.getAsString());
                }
                if (!result.contains("EPSILON")) break;
            } else {
                result.add(symbol);
                break;
            }
        }

        return result;
    }

    private static void insert(Map<String, Map<String, List<String>>> table, String nonTerminal, String terminal, List<String> production) {
        table.computeIfAbsent(nonTerminal, k -> new HashMap<>()).put(terminal, new ArrayList<>(production));
    }

    public static void printTable(Map<String, Map<String, List<String>>> table) {
        for (String nonTerm : table.keySet()) {
            System.out.println("NonTerminal: " + nonTerm);
            for (String term : table.get(nonTerm).keySet()) {
                System.out.println("  " + term + " → " + table.get(nonTerm).get(term));
            }
        }
    }

    public Map<String, Map<String, List<String>>> getParsingTable() {
        return parsingTable;
    }

}
