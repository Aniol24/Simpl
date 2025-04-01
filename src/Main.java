import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <path-to-grammar-file>");
            System.exit(1);
        }
    }
}