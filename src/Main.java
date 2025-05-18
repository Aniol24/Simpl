import Utilities.PathReader;
import FrontEnd.TAC.TACCodeGenerator;
import FrontEnd.Lexicon.Scanner;
import FrontEnd.Preprocessor.Preprocessor;
import FrontEnd.Semantics.SemanticAnalyzer;
import FrontEnd.Syntax.Parser;
import Global.Errors.ErrorHandler;
import Global.SymbolTable.SymbolTable;
import BackEnd.MIPSCodeGenerator;

import java.nio.file.Paths;

public class Main {

    private static final String FILE_PATH = "src/Files/Codes/fibonacci.smpl";

    public static void main(String[] args) {

        // Declarem l'ErrorHandler i la taula de símbols
        ErrorHandler errorHandler = new ErrorHandler();
        SymbolTable symbolTable = new SymbolTable();

        // Llegim el codi font, si no existeix el fitxer, sortim
        String codeContent = PathReader.readFile(Paths.get(FILE_PATH));
        if (codeContent == null) {
            return;
        }

        // Preprocessament del codi font. Esborrem els comentaris
        String pureCode = Preprocessor.removeComments(codeContent);

        // Preparem el lexer i el parser
        Scanner scanner  = new Scanner(pureCode, errorHandler);
        Parser parser    = new Parser(scanner, errorHandler);
        parser.parse();

        parser.printParseTree(); // Mostrem l'arbre sintàctic (debug)

        // Analitzem el codi font semànticament
        SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(parser.getParseTreeRoot(), symbolTable, errorHandler);
        semanticAnalyzer.analyze();

        semanticAnalyzer.printSymbolTableContents(); // Mostrem la taula de símbols (debug)

        // Si s'ha registrat errors, no generem el codi màquina
        if (errorHandler.hasErrors()) {
            errorHandler.printErrors();
            return;
        }

        // Generem TAC
        TACCodeGenerator tacCodeGenerator = new TACCodeGenerator(parser.getParseTreeRoot());
        tacCodeGenerator.generate();

        tacCodeGenerator.printTACCode(); // Mostrem el TAC generat (debug)

        // Generem MIPS
        MIPSCodeGenerator mipsCodeGenerator = new MIPSCodeGenerator(tacCodeGenerator.getCode(), symbolTable);
        mipsCodeGenerator.setCommentTAC(false); // Per a mostrar les instruccions TAC al codi MIPS com a comentaris
        mipsCodeGenerator.generate();
    }
}
