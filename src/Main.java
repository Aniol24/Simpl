import FrontEnd.Preprocessor.Preprocessor;
import FrontEnd.Semantics.SemanticAnalyzer;
import Global.SymbolTable.SymbolTable;
import FrontEnd.Syntax.Parser;
import FrontEnd.Lexicon.Scanner;
import Global.Errors.ErrorHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            ErrorHandler.reportError("Error interno inesperado: " + e.getMessage());
            System.exit(1);
        });

        Path codePath = Paths.get("src/Files/Examples/code.smpl");

        String codeContent = "";

        try {
            codeContent = Files.readString(codePath);
        } catch (IOException e) {
            ErrorHandler.reportError("No se pudo leer el fichero: " + e.getMessage());
            System.exit(1);
        }

        String pureCode = Preprocessor.removeComments(codeContent);
        Scanner scanner = new Scanner(pureCode);
        Parser parser = new Parser(scanner);
        try {
            parser.parse();
        } catch (Exception e) {
            ErrorHandler.reportError("Error al parsear el código: " + e.getMessage());
            System.exit(1);
        }

        try {
            SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(parser.getParseTreeRoot());
        } catch (Exception e) {
            ErrorHandler.reportError("Error en análisis semántico: " + e.getMessage());
            System.exit(1);
        }

        System.out.println("Análisis completado con éxito.");
    }
}