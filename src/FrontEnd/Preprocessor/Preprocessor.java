package FrontEnd.Preprocessor;

import java.nio.file.Path;
import java.util.regex.Pattern;

public class Preprocessor {

    /**
     * Elimina comentaris del codi font.
     *
     * @param code El codi font com a cadena de text.
     * @return El codi sense comentaris.
     */
    public static String removeComments(String code) {

        // Elimina comentaris multilínia que comencen i acaben amb '##'
        Pattern multiLinePattern = Pattern.compile("(?s)##.*?##"); // Fem ús de regex
        code = multiLinePattern.matcher(code).replaceAll(match -> {
            String m = match.group();
            int lineCount = m.length() - m.replace("\n", "").length();
            return "\n".repeat(lineCount);
        });

        // Elimina comentaris d'una sola línia que comencen amb '#'
        code = code.replaceAll("(?m)#.*$", "");
        return code;
    }
}