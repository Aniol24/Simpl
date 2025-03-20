import java.util.Stack;

public class Tabulator {

    public static void main(String[] args) {
        String code = "fn fibonacci(int -> n) -> int:\n" +
                "\tif (n <= 0) :\n" +
                "\t    if (n == 1) :\n" +
                "\t\t\treturn n\n" +
                "\treturn fibonacci(n - 1) + fibonacci(n - 2)\n" +
                "\n" +
                "fn main:\n" +
                "\tint -> n = 4\n" +
                "\tfibonacci(n)\n" +
                "\treturn";

        try {
            String processedCode = processCode(code);
            System.out.println("Processed Code:\n" + processedCode);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public static String processCode(String code) throws Exception {
        String[] lines = code.split("\n");
        Stack<Integer> indentStack = new Stack<>();
        indentStack.push(0); // Nivell d'indentació inicial
        StringBuilder processedCode = new StringBuilder();

        for (String line : lines) {
            // Ignorar línies buides
            if (line.trim().isEmpty()) {
                processedCode.append(line).append("\n");
                continue;
            }

            // Comptar el nombre de tabulacions al començament de la línia
            int currentIndent = countLeadingTabs(line);

            // Gestionar la reducció del nivell d'indentació
            while (currentIndent < indentStack.peek()) {
                indentStack.pop();
                processedCode.append("END\n");
            }

            // Gestionar l'augment del nivell d'indentació
            if (currentIndent > indentStack.peek()) {
                throw new Exception("Error d'indentació a la línia: " + line);
            }

            // Afegir "START" al final de les línies que acaben amb ":" reemplaçant-lo
            if (line.trim().endsWith(":")) {
                processedCode.append(line.replace(":", " START")).append("\n");
                indentStack.push(currentIndent + 1);
            } else {
                processedCode.append(line).append("\n");
            }
        }

        // Tancar qualsevol bloc obert restant amb "END"
        while (indentStack.size() > 1) {
            indentStack.pop();
            processedCode.append("END\n");
        }

        return processedCode.toString();
    }

    private static int countLeadingTabs(String line) {
        int count = 0;
        int spaceCount = 0;
        for (char c : line.toCharArray()) {
            if (c == '\t') {
                count++;
                spaceCount = 0;
            } else if (c == ' ') {
                spaceCount++;
                if (spaceCount == 4) {
                    count++;
                    spaceCount = 0;
                }
            } else {
                break;
            }
        }
        return count;
    }
}