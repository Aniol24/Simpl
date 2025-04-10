package Utils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class analitzadorGramatica {

    static class Rule {
        String name;
        List<List<String>> tokens;
    }

    public static void main(String[] args) throws Exception {
        Path path = Paths.get("src", "JSON Files", "gramatica.json");
        List<Rule> rules = readGrammar(path.toString());

        Set<String> nonTerminals = new HashSet<>();
        Set<String> terminals = new HashSet<>();
        Set<String> allTokens = new HashSet<>();
        Map<String, Set<String>> first = new HashMap<>();
        Map<String, Set<String>> follow = new HashMap<>();
        Map<String, List<List<String>>> productions = new HashMap<>();

        for (Rule rule : rules) {
            nonTerminals.add(rule.name);
            productions.put(rule.name, rule.tokens);
            first.put(rule.name, new HashSet<>());
            follow.put(rule.name, new HashSet<>());
        }

        for (Rule rule : rules) {
            for (List<String> production : rule.tokens) {
                for (String token : production) {
                    allTokens.add(token);
                    if (!nonTerminals.contains(token) && !token.equals("EPSILON")) {
                        terminals.add(token);
                    }
                }
            }
        }

        // FIRST
        boolean changed;
        do {
            changed = false;
            for (Rule rule : rules) {
                Set<String> firstSet = first.get(rule.name);

                for (List<String> production : rule.tokens) {
                    boolean nullable = true;
                    for (String symbol : production) {
                        Set<String> symbolFirst;

                        if (symbol.equals("EPSILON")) {
                            nullable = true;
                            if (firstSet.add("EPSILON"))
                                changed = true;
                            break;
                        }

                        if (nonTerminals.contains(symbol)) {
                            symbolFirst = first.get(symbol);
                        } else {
                            symbolFirst = new HashSet<>();
                            symbolFirst.add(symbol); // terminal
                        }

                        // Añadir todos excepto EPSILON
                        for (String tok : symbolFirst) {
                            if (!tok.equals("EPSILON") && firstSet.add(tok)) {
                                changed = true;
                            }
                        }

                        if (!symbolFirst.contains("EPSILON")) {
                            nullable = false;
                            break; // no es nullable, se detiene la iteración de esta producción
                        }
                    }
                    if (nullable) {
                        if (firstSet.add("EPSILON"))
                            changed = true;
                    }
                }
            }
        } while (changed);

        // FOLLOW
        follow.get("INICIAL").add("EOF");

        do {
            changed = false;
            for (Rule rule : rules) {
                for (List<String> prod : rule.tokens) {
                    for (int i = 0; i < prod.size(); i++) {
                        String B = prod.get(i);
                        if (nonTerminals.contains(B)) {
                            Set<String> followB = follow.get(B);
                            Set<String> trailer = new HashSet<>();

                            boolean epsilonInTrailer = true;
                            for (int j = i + 1; j < prod.size(); j++) {
                                String beta = prod.get(j);
                                Set<String> firstBeta;

                                if (nonTerminals.contains(beta)) {
                                    firstBeta = new HashSet<>(first.get(beta));
                                } else {
                                    firstBeta = new HashSet<>();
                                    firstBeta.add(beta);
                                }

                                trailer.addAll(firstBeta);
                                if (!firstBeta.contains("EPSILON")) {
                                    epsilonInTrailer = false;
                                    break;
                                }
                            }

                            if (i + 1 == prod.size() || epsilonInTrailer) {
                                trailer.addAll(follow.get(rule.name));
                            }

                            trailer.remove("EPSILON");
                            if (followB.addAll(trailer))
                                changed = true;
                        }
                    }
                }
            }
        } while (changed);

        // Guardar resultados
        saveAsJson("tokens.json", allTokens);
        saveAsJson("terminals_and_nonterminals.json", Map.of(
                "terminals", terminals,
                "nonTerminals", nonTerminals
        ));
        saveAsJson("first_and_follow.json", Map.of(
                "first", first,
                "follow", follow
        ));

        System.out.println("✅ JSONS Generats");
    }

    // Método modificado para leer el JSON correctamente usando Gson
    static List<Rule> readGrammar(String path) throws IOException {
        Gson gson = new Gson();
        try (Reader reader = new FileReader(path)) {
            return gson.fromJson(reader, new TypeToken<List<Rule>>(){}.getType());
        }
    }

    static void saveAsJson(String fileName, Object data) throws IOException {
        FileWriter writer = new FileWriter("src/JSON Files/" + fileName);
        writer.write(toJson(data));
        writer.close();
    }

    static String toJson(Object obj) {
        if (obj instanceof Set<?> set) {
            return setToJson(set);
        } else if (obj instanceof Map<?, ?> map) {
            return mapToJson(map);
        }
        return "\"Unsupported\"";
    }

    static String setToJson(Set<?> set) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object item : set) {
            if (!first)
                sb.append(",");
            sb.append("\"").append(item).append("\"");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    static String mapToJson(Map<?, ?> map) {
        StringBuilder sb = new StringBuilder("{\n");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first)
                sb.append(",\n");
            sb.append("\"").append(entry.getKey()).append("\": ");
            Object value = entry.getValue();
            if (value instanceof Set<?> s) {
                sb.append(setToJson(s));
            } else if (value instanceof Map<?, ?> m) {
                sb.append(mapToJson(m));
            } else {
                sb.append("\"").append(value).append("\"");
            }
            first = false;
        }
        sb.append("\n}");
        return sb.toString();
    }
}
