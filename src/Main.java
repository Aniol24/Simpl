import Preprocessor.Preprocessor;
import Syntax.Parser;
import Lexicon.Scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {

        Path codePath = Paths.get("src/Code/test.smpl");

        String codeContent = "";

        try {
            codeContent = Files.readString(codePath);
        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
            return;
        }

        String pureCode = Preprocessor.removeComments(codeContent);

        Scanner scanner = new Scanner(pureCode);
        Parser parser = new Parser(scanner);


        try {
            parser.parse();
        } catch (Exception e) {
            System.err.println("Error parsing the code: " + e.getMessage());
        }
    }
}