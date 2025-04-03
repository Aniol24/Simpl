package Lexicon;

public class Scanner {

    private final String[] lines;
    private int currentLine;
    private int currentPosition;
    private int currentIndent;

    public Scanner (String code) {
        this.lines = code.split("\n");
        this.currentLine = 1;
        this.currentPosition = 0;
        this.currentIndent = 0;
    }

    public String nextToken() throws Exception {
        // Retornem END tokens pendents si n'hi ha
        while (currentLine <= lines.length) {
            String line = getCurrentLine();

            // Saltem linies buides
            if (line.trim().isEmpty()) {
                currentLine++;
                currentPosition = 0;
                continue;
            }

            // Comprovació d'indentació a l'inici de línia
            if (currentPosition == 0) {
                int indent = countLeadingTabs(line);
                if (indent < currentIndent) {
                    currentIndent--;
                    return "END";
                } else if (indent > currentIndent) {
                    // Si la indentació augmenta sense START previ, llencem excepció
                    throw new Exception("Indentation error at line " + currentLine + ". Unexpected indent increase.");
                }
            }

            // Identificació de tokens
            if (currentPosition < line.length()) {
                char ch = line.charAt(currentPosition);
                // Saltem espais i tabulacions
                if (ch == ' ' || ch == '\t') {
                    currentPosition++;
                    continue;
                }

                StringBuilder lexeme = new StringBuilder();

                // Identificadors
                if (Character.isLetter(ch) || ch == '_') {
                    while (currentPosition < line.length() && Character.isLetterOrDigit(line.charAt(currentPosition))) {
                        lexeme.append(line.charAt(currentPosition));
                        currentPosition++;
                    }
                    if (lexeme.toString().equals("int")) {
                        return "INT";
                    } else if (lexeme.toString().equals("flt")) {
                        return "FLOAT";
                    } else if (lexeme.toString().equals("chr")) {
                        return "CHAR";
                    } else if (lexeme.toString().equalsIgnoreCase("null")) {
                        return "NULL";
                    } else if (lexeme.toString().equals("if")) {
                        return "IF";
                    } else if (lexeme.toString().equals("elif")) {
                        return "ELIF";
                    } else if (lexeme.toString().equals("else")) {
                        return "ELSE";
                    } else if (lexeme.toString().equals("while")) {
                        return "WHILE";
                    } else if (lexeme.toString().equals("for")) {
                        return "FOR";
                    } else if (lexeme.toString().equals("do")) {
                        return "DO";
                    } else if (lexeme.toString().equals("until")) {
                        return "UNTIL";
                    } else if (lexeme.toString().equals("return")) {
                        return "RETURN";
                    } else if (lexeme.toString().equals("fn")) {
                        return "FN";
                    } else if (lexeme.toString().equals("main")) {
                        return "MAIN";
                    } else {
                        return "ID"; // Considerar el FUNCTION_NAME després de "fn"
                    }
                }

                // Nombres
                else if (Character.isDigit(ch)) {
                    // Read the integer part
                    while (currentPosition < line.length() && Character.isDigit(line.charAt(currentPosition))) {
                        lexeme.append(line.charAt(currentPosition));
                        currentPosition++;
                    }
                    // Check for a fractional part
                    if (currentPosition < line.length() && line.charAt(currentPosition) == '.') {
                        // Peek ahead to ensure there's at least one digit after the dot
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

                // Símbols
                else {
                    lexeme.append(ch);
                    currentPosition++;

                    if (ch == ':') {
                        String remaining = getCurrentLine().substring(currentPosition).trim();
                        if (!remaining.isEmpty()) {
                            // Si hi ha codi després del ':', llencem excepció
                            throw new Exception("Error at line " + currentLine + ": unexpected code after ':'");
                        }
                        currentIndent++;
                        currentLine++;
                        currentPosition = 0;
                        return "START";
                    } else if (ch == '=') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "==";
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '>') {
                            currentPosition++;
                            return "=>";
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '<') {
                            currentPosition++;
                            return "=<";
                        } else {
                            return "=";
                        }
                    } else if (ch == '!') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "!=";
                        } else {
                            return "!";
                        }
                    } else if (ch == '<') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "<=";
                        } else {
                            return "<";
                        }
                    } else if (ch == '>') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return ">=";
                        } else {
                            return ">";
                        }
                    } else if (ch == '+') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '+') {
                            currentPosition++;
                            return "++";
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "+=";
                        } else {
                            return "+";
                        }
                    } else if (ch == '-') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '>') {
                            currentPosition++;
                            return "->";
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '-') {
                            currentPosition++;
                            return "--";
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "-=";
                        } else {
                            return "-";
                        }
                    } else if (ch == '*') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '*') {
                            currentPosition++;
                            return "**";
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "*=";
                        } else {
                            return "*";
                        }
                    } else if (ch == '/') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "/=";
                        } else {
                            return "/";
                        }
                    } else if (ch == '%') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return "%=";
                        } else {
                            return "%";
                        }
                    } else if (ch == ',') {
                        return ",";
                    } else if (ch == '(') {
                        return "(";
                    } else if (ch == ')') {
                        return ")";
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
            // End of current line.
            currentLine++;
            currentPosition = 0;
            return "EOL";
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
