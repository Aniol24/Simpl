package Preprocessor;

import java.util.regex.Pattern;

public class Preprocessor {

    /**
     * Removes single-line comments and multi-line comments from the given text.
     *
     * @param code The input text from which comments should be removed.
     * @return The text with comments removed.
     */
    public static String removeComments(String code) {

        // Remove multi-line comments that start and end with "##"
        Pattern multiLinePattern = Pattern.compile("(?s)##.*?##");
        code = multiLinePattern.matcher(code).replaceAll(match -> {
            String m = match.group();
            int lineCount = m.length() - m.replace("\n", "").length();
            return "\n".repeat(lineCount);
        });

        // Remove single line comments that start with '#'
        code = code.replaceAll("(?m)#.*$", "");
        return code;
    }
}