public class Preprocessor {

    /**
     * Removes single-line comments and multi-line comments from the given text.
     * Single-line comments start with a '#' and extend until the end of the line.
     * Multi-line comments are enclosed between two "##" markers.
     *
     * @param text The input text from which comments should be removed.
     * @return The text with comments removed.
     */
    public static String removeComments(String text) {
        // Remove multi-line comments that start and end with "##"
        // The (?s) flag makes '.' match newlines.
        text = text.replaceAll("(?s)##.*?##", "");

        // Remove single line comments that start with '#' and go until the end of the line.
        // The (?m) flag makes '^' and '$' work per line.
        text = text.replaceAll("(?m)#.*$", "");
        return text;
    }

    public static void main(String[] args) {
        // Example text containing both types of comments.
        String text = "This is code "
                + "# This is a single-line comment\n"
                + "## Test ##\n"
                + "This is not commented\n"
                + "## Start of a multi-line comment\n"
                + "Line inside multi-line comment\n"
                + "Another line inside multi-line comment\n"
                + "## End of the multi-line comment\n"
                + "Last line with a # single-line comment at the end";

        System.out.println("Original Text:\n" + text);
        String processedText = removeComments(text);
        System.out.println("\nProcessed Text:\n" + processedText);
    }
}