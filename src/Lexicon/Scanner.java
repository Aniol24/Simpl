package Lexicon;

public class Scanner {

    private final String[] lines;
    private int currentLine;
    private int currentPosition;
    private int currentIndent;
    private boolean expectFunctionName; // Indica que el próximo identificador es el nombre de una función

    public Scanner(String code) {
        this.lines = code.split("\n");
        this.currentLine = 1;
        this.currentPosition = 0;
        this.currentIndent = 0;
        this.expectFunctionName = false;
    }

    public String nextToken() throws Exception {
        // Si ya se han procesado todas las líneas pero quedan bloques abiertos,
        // devolvemos los tokens "END" pendientes.
        if (currentLine > lines.length && currentIndent > 0) {
            currentIndent--;
            return "END";
        }

        while (currentLine <= lines.length) {
            String line = getCurrentLine();

            // Salta líneas vacías.
            if (line.trim().isEmpty()) {
                currentLine++;
                currentPosition = 0;
                continue;
            }

            // Comprobación de indentación al inicio de la línea.
            if (currentPosition == 0) {
                int indent = countLeadingTabs(line);
                if (indent < currentIndent) {
                    currentIndent--;
                    return "END";
                } else if (indent > currentIndent) {
                    throw new Exception("Indentation error at line " + currentLine + ". Unexpected indent increase.");
                }
            }

            if (currentPosition < line.length()) {
                char ch = line.charAt(currentPosition);
                // Salta espacios y tabulaciones.
                if (ch == ' ' || ch == '\t') {
                    currentPosition++;
                    continue;
                }

                StringBuilder lexeme = new StringBuilder();

                // Procesamiento de identificadores
                if (Character.isLetter(ch) || ch == '_') {
                    while (currentPosition < line.length() && Character.isLetterOrDigit(line.charAt(currentPosition))) {
                        lexeme.append(line.charAt(currentPosition));
                        currentPosition++;
                    }
                    String word = lexeme.toString();

                    // Si se esperaba el nombre de una función, se procesa primero.
                    if (expectFunctionName) {
                        expectFunctionName = false;
                        if (word.equals("main"))
                            return "MAIN";
                        else
                            return "ID";
                    }

                    // Procesamiento de palabras reservadas
                    if (word.equals("int")) {
                        return "INT";
                    } else if (word.equals("flt")) {
                        return "FLOAT";
                    } else if (word.equals("chr")) {
                        return "CHAR";
                    } else if (word.equals("if")) {
                        return "IF";
                    } else if (word.equals("elif")) {
                        return "ELIF";
                    } else if (word.equals("else")) {
                        return "ELSE";
                    } else if (word.equals("while")) {
                        return "WHILE";
                    } else if (word.equals("for")) {
                        return "FOR";
                    } else if (word.equals("do")) {
                        return "DO";
                    } else if (word.equals("until")) {
                        return "UNTIL";
                    } else if (word.equals("return")) {
                        return "RETURN";
                    } else if (word.equals("fn")) {
                        // Al leer "fn" se activa la bandera para que el próximo identificador sea el nombre de la función.
                        expectFunctionName = true;
                        return "FN";
                    } else if (word.equals("main")) {
                        return "MAIN";
                    } else {
                        return "ID";
                    }
                }
                // Procesamiento de números
                else if (Character.isDigit(ch)) {
                    while (currentPosition < line.length() && Character.isDigit(line.charAt(currentPosition))) {
                        lexeme.append(line.charAt(currentPosition));
                        currentPosition++;
                    }
                    if (currentPosition < line.length() && line.charAt(currentPosition) == '.') {
                        if (currentPosition + 1 < line.length() && Character.isDigit(line.charAt(currentPosition + 1))) {
                            lexeme.append('.');
                            currentPosition++;
                            while (currentPosition < line.length() && Character.isDigit(line.charAt(currentPosition))) {
                                lexeme.append(line.charAt(currentPosition));
                                currentPosition++;
                            }
                            return "FLOAT_LITERAL";
                        }
                    }
                    return "INTEGER_LITERAL";
                }
                // Procesamiento de símbolos
                else {
                    lexeme.append(ch);
                    currentPosition++;

                    if (ch == ':') {
                        String remaining = getCurrentLine().substring(currentPosition).trim();
                        if (!remaining.isEmpty()) {
                            throw new Exception("Error at line " + currentLine + ": unexpected code after ':'");
                        }
                        currentIndent++;
                        currentLine++;
                        currentPosition = 0;
                        return "START";
                    } else if (ch == '=') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "EQUALS";
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '>') {
                            currentPosition++;
                            return "GREATER";
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '<') {
                            currentPosition++;
                            return "GREATER";
                        } else {
                            return "EQ";
                        }
                    } else if (ch == '!') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "NOT_EQUAL";
                        } else {
                            return "NOT";
                        }
                    } else if (ch == '<') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "LOWER_EQUAL";
                        } else {
                            return "LOWER";
                        }
                    } else if (ch == '>') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "GREATER_EQUAL";
                        } else {
                            return "GREATER";
                        }
                    } else if (ch == '+') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '+') {
                            currentPosition++;
                            return "INC";
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "+=";
                        } else {
                            return "SUM";
                        }
                    } else if (ch == '-') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '>') {
                            currentPosition++;
                            return "ARROW";
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '-') {
                            currentPosition++;
                            return "DEC";
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "-=";
                        } else {
                            return "SUB";
                        }
                    } else if (ch == '*') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '*') {
                            currentPosition++;
                            return "POW";
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "*=";
                        } else {
                            return "MULT";
                        }
                    } else if (ch == '/') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "/=";
                        } else {
                            return "DIV";
                        }
                    } else if (ch == '%') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "%=";
                        } else {
                            return "MOD";
                        }
                    } else if (ch == ',') {
                        return "COMA";
                    } else if (ch == '(') {
                        return "PO";
                    } else if (ch == ')') {
                        return "PT";
                    } else if (ch == '[') {
                        return "[";
                    } else if (ch == ']') {
                        return "]";
                    } else if (ch == '"') {
                        return "\"";
                    } else if (ch == '\'') {
                        return "'";
                    } else if (ch == '&') {
                        return "AND";
                    } else if (ch == '|') {
                        return "OR";
                    }
                }
            }
            // Fin de la línea actual: se retorna EOL y se pasa a la siguiente línea.
            currentLine++;
            currentPosition = 0;
            return "EOL";
        }

        // Si ya no quedan líneas pero aún quedan bloques abiertos, se retornan los tokens "END".
        if (currentIndent > 0) {
            currentIndent--;
            return "END";
        }
        return "EOF";
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

    private String getCurrentLine() {
        if (currentLine - 1 < lines.length) {
            return lines[currentLine - 1];
        }
        return "No lines left";
    }
}
