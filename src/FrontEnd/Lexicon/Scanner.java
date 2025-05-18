package FrontEnd.Lexicon;

import Global.Errors.ErrorHandler;

public class Scanner {

    /**
     * ErrorHandler per a gestionar errors
     */
    private final ErrorHandler errorHandler;
    /**
     * Conté el codi font a analitzar
     */
    private final String[] lines;
    /**
     * Conté la línia actual a analitzar
     */
    private int currentLine;
    /**
     * Conté la posició actual a la línia
     */
    private int currentPosition;
    /**
     * Conté la indentació actual
     */
    private int currentIndent;

    /**
     * Constructor de la classe Scanner
     *
     * @param code         Codi font a analitzar
     * @param errorHandler ErrorHandler per a gestionar errors
     */
    public Scanner(String code, ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;

        this.lines = code.split("\n");
        this.currentLine = 1;
        this.currentPosition = 0;
        this.currentIndent = 0;
    }

    /**
     * Retorna el següent token del codi font
     *
     * @return Token següent
     */
    public Token nextToken() {
        // Si ja no queden línies, però encara hi ha blocs oberts, es retorna els tokens "END" pendents
        if (currentLine > lines.length && currentIndent > 0) {
            currentIndent--;
            return new Token("END", "END", currentLine);
        }

        while (currentLine <= lines.length) {
            String line = getCurrentLine();

            // Salta línees buides
            if (line.trim().isEmpty()) {
                currentLine++;
                currentPosition = 0;
                continue;
            }

            // Comprovem indentació
            if (currentPosition == 0) {
                int indent = countLeadingTabs(line);
                if (indent < currentIndent) {
                    currentIndent--;
                    return new Token("END", "END", currentLine);
                } else if (indent > currentIndent) {
                    errorHandler.recordError("Indentation error at line " + currentLine + ". Unexpected indent increase.", currentLine);
                }
            }

            if (currentPosition < line.length()) {
                char ch = line.charAt(currentPosition);
                // Salta espais i tabulacions
                if (ch == ' ' || ch == '\t') {
                    currentPosition++;
                    continue;
                }

                StringBuilder lexeme = new StringBuilder();

                // Processament d'identificadors i paraules reservades
                if (Character.isLetter(ch) || ch == '_') {
                    while (currentPosition < line.length() && (Character.isLetterOrDigit(line.charAt(currentPosition)) || (line.charAt(currentPosition) == '_'))) {
                        lexeme.append(line.charAt(currentPosition));
                        currentPosition++;
                    }
                    String word = lexeme.toString();

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
                // Processem números enters i decimals
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
                // Processament de símbols
                else {
                    lexeme.append(ch);
                    currentPosition++;

                    if (ch == ':') {
                        String remaining = getCurrentLine().substring(currentPosition).trim();
                        if (!remaining.isEmpty()) {
                            errorHandler.recordError("Error at line " + currentLine + ": unexpected code after ':'", currentLine);
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
                                errorHandler.recordError("Error at line " + currentLine + ": missing closing single quote for character literal.", currentLine);
                            }
                        } else {
                            errorHandler.recordError("Error at line " + currentLine + ": invalid or empty character literal.", currentLine);
                        }
                    } else if (ch == '=') {
                        if (currentPosition < line.length() && line.charAt(currentPosition) == '=') {
                            currentPosition++;
                            return new Token("EQUALS", "==", currentLine);
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '>') {
                            currentPosition++;
                            return new Token("GREATER", ">=", currentLine);
                        } else if (currentPosition < line.length() && line.charAt(currentPosition) == '<') {
                            currentPosition++;
                            return new Token("LOWER", "<=", currentLine);
                        } else {
                            return new Token("EQ", "=", currentLine);
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
            // Fi de la línia actual, es retorna un token "EOL" i es passa a la següent línia
            currentLine++;
            currentPosition = 0;
            return new Token("EOL", currentLine);
        }

        // Si ja no queden línies, però encara hi ha blocs oberts, es retorna els tokens "END" pendents
        if (currentIndent > 0) {
            currentIndent--;
            return new Token("END", currentLine);
        }
        return new Token("EOF", currentLine);
    }

    /**
     * Compta el nombre de tabulacions i espais al principi d'una línia
     *
     * @param line Línia a analitzar
     * @return Nombre de tabulacions i espais al principi de la línia
     */
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

    /**
     * Retorna la línia actual a analitzar
     *
     * @return Línia actual
     */
    private String getCurrentLine() {
        if (currentLine - 1 < lines.length) {
            return lines[currentLine - 1];
        }
        return "No lines left";
    }
}
