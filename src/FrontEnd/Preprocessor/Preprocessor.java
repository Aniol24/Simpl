package FrontEnd.Preprocessor;

import java.util.regex.Pattern;

public class Preprocessor {

    /**
     * Elimina comentarios en el código fuente.
     *
     * @param code El código fuente como una cadena de texto.
     * @return El código fuente sin comentarios.
     */
    public static String removeComments(String code) {

        // Elimina comentarios multilínea que empiezan y terminan con '##'
        Pattern multiLinePattern = Pattern.compile("(?s)##.*?##");
        code = multiLinePattern.matcher(code).replaceAll(match -> {
            String m = match.group();
            int lineCount = m.length() - m.replace("\n", "").length();
            return "\n".repeat(lineCount);
        });

        // Elimina comentarios de una sola línea que empiezan con '#'
        code = code.replaceAll("(?m)#.*$", "");
        return code;
    }
}