import Parser.Parser;
import Parser.ParsingTable;

import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        /*if (args.length != 1) {
            System.err.println("Usage: java Main <path-to-grammar-file>");
            System.exit(1);
        }
         */
        List<String> tokens = List.of(
                "FN", "MAIN", "START",
                "INT", "ARROW", "ID", "EQ","INTEGER_LITERAL", "EOL",
                "RETURN", "EOL",
                "END"
        );

        Parser parser = new Parser(tokens);
    }
}