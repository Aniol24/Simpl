package FrontEnd.Lexicon;

public class Scanner {

    private final String[] lines;
    private int currentLine;
    private int currentPosition;
    private int currentIndent;

    public Scanner(String code) {
        this.lines = code.split("\n");
        this.currentLine = 1;
        this.currentPosition = 0;
        this.currentIndent = 0;
    }

    public Token nextToken() throws Exception {
        // Si ya se han procesado todas las líneas, pero quedan bloques abiertos,
        // devolvemos los tokens "END" pendientes.
        if (currentLine > lines.length && currentIndent > 0) {
            currentIndent--;
            return new Token("END", currentLine);
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
                    return new Token("END", currentLine);
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

                    // Procesamiento de palabras reservadas
                    switch (word) {
                        case "main" -> {
                            return new Token("MAIN", word, currentLine);
                        }
                        case "int" -> {
                            return new Token("INT", word, currentLine);
                        }
                        case "flt" -> {
                            return new Token("FLOAT", word, currentLine);
                        }
                        case "chr" -> {
                            return new Token("CHAR", word, currentLine);
                        }
                        case "if" -> {
                            return new Token("IF", word, currentLine);
                        }
                        case "elif" -> {
                            return new Token("ELIF", word, currentLine);
                        }
                        case "else" -> {
                            return new Token("ELSE", word, currentLine);
                        }
                        case "while" -> {
                            return new Token("WHILE", word, currentLine);
                        }
                        case "for" -> {
                            return new Token("FOR", word, currentLine);
                        }
                        case "do" -> {
                            return new Token("DO", word, currentLine);
                        }
                        case "until" -> {
                            return new Token("UNTIL", word, currentLine);
                        }
                        case "return" -> {
                            return new Token("RETURN", word, currentLine);
                        }
                        case "fn" -> {
                            return new Token("FN", word, currentLine);
                        }
                        default -> {
                            return new Token("ID", word, currentLine);
                        }
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
                            return new Token("FLOAT_LITERAL", lexeme.toString(), currentLine);
                        }
                    }
                    return new Token("INTEGER_LITERAL", lexeme.toString(), currentLine);
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
                        return new Token("START", ":", currentLine);
                    } else if (ch == '\'') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) != '\'') {
                            currentPosition++;
                            if (currentPosition < line.length() && line.charAt(currentPosition) == '\'') {
                                currentPosition++;
                                return new Token("CHAR_LITERAL", String.valueOf(line.charAt(currentPosition - 2)), currentLine);
                            } else {
                                throw new Exception("Error at line " + currentLine + ": missing closing single quote for character literal.");
                            }
                        } else {
                            throw new Exception("Error at line " + currentLine + ": invalid or empty character literal.");
                        }
                    } else if (ch == '=') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return new Token("EQUALS", currentLine);
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '>') {
                            currentPosition++;
                            return new Token("GREATER", currentLine);
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '<') {
                            currentPosition++;
                            return new Token("LOWER", currentLine);
                        } else {
                            return new Token("EQ", currentLine);
                        }
                    } else if (ch == '!') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return new Token("NOT_EQUAL", currentLine);
                        } else {
                            return new Token("NOT", currentLine);
                        }
                    } else if (ch == '<') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return new Token("LOWER_EQUAL", currentLine);
                        } else {
                            return new Token("LOWER", currentLine);
                        }
                    } else if (ch == '>') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return new Token("GREATER_EQUAL", currentLine);
                        } else {
                            return new Token("GREATER", currentLine);
                        }
                    } else if (ch == '+') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '+') {
                            currentPosition++;
                            return new Token("INC", currentLine);
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return new Token("+=", currentLine);
                        } else {
                            return new Token("SUM", currentLine);
                        }
                    } else if (ch == '-') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '>') {
                            currentPosition++;
                            return new Token("ARROW", currentLine);
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '-') {
                            currentPosition++;
                            return new Token("DEC", currentLine);
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return new Token("-=", currentLine);
                        } else {
                            return new Token("SUB", currentLine);
                        }
                    } else if (ch == '*') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '*') {
                            currentPosition++;
                            return new Token("POW", currentLine);
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return new Token("*=", currentLine);
                        } else {
                            return new Token("MULT", currentLine);
                        }
                    } else if (ch == '/') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return new Token("/=", currentLine);
                        } else {
                            return new Token("DIV", currentLine);
                        }
                    } else if (ch == '%') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return new Token("%=", currentLine);
                        } else {
                            return new Token("MOD", currentLine);
                        }
                    } else if (ch == ',') {
                        return new Token("COMA", currentLine);
                    } else if (ch == '(') {
                        return new Token("PO", currentLine);
                    } else if (ch == ')') {
                        return new Token("PT", currentLine);
                    } else if (ch == '[') {
                        return new Token("[", currentLine);
                    } else if (ch == ']') {
                        return new Token("]", currentLine);
                    } else if (ch == '"') {
                        return new Token("\"", currentLine);
                    } else if (ch == '&') {
                        return new Token("AND", currentLine);
                    } else if (ch == '|') {
                        return new Token("OR", currentLine);
                    }
                }
            }
            // Fin de la línea actual: se retorna EOL y se pasa a la siguiente línea.
            currentLine++;
            currentPosition = 0;
            return new Token("EOL", currentLine);
        }

        // Si ya no quedan líneas pero aún quedan bloques abiertos, se retornan los tokens "END".
        if (currentIndent > 0) {
            currentIndent--;
            return new Token("END", currentLine);
        }
        return new Token("EOF", currentLine);
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
