import BackEnd.CodeGenerator;
import BackEnd.TACInstruction;
import FrontEnd.Lexicon.Scanner;
import FrontEnd.Preprocessor.Preprocessor;
import FrontEnd.Semantics.SemanticAnalyzer;
import FrontEnd.Syntax.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Path codePath = Paths.get("src/Files/Examples/code.smpl");
        String codeContent;
        try {
            codeContent = Files.readString(codePath);
        } catch (IOException e) {
            System.err.println("Error leyendo el fichero: " + e.getMessage());
            return;
        }

        String pureCode = Preprocessor.removeComments(codeContent);
        Scanner scanner  = new Scanner(pureCode);
        Parser parser    = new Parser(scanner);
        try {
            parser.parse();
        } catch (Exception e) {
            System.err.println("Error en parseo: " + e.getMessage());
            return;
        }

        new SemanticAnalyzer(parser.getParseTreeRoot());

        CodeGenerator gen = new CodeGenerator();
        try {
            List<TACInstruction> code = gen.generate(parser.getParseTreeRoot());
            for (int i = 0; i < code.size(); i++) {
                System.out.println(code.get(i).toString());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }
}
