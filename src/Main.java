import FrontEnd.TAC.CodeGenerator;
import FrontEnd.TAC.TACInstruction;
import FrontEnd.Lexicon.Scanner;
import FrontEnd.Preprocessor.Preprocessor;
import FrontEnd.Semantics.SemanticAnalyzer;
import FrontEnd.Syntax.Parser;
import Global.Errors.ErrorHandler;
import Global.SymbolTable.SymbolTable;
import BackEnd.MIPSCodeGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

//        TODO: BOOLEAN logic goes left to right. When we have (x < y & y > 0) it should be ((x < y) & (y > 0)). Else it does (((x < y) & y) > 0)
//        TODO: FIX SINCE (x < y & y > 0) isn't calculated correctly in the TAC
//        Path codePath = Paths.get("src/Files/Examples/12_LogicalOperators.smpl");
//        TODO: FIX TAC code generation since there's a semantic error which thinks that we have booleans(we decided they don't exist in our language)
//        Path codePath = Paths.get("src/Files/Examples/13_FnCallCondition.smpl");
//        TODO: FIX TAC code generation since there's a semantic error which thinks that we have booleans(we decided they don't exist in our language)
//        TODO: FIX TAC code generation (x < y & y > 0) isn't calculated correctly in the TAC
//        TODO: If the condition is  ((x < y) & (y > 0)) then it's calculated correctly in the TAC
//        Path codePath = Paths.get("src/Files/Examples/19_MultipleParamsFn.smpl");
//        TODO: Fix since with "loop" semantic errors pop up and without "loop" it just skips the "for"
//        Path codePath = Paths.get("src/Files/Examples/22_NestedLoops.smpl");

public class Main {
    public static void main(String[] args) {
        Path codePath = Paths.get("src/Files/Examples/fibonacci.smpl");

        ErrorHandler errorHandler = new ErrorHandler();
        SymbolTable symbolTable = new SymbolTable();

        String codeContent;
        try {
            codeContent = Files.readString(codePath);
        } catch (IOException e) {
            System.err.println("Could not read file: " + e.getMessage());
            return;
        }

        String pureCode = Preprocessor.removeComments(codeContent);
        Scanner scanner  = new Scanner(pureCode, errorHandler);
        Parser parser    = new Parser(scanner, errorHandler);
        parser.parse();

        SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(parser.getParseTreeRoot(), symbolTable, errorHandler);
        semanticAnalyzer.analyze();

        semanticAnalyzer.printSymbolTableContents();

        if (errorHandler.hasErrors()) {
            errorHandler.printErrors();
            return;
        }

        CodeGenerator gen = new CodeGenerator();
        List<TACInstruction> code = gen.generate(parser.getParseTreeRoot());
        for (int i = 0; i < code.size(); i++) {
            System.out.println(code.get(i).toString());
        }

        //Generate MIPS
        MIPSCodeGenerator mipsCodeGenerator = new MIPSCodeGenerator();
        mipsCodeGenerator.generate(code);
    }
}
