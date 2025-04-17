package FrontEnd.Semantics;

import FrontEnd.Syntax.TreeNode;
import Global.SymbolTable.*;

import java.util.ArrayList;
import java.util.List;

public class SemanticAnalyzer {
    private SymbolTable symbolTable;
    private List<String> semanticErrors;
    private Symbol currentFunction; // Track the function being analyzed for return checks

    public SemanticAnalyzer(TreeNode root) {
        this.symbolTable = new SymbolTable();
        this.semanticErrors = new ArrayList<>();
        this.currentFunction = null;
        System.out.println("Starting semantic analysis...");
        analyzeNode(root); // Start the analysis from the root
        System.out.println("Semantic analysis completed.");
        if (!semanticErrors.isEmpty()) {
            System.err.println("\n--- Semantic Errors Found ---");
            for (String error : semanticErrors) {
                System.err.println(error);
            }
            System.err.println("---------------------------");
        } else {
            System.out.println("No semantic errors detected.");
        }
    }

    // --- Main Dispatcher ---
    private void analyzeNode(TreeNode node) {
        if (node == null || "EPSILON".equals(node.getValue())) {
            return; // Ignore null or epsilon nodes
        }

        // Pre-order actions (like entering scope)
        boolean scopeEntered = false;
        String nodeValue = node.getValue(); // Use node type/value for dispatch

        // --- Scope Entry ---
        // Use meaningful scope names
        if ("FUNCTION".equals(nodeValue) || "MAIN_FUNCTION".equals(nodeValue)) { // Assuming MAIN is a distinct node type
            // Function scope handled within analyzeFunction/analyzeMain
        } else if ("IF_STMT".equals(nodeValue) || "ELIF_BLOCK".equals(nodeValue) || "ELSE_BLOCK".equals(nodeValue)
                || "WHILE_LOOP".equals(nodeValue) || "FOR_LOOP".equals(nodeValue) || "UNTIL_LOOP".equals(nodeValue)) {
            // Enter scope for control flow blocks if they have START/END
            if (hasStartEnd(node)) {
                symbolTable.enterScope(nodeValue + "_scope@" + node.getToken().getLine());
                scopeEntered = true;
            }
        }

        // --- Dispatch based on Node Type ---
        switch (nodeValue) {
            case "START_PROG": // Assuming a root node type like START_PROG or PROGRAM
                analyzeProgram(node);
                break;
            case "FUNCTION":
                analyzeFunction(node);
                break;
            case "MAIN_FUNCTION": // Or however your 'main' function is represented
                analyzeMainFunction(node);
                break;
            case "DECLARATION":
                analyzeDeclaration(node);
                break;
            case "ASSIGNMENT": // Assuming ASSIGNMENT is a node type
                analyzeAssignment(node);
                break;
            case "ID_INSTRUCTION": // A node representing an ID followed by instruction' (assignment or func call)
                analyzeIdInstruction(node);
                break;
            case "RETURN_STATEMENT":
                analyzeReturnStatement(node);
                break;
            case "IF_STMT":
            case "ELIF_BLOCK": // Assuming ELIF is a node
            case "WHILE_LOOP":
            case "UNTIL_LOOP":
                analyzeConditionalOrLoop(node);
                break;
            case "FOR_LOOP":
                analyzeForLoop(node);
                break;
            case "FUNCTION_CALL": // Assuming FUNCTION_CALL is a node type
                analyzeFunctionCall(node); // Analyze call, but result type might not be used here
                break;
            // Add cases for other relevant non-terminals like IF_STMT, WHILE_LOOP, etc.
            default:
                // Default: Recursively analyze children
                for (TreeNode child : node.getChildren()) {
                    analyzeNode(child);
                }
                break;
        }

        // Post-order actions (like exiting scope)
        if (scopeEntered) {
            symbolTable.exitScope();
        }
    }

    // Helper to check if a node structure implies a START/END block
    private boolean hasStartEnd(TreeNode node) {
        // This logic depends heavily on your AST structure.
        // Example: Check if children include START and END nodes.
        boolean hasStart = false;
        boolean hasEnd = false;
        for(TreeNode child : node.getChildren()) {
            if("START".equals(child.getValue())) hasStart = true;
            if("END".equals(child.getValue())) hasEnd = true;
        }
        return hasStart && hasEnd;
    }


    // --- Specific Node Analysis Methods ---

    private void analyzeProgram(TreeNode programNode) {
        // Handle global declarations if any, then process functions/main
        // Two-pass approach for functions is good:
        // Pass 1: Register all function signatures (declarations)
        registerFunctionSignatures(programNode);
        // Pass 2: Analyze function bodies and main
        analyzeFunctionBodies(programNode);
    }

    private void registerFunctionSignatures(TreeNode node) {
        if (node == null) return;
        if ("FUNCTION".equals(node.getValue())) {
            declareFunctionSignature(node);
        } else if ("MAIN_FUNCTION".equals(node.getValue())) {
            declareFunctionSignature(node); // Also declare main signature
        } else {
            // Recursively search for functions in children
            for (TreeNode child : node.getChildren()) {
                registerFunctionSignatures(child);
            }
        }
    }

    private void analyzeFunctionBodies(TreeNode node) {
        if (node == null) return;
        if ("FUNCTION".equals(node.getValue())) {
            analyzeFunction(node);
        } else if ("MAIN_FUNCTION".equals(node.getValue())) {
            analyzeMainFunction(node);
        } else {
            // Recursively search for functions in children
            for (TreeNode child : node.getChildren()) {
                analyzeFunctionBodies(child);
            }
        }
    }


    private Symbol declareFunctionSignature(TreeNode funcNode) {
        // --- Extract Name ---
        TreeNode fnTokenNode = findNode(funcNode, "FN"); // Find the FN token node first
        TreeNode funcPrimeNode = null;
        TreeNode idNode = null;
        TreeNode mainNode = null;
        String funcName = null;
        int line = getLine(funcNode); // Default line

        if (fnTokenNode != null) {
            // According to the structure, FUNCTION_PRIME follows FN.
            // Let's assume FUNCTION_PRIME is a child of funcNode (like FN).
            // If it's structured differently (e.g., child of FN), adjust findNode context.
            funcPrimeNode = findNode(funcNode, "FUNCTION_PRIME");
            if (funcPrimeNode != null) {
                // Now look for ID or MAIN under FUNCTION_PRIME
                idNode = findNode(funcPrimeNode, "ID");
                mainNode = findNode(funcPrimeNode, "MAIN");
                // Use FUNCTION_PRIME's line as a fallback if ID/MAIN not found yet
                line = getLine(funcPrimeNode);
            } else {
                // Report error if FUNCTION_PRIME is missing after FN
                reportError(getLine(fnTokenNode), "Expected FUNCTION_PRIME node after FN token.");
                // Optionally return null here if this structure is mandatory
            }
        } else {
            // Report error if FN is missing in a FUNCTION node
            reportError(getLine(funcNode), "Expected FN token node within FUNCTION node.");
            // Optionally return null here
        }


        // Determine name and final line number based on ID or MAIN found
        if (idNode != null) {
            funcName = idNode.getAttribute();
            line = getLine(idNode); // Use ID's line
        } else if (mainNode != null) {
            funcName = "main";
            line = getLine(mainNode); // Use MAIN's line
        } else {
            // If we reach here, name extraction failed even with the corrected structure search
            reportError(getLine(funcNode), "Function definition missing ID or MAIN node within expected FN -> FUNCTION_PRIME structure.");
            return null; // Stop if name cannot be found
        }

        // --- Extract Parameters and Return Type (using funcPrimeNode as context if available) ---
        // Assuming PARAM_DEF and TYPE_DEF are children of FUNCTION_PRIME based on original code structure
        TreeNode contextNodeForParamsAndReturn = (funcPrimeNode != null) ? funcPrimeNode : funcNode;
        List<Symbol> parameters = extractParameters(contextNodeForParamsAndReturn);
        String returnType = determineReturnType(contextNodeForParamsAndReturn);

        // --- Declare in Global Scope ---
        Symbol funcSymbol = new Symbol(funcName, returnType, line, parameters);
        // Use the global scope directly for function declarations
        Scope globalScope = symbolTable.getGlobalScope(); // Need a method in SymbolTable to get global scope
        if (!globalScope.declareSymbol(funcSymbol)) { // Declare in global scope
            Symbol existing = globalScope.lookupSymbol(funcName); // Check global only
            reportError(line, "Function '" + funcName + "' already declared globally at line " + (existing != null ? existing.getLineNumber() : "?"));
            // Decide if this should return null or the existing symbol
            return null; // Returning null as it's a redeclaration error
        }
        System.out.println("Successfully declared function signature: " + funcSymbol); // Debugging success
        return funcSymbol;
    }


    private void analyzeFunction(TreeNode funcNode) {
        Symbol funcSymbol = symbolTable.lookupSymbol(getFunctionName(funcNode)); // Look up the already declared function
        if (funcSymbol == null || !funcSymbol.isFunction()) {
            // This shouldn't happen if registration worked, but good to check
            reportError(getLine(funcNode), "Internal error: Function '" + getFunctionName(funcNode) + "' not found during analysis phase.");
            return;
        }

        Symbol previousFunction = currentFunction;
        currentFunction = funcSymbol; // Set current function for return checks

        symbolTable.enterScope(funcSymbol.getName()); // Enter function scope

        // Declare parameters in the function's scope
        for (Symbol param : funcSymbol.getParameters()) {
            Symbol paramInScope = new Symbol(param.getName(), param.getType(), param.getLineNumber());
            paramInScope.setInitialized(true); // Parameters are considered initialized
            if (!symbolTable.declareSymbol(paramInScope)) {
                // This should ideally be caught during signature declaration if names clash
                reportError(param.getLineNumber(), "Duplicate parameter name '" + param.getName() + "' in function '" + funcSymbol.getName() + "'");
            }
        }

        // Analyze the function body (code inside START/END)
        TreeNode codeBlock = findCodeBlock(funcNode); // Find the CODE node
        if(codeBlock != null) {
            analyzeNode(codeBlock); // Analyze the code block
        } else {
            reportError(getLine(funcNode), "Function '" + funcSymbol.getName() + "' is missing its code block (START...END).");
        }

        symbolTable.exitScope(); // Exit function scope
        currentFunction = previousFunction; // Restore previous function context
    }

    private void analyzeMainFunction(TreeNode mainFuncNode) {
        // Similar to analyzeFunction, but specific details for main if any
        Symbol mainSymbol = symbolTable.lookupSymbol("main");
        if (mainSymbol == null || !mainSymbol.isFunction()) {
            reportError(getLine(mainFuncNode), "Internal error: Main function not found during analysis phase.");
            return;
        }
        if (!mainSymbol.getReturnType().equals("void") && !mainSymbol.getReturnType().equals("int")) { // Example: Allow void or int return for main
            reportError(mainSymbol.getLineNumber(), "Main function must return 'void' or 'int'.");
        }
        if (!mainSymbol.getParameters().isEmpty()) {
            reportError(mainSymbol.getLineNumber(), "Main function cannot take parameters.");
        }


        Symbol previousFunction = currentFunction;
        currentFunction = mainSymbol;

        symbolTable.enterScope("main");

        // Analyze the main function body
        TreeNode codeBlock = findCodeBlock(mainFuncNode);
        if(codeBlock != null) {
            analyzeNode(codeBlock);
        } else {
            reportError(getLine(mainFuncNode), "Main function is missing its code block (START...END).");
        }

        symbolTable.exitScope();
        currentFunction = previousFunction;
    }

    private List<Symbol> extractParameters(TreeNode funcNode) {
        List<Symbol> params = new ArrayList<>();
        // Need robust logic based on your AST for PARAM_DEF -> PO -> PARAMS -> PARAM -> NEXT_PARAM etc.
        // Example assuming a PARAMS node containing PARAM children:
        TreeNode paramsContainer = findNodeRecursive(funcNode, "PARAMS"); // Find the node containing parameters
        if (paramsContainer != null) {
            for (TreeNode paramNode : paramsContainer.getChildren()) {
                if ("PARAM".equals(paramNode.getValue())) {
                    TreeNode typeNode = findNode(paramNode, "VAR_TYPE");
                    TreeNode idNode = findNode(paramNode, "ID");
                    if (typeNode != null && idNode != null) {
                        String type = extractType(typeNode);
                        String name = idNode.getAttribute();
                        int line = idNode.getToken().getLine();
                        // Check for duplicate parameter names *within this function signature*
                        for(Symbol existingParam : params) {
                            if(existingParam.getName().equals(name)) {
                                reportError(line, "Duplicate parameter name '" + name + "' in function signature.");
                                // Decide whether to continue adding or stop
                            }
                        }
                        params.add(new Symbol(name, type, line));
                    } else {
                        reportError(getLine(paramNode), "Malformed parameter definition.");
                    }
                }
            }
        }
        return params;
    }

    private String determineReturnType(TreeNode funcNode) {
        // Need robust logic based on your AST for TYPE_DEF -> ARROW -> VAR_TYPE
        TreeNode typeDefNode = findNodeRecursive(funcNode, "TYPE_DEF");
        if (typeDefNode != null) {
            TreeNode arrowNode = findNode(typeDefNode, "ARROW");
            if (arrowNode != null) {
                TreeNode varTypeNode = findNode(arrowNode, "VAR_TYPE"); // Type is after arrow
                if (varTypeNode != null) {
                    return extractType(varTypeNode);
                } else {
                    reportError(getLine(arrowNode), "Missing return type after '->'.");
                    return "void"; // Default or error type
                }
            }
        }
        return "void"; // Default return type if TYPE_DEF or ARROW is missing/epsilon
    }

    private void analyzeDeclaration(TreeNode declNode) {
        // Assumes structure: VAR_TYPE ARROW ID INIT_OPT
        TreeNode typeNode = findNode(declNode, "VAR_TYPE");
        TreeNode arrowNode = findNode(declNode, "ARROW"); // Use findNode for robustness
        TreeNode idNode = findNode(declNode, "ID");
        TreeNode initOptNode = findNode(declNode, "INIT_OPT");

        if (typeNode == null || arrowNode == null || idNode == null) {
            reportError(getLine(declNode), "Malformed variable declaration.");
            return;
        }

        String varType = extractType(typeNode);
        String varName = idNode.getAttribute();
        int line = idNode.getToken().getLine();
        boolean isInitialized = false;
        String initExprType = null;

        // Analyze initializer if present
        if (initOptNode != null && initOptNode.getChildren().size() > 1) { // Check if not epsilon
            TreeNode eqNode = findNode(initOptNode, "EQ"); // Or ASSIGN if that's the token
            TreeNode evalNode = findNode(initOptNode, "EVAL");
            if (eqNode != null && evalNode != null) {
                initExprType = analyzeEval(evalNode); // Analyze the expression
                if (initExprType != null) {
                    isInitialized = true;
                    // --- Type Check ---
                    if (!isTypeCompatible(varType, initExprType)) {
                        reportError(getLine(eqNode), "Type mismatch: Cannot assign expression of type '" + initExprType + "' to variable '" + varName + "' of type '" + varType + "'.");
                    }
                } // else: error already reported by analyzeEval
            } else {
                reportError(getLine(initOptNode), "Malformed initializer in declaration.");
            }
        }

        // Declare the symbol
        Symbol varSymbol = new Symbol(varName, varType, line);
        varSymbol.setInitialized(isInitialized);
        if (!symbolTable.declareSymbol(varSymbol)) {
            Symbol existing = symbolTable.getCurrentScope().lookupSymbol(varName); // Check current scope only for redeclaration
            reportError(line, "Variable '" + varName + "' already declared in this scope at line " + (existing != null ? existing.getLineNumber() : "?"));
        }
    }

    // Represents instruction starting with ID (assignment or function call)
    private void analyzeIdInstruction(TreeNode idInstrNode) {
        TreeNode idNode = findNode(idInstrNode, "ID");
        TreeNode instructionPrimeNode = findNode(idInstrNode, "INSTRUCTION_PRIME"); // Assuming this follows ID

        if (idNode == null || instructionPrimeNode == null) {
            reportError(getLine(idInstrNode), "Malformed instruction starting with identifier.");
            return;
        }

        String name = idNode.getAttribute();
        int line = idNode.getToken().getLine();

        Symbol symbol = symbolTable.lookupSymbol(name);
        if (symbol == null) {
            reportError(line, "Identifier '" + name + "' not declared.");
            return;
        }

        // Distinguish between assignment and function call based on INSTRUCTION_PRIME content
        TreeNode assignmentNode = findNode(instructionPrimeNode, "ASSIGNMENT");
        TreeNode funcCallNode = findNode(instructionPrimeNode, "FUNCTION_CALL");

        if (assignmentNode != null) {
            // --- Assignment (ID = <eval> or ID++ etc.) ---
            if (symbol.isFunction()) {
                reportError(line, "Cannot assign to function '" + name + "'.");
                return;
            }

            // Analyze the specific assignment type
            TreeNode eqNode = findNode(assignmentNode, "EQ"); // Or ASSIGN token
            TreeNode evalNode = findNode(assignmentNode, "EVAL");
            TreeNode incNode = findNode(assignmentNode, "INCREMENT"); // Assuming ++ is INC token
            TreeNode decNode = findNode(assignmentNode, "DECREMENT"); // Assuming -- is DEC token
            // Add your ** operator token here

            if (eqNode != null && evalNode != null) { // Standard assignment: ID = <eval>
                String rhsType = analyzeEval(evalNode);
                if (rhsType != null) {
                    if (!isTypeCompatible(symbol.getType(), rhsType)) {
                        reportError(getLine(eqNode), "Type mismatch: Cannot assign expression of type '" + rhsType + "' to variable '" + name + "' of type '" + symbol.getType() + "'.");
                    }
                    symbol.setInitialized(true); // Mark as initialized after successful assignment
                }
            } else if (incNode != null || decNode != null /* || yourPowNode != null */) {
                // Increment/Decrement/Power assignment
                if(!symbol.isInitialized()) {
                    reportError(line, "Variable '" + name + "' might not have been initialized before increment/decrement.");
                }
                // Type check: Can only inc/dec numeric types (int/flt)
                if (!symbol.getType().equals("int") && !symbol.getType().equals("flt")) {
                    reportError(line, "Operator '++/--' cannot be applied to type '" + symbol.getType() + "'.");
                }
                // No type change, still initialized
                symbol.setInitialized(true);
            } else {
                reportError(getLine(assignmentNode), "Unrecognized assignment operation for '" + name + "'.");
            }

        } else if (funcCallNode != null) {
            // --- Function Call (ID (<args>)) ---
            if (!symbol.isFunction()) {
                reportError(line, "'" + name + "' is a variable, not a function, cannot call it.");
                return;
            }
            // Analyze the function call itself (arguments vs parameters)
            analyzeFunctionCallArgs(funcCallNode, symbol);

        } else {
            reportError(getLine(instructionPrimeNode), "Instruction after identifier '" + name + "' is neither assignment nor function call.");
        }
    }

    // Placeholder - analyze assignment when it's the top-level node (if grammar allows)
    private void analyzeAssignment(TreeNode assignNode) {
        // This might be needed if your grammar allows assignments directly,
        // e.g., <instruction> ::= <assignment> EOL
        // You'd need to find the ID on the LHS and the EVAL on the RHS
        reportError(getLine(assignNode), "Direct assignment analysis not fully implemented yet.");
    }


    private void analyzeConditionalOrLoop(TreeNode node) {
        // Analyze the condition (<eval>)
        TreeNode evalNode = findNodeRecursive(node, "EVAL"); // Find the condition expression
        if (evalNode != null) {
            String conditionType = analyzeEval(evalNode);
            // Optional: Check if condition type is boolean-compatible (depends on language rules)
            // if (!isBooleanConvertible(conditionType)) {
            //     reportError(getLine(evalNode), "Condition expression must evaluate to a boolean-compatible type, found '" + conditionType + "'.");
            // }
        } else {
            reportError(getLine(node), "Missing condition expression in " + node.getValue() + " statement.");
        }

        // Analyze the code block (which handles its own scope if START/END present)
        TreeNode codeBlock = findCodeBlock(node);
        if(codeBlock != null) {
            analyzeNode(codeBlock);
        } else if (hasStartEnd(node)){ // Only report missing code if START/END were expected
            reportError(getLine(node), "Missing code block in " + node.getValue() + " statement.");
        }

        // Specific handling for IF -> ELIF -> ELSE chain if needed
        if("IF_STMT".equals(node.getValue())) {
            // Find and analyze ELIF_BLOCKS and ELSE_BLOCK siblings/children
            // This depends heavily on AST structure
        }
    }

    private void analyzeForLoop(TreeNode forNode) {
        // FOR (<declaration> , <eval> , ID <assignment>) START <code> END

        symbolTable.enterScope("for_loop_scope@" + getLine(forNode)); // FOR loop has its own scope for the declaration

        // 1. Analyze Declaration
        TreeNode declNode = findNode(forNode, "DECLARATION");
        if (declNode != null) {
            analyzeDeclaration(declNode);
        } else {
            reportError(getLine(forNode), "Missing declaration part in for loop.");
        }

        // 2. Analyze Condition
        TreeNode evalNode = findNode(forNode, "EVAL");
        if (evalNode != null) {
            String conditionType = analyzeEval(evalNode);
            // Optional: Check boolean compatibility
        } else {
            reportError(getLine(forNode), "Missing condition part in for loop.");
        }

        // 3. Analyze Update (ID <assignment>)
        TreeNode idNode = findNode(forNode, "ID"); // ID for the update part
        TreeNode assignmentNode = findNode(forNode, "ASSIGNMENT"); // Assignment for the update part
        if (idNode != null && assignmentNode != null) {
            String idName = idNode.getAttribute();
            Symbol loopVar = symbolTable.lookupSymbol(idName); // Look up the ID (could be the declared one or another)
            if (loopVar == null) {
                reportError(getLine(idNode), "Identifier '" + idName + "' in for loop update part not declared.");
            } else if (loopVar.isFunction()) {
                reportError(getLine(idNode), "Cannot use function '" + idName + "' in for loop update part.");
            } else {
                // Analyze the specific assignment type (e.g., ++, --, = <eval>)
                // Similar logic to analyzeIdInstruction's assignment part
                // Ensure variable is numeric if using ++/--
                if(!loopVar.isInitialized()) {
                    // Warn or error depending on language rules, init might happen in first iteration
                    // reportError(getLine(idNode), "Variable '" + idName + "' might not be initialized before for loop update.");
                }
                // Example for ++/--
                TreeNode incNode = findNode(assignmentNode, "INCREMENT");
                TreeNode decNode = findNode(assignmentNode, "DECREMENT");
                if(incNode != null || decNode != null) {
                    if (!loopVar.getType().equals("int") && !loopVar.getType().equals("flt")) {
                        reportError(getLine(idNode), "Operator '++/--' in for loop update cannot be applied to type '" + loopVar.getType() + "'.");
                    }
                }
                // Add checks for = <eval> if allowed
            }
        } else {
            reportError(getLine(forNode), "Missing update part (ID assignment) in for loop.");
        }


        // 4. Analyze Loop Body
        TreeNode codeBlock = findCodeBlock(forNode);
        if(codeBlock != null) {
            analyzeNode(codeBlock);
        } else {
            reportError(getLine(forNode), "Missing code block in for loop.");
        }


        symbolTable.exitScope(); // Exit for loop scope
    }


    private void analyzeReturnStatement(TreeNode returnNode) {
        if (currentFunction == null) {
            reportError(getLine(returnNode), "Return statement found outside of a function.");
            return;
        }

        String expectedReturnType = currentFunction.getReturnType();
        TreeNode evalNode = findNode(returnNode, "EVAL"); // Check if there's an expression

        if (evalNode != null) {
            // Return with value
            String actualReturnType = analyzeEval(evalNode);
            if (actualReturnType != null) { // If expression analysis was successful
                if (expectedReturnType.equals("void")) {
                    reportError(getLine(returnNode), "Function '" + currentFunction.getName() + "' is void and cannot return a value.");
                } else if (!isTypeCompatible(expectedReturnType, actualReturnType)) {
                    reportError(getLine(evalNode), "Type mismatch: Cannot return value of type '" + actualReturnType + "' from function '" + currentFunction.getName() + "' expecting '" + expectedReturnType + "'.");
                }
            }
        } else {
            // Return without value
            if (!expectedReturnType.equals("void")) {
                reportError(getLine(returnNode), "Function '" + currentFunction.getName() + "' expects a return value of type '" + expectedReturnType + "', but return has no value.");
            }
        }
    }

    // --- Expression Analysis ---

    /**
     * Analyzes an <eval> node and returns its resulting type, or null on error.
     * Performs type checking and initialization checks.
     */
    private String analyzeEval(TreeNode evalNode) {
        // Grammar: <eval> ::= <expr> <eval'>
        // We need to handle the chain of boolean/comparison operators in <eval'>
        TreeNode exprNode = findNode(evalNode, "EXPR");
        if (exprNode == null) {
            reportError(getLine(evalNode), "Invalid expression structure (missing EXPR in EVAL).");
            return null;
        }

        String currentType = analyzeExpr(exprNode); // Analyze the first expression
        if (currentType == null) return null; // Error occurred deeper

        // Iterate through the <eval'> chain (AND, OR, EQUALS, NOT_EQUAL, etc.)
        TreeNode evalPrimeNode = findNode(evalNode, "EVAL_PRIME");
        while (evalPrimeNode != null && evalPrimeNode.getChildren().size() > 1) { // While not epsilon
            // Find the operator (AND, OR, EQ, NEQ, LT, LE, GT, GE) and the next EXPR
            TreeNode operatorNode = evalPrimeNode.getChildren().get(0); // Assumes operator is first child
            TreeNode nextExprNode = findNode(evalPrimeNode, "EXPR"); // Assumes EXPR follows operator

            if (nextExprNode == null) {
                reportError(getLine(evalPrimeNode), "Invalid expression structure (missing EXPR after operator '" + operatorNode.getValue() + "').");
                return null;
            }

            String nextType = analyzeExpr(nextExprNode);
            if (nextType == null) return null;

            // --- Type Checking for Boolean/Comparison Operators ---
            String operator = operatorNode.getValue();
            if (isComparisonOperator(operator)) {
                // Comparisons (==, !=, <, <=, >, >=) typically require numeric types or same types
                if (!areTypesComparable(currentType, nextType)) {
                    reportError(getLine(operatorNode), "Cannot compare types '" + currentType + "' and '" + nextType + "' using operator '" + operator + "'.");
                    return null; // Stop further analysis of this expression
                }
                currentType = "bool"; // Result of comparison is boolean (assuming bool type exists or is handled)
            } else if (isBooleanOperator(operator)) {
                // Boolean logic (AND, OR) typically requires boolean operands
                if (!isBooleanConvertible(currentType) || !isBooleanConvertible(nextType)) {
                    reportError(getLine(operatorNode), "Operator '" + operator + "' requires boolean-compatible operands, found '" + currentType + "' and '" + nextType + "'.");
                    return null;
                }
                currentType = "bool"; // Result is boolean
            } else {
                reportError(getLine(operatorNode), "Internal error: Unhandled operator '" + operator + "' in <eval'> chain.");
                return null;
            }

            // Move to the next <eval'> in the chain
            evalPrimeNode = findNode(evalPrimeNode, "EVAL_PRIME"); // Look for nested EVAL_PRIME
        }

        return currentType; // Return the final type of the <eval> expression
    }


    /** Analyzes an <expr> node: <term> <expr'> */
    private String analyzeExpr(TreeNode exprNode) {
        // Grammar: <expr> ::= <term> <expr'>
        TreeNode termNode = findNode(exprNode, "TERM");
        if (termNode == null) {
            reportError(getLine(exprNode), "Invalid expression structure (missing TERM in EXPR).");
            return null;
        }
        String currentType = analyzeTerm(termNode);
        if (currentType == null) return null;

        // Iterate through <expr'> chain (+, -)
        TreeNode exprPrimeNode = findNode(exprNode, "EXPR_PRIME");
        while (exprPrimeNode != null && exprPrimeNode.getChildren().size() > 1) {
            TreeNode operatorNode = exprPrimeNode.getChildren().get(0); // + or -
            TreeNode nextTermNode = findNode(exprPrimeNode, "TERM");

            if (nextTermNode == null) {
                reportError(getLine(exprPrimeNode), "Invalid expression structure (missing TERM after operator '" + operatorNode.getValue() + "').");
                return null;
            }
            String nextType = analyzeTerm(nextTermNode);
            if (nextType == null) return null;

            // --- Type Checking for + / - ---
            // Require numeric types, result is numeric (promote int to float if needed)
            if (!isNumeric(currentType) || !isNumeric(nextType)) {
                reportError(getLine(operatorNode), "Operator '" + operatorNode.getValue() + "' requires numeric operands, found '" + currentType + "' and '" + nextType + "'.");
                return null;
            }
            // Type promotion (e.g., int + float -> float)
            currentType = promoteNumericType(currentType, nextType);

            exprPrimeNode = findNode(exprPrimeNode, "EXPR_PRIME"); // Move to next link
        }
        return currentType;
    }

    /** Analyzes a <term> node: <factor> <term'> */
    private String analyzeTerm(TreeNode termNode) {
        // Grammar: <term> ::= <factor> <term'>
        TreeNode factorNode = findNode(termNode, "FACTOR");
        if (factorNode == null) {
            reportError(getLine(termNode), "Invalid expression structure (missing FACTOR in TERM).");
            return null;
        }
        String currentType = analyzeFactor(factorNode);
        if (currentType == null) return null;

        // Iterate through <term'> chain (*, /, %)
        TreeNode termPrimeNode = findNode(termNode, "TERM_PRIME");
        while (termPrimeNode != null && termPrimeNode.getChildren().size() > 1) {
            TreeNode operatorNode = termPrimeNode.getChildren().get(0); // *, /, %
            TreeNode nextFactorNode = findNode(termPrimeNode, "FACTOR");

            if (nextFactorNode == null) {
                reportError(getLine(termPrimeNode), "Invalid expression structure (missing FACTOR after operator '" + operatorNode.getValue() + "').");
                return null;
            }
            String nextType = analyzeFactor(nextFactorNode);
            if (nextType == null) return null;

            // --- Type Checking for * / % ---
            if (!isNumeric(currentType) || !isNumeric(nextType)) {
                reportError(getLine(operatorNode), "Operator '" + operatorNode.getValue() + "' requires numeric operands, found '" + currentType + "' and '" + nextType + "'.");
                return null;
            }
            // Special case: Modulo (%) often requires integers
            if ("MODULO".equals(operatorNode.getValue()) /* Adjust token name */ && (!currentType.equals("int") || !nextType.equals("int"))) {
                reportError(getLine(operatorNode), "Operator '%' requires integer operands, found '" + currentType + "' and '" + nextType + "'.");
                return null;
            }

            currentType = promoteNumericType(currentType, nextType);

            termPrimeNode = findNode(termPrimeNode, "TERM_PRIME");
        }
        return currentType;
    }

    /** Analyzes a <factor> node: (<eval>) | ID <factor'> | <literal> | NOT <factor> */
    private String analyzeFactor(TreeNode factorNode) {
        // Determine the specific type of factor
        TreeNode firstChild = factorNode.getChildren().isEmpty() ? null : factorNode.getChildren().get(0);
        if (firstChild == null) {
            reportError(getLine(factorNode), "Empty factor node encountered.");
            return null;
        }

        String nodeType = firstChild.getValue();

        if ("LPAREN".equals(nodeType)) { // Or PO token
            // Factor is (<eval>)
            TreeNode evalNode = findNode(factorNode, "EVAL"); // Should be between LPAREN and RPAREN
            if (evalNode == null) {
                reportError(getLine(factorNode), "Malformed parenthesized expression.");
                return null;
            }
            // RPAREN check might be implicit if parser enforces it
            return analyzeEval(evalNode); // Recursively analyze the inner expression

        } else if ("ID".equals(nodeType)) {
            // Factor is ID <factor'> (ID or ID(<args>))
            String name = firstChild.getAttribute();
            int line = firstChild.getToken().getLine();
            Symbol symbol = symbolTable.lookupSymbol(name);

            if (symbol == null) {
                reportError(line, "Identifier '" + name + "' not declared.");
                return null;
            }

            TreeNode factorPrimeNode = findNode(factorNode, "FACTOR_PRIME"); // Check for function call part

            if (factorPrimeNode != null && !isEpsilon(factorPrimeNode)) {
                // It's a function call: ID (<args>)
                if (!symbol.isFunction()) {
                    reportError(line, "'" + name + "' is not a function and cannot be called.");
                    return null;
                }
                // Analyze arguments vs parameters
                analyzeFunctionCallArgs(factorPrimeNode, symbol); // Assuming FACTOR_PRIME contains FUNCTION_CALL structure
                return symbol.getReturnType(); // Result type is the function's return type

            } else {
                // It's just an ID (variable usage)
                if (symbol.isFunction()) {
                    reportError(line, "Cannot use function '" + name + "' as a value without calling it.");
                    return null;
                }
                // --- Initialization Check ---
                if (!symbol.isInitialized()) {
                    reportError(line, "Variable '" + name + "' might not have been initialized before use.");
                    // Depending on language rules, might continue or return null
                }
                return symbol.getType(); // Result type is the variable's type
            }

        } else if ("LITERAL".equals(nodeType)) {
            // Factor is <literal>
            return analyzeLiteral(firstChild); // Get type from literal node

        } else if ("NOT".equals(nodeType)) {
            // Factor is NOT <factor>
            TreeNode nextFactor = findNode(factorNode, "FACTOR"); // Find the factor following NOT
            if(nextFactor == null) {
                reportError(getLine(firstChild), "Missing expression after NOT operator.");
                return null;
            }
            String operandType = analyzeFactor(nextFactor);
            if (operandType != null) {
                // Type check: NOT usually requires boolean-compatible type
                if (!isBooleanConvertible(operandType)) {
                    reportError(getLine(firstChild), "Operator 'NOT' requires a boolean-compatible operand, found '" + operandType + "'.");
                    return null;
                }
                return "bool"; // Result of NOT is boolean
            }
            return null; // Error in nested factor

        } else {
            reportError(getLine(factorNode), "Unrecognized factor structure starting with '" + nodeType + "'.");
            return null;
        }
    }

    private String analyzeLiteral(TreeNode literalNode) {
        // Assumes literalNode contains the actual literal token (INTEGER_LITERAL, etc.)
        if (literalNode.getChildren().isEmpty()) {
            reportError(getLine(literalNode), "Empty literal node.");
            return null;
        }
        TreeNode typeNode = literalNode.getChildren().get(0);
        switch (typeNode.getValue()) {
            case "INTEGER_LITERAL": return "int";
            case "FLOAT_LITERAL":   return "flt";
            case "CHAR_LITERAL":    return "chr";
            // Add BOOLEAN_LITERAL if you have true/false
            // case "BOOLEAN_LITERAL": return "bool";
            default:
                reportError(getLine(typeNode), "Unknown literal type: " + typeNode.getValue());
                return null;
        }
    }

    // --- Function Call Analysis ---

    private String analyzeFunctionCall(TreeNode funcCallNode) {
        // This is called when FUNCTION_CALL is a standalone node (if grammar allows)
        // Need to find the ID and arguments
        TreeNode idNode = findNode(funcCallNode, "ID"); // Find the function name being called
        if (idNode == null) {
            reportError(getLine(funcCallNode), "Function call missing function name.");
            return null;
        }
        String funcName = idNode.getAttribute();
        int line = idNode.getToken().getLine();

        Symbol symbol = symbolTable.lookupSymbol(funcName);
        if (symbol == null) {
            reportError(line, "Function '" + funcName + "' not declared.");
            return null;
        }
        if (!symbol.isFunction()) {
            reportError(line, "'" + funcName + "' is not a function.");
            return null;
        }

        analyzeFunctionCallArgs(funcCallNode, symbol); // Reuse the argument analysis
        return symbol.getReturnType();
    }


    /** Analyzes arguments passed in a function call against expected parameters */
    private void analyzeFunctionCallArgs(TreeNode callArgsNode, Symbol functionSymbol) {
        // callArgsNode might be FACTOR_PRIME or FUNCTION_CALL depending on context
        // Needs robust logic based on AST for (<eval> <next_arg>) structure
        List<String> argumentTypes = new ArrayList<>();
        List<TreeNode> argEvalNodes = findArgumentExpressions(callArgsNode); // Helper to find all EVAL nodes for args

        for (TreeNode evalNode : argEvalNodes) {
            String argType = analyzeEval(evalNode);
            if (argType == null) {
                // Error analyzing argument, stop checking this call
                return;
            }
            argumentTypes.add(argType);
        }

        // Compare arguments with parameters
        List<Symbol> parameters = functionSymbol.getParameters();
        int callLine = getLine(callArgsNode); // Get line number for error reporting

        // Check number of arguments
        if (argumentTypes.size() != parameters.size()) {
            reportError(callLine, "Function '" + functionSymbol.getName() + "' expects " + parameters.size() +
                    " arguments, but received " + argumentTypes.size() + ".");
            return; // Stop further checks if arg count mismatches
        }

        // Check types of arguments
        for (int i = 0; i < parameters.size(); i++) {
            String expectedType = parameters.get(i).getType();
            String actualType = argumentTypes.get(i);
            if (!isTypeCompatible(expectedType, actualType)) {
                reportError(callLine, "Type mismatch in argument " + (i + 1) + " for function '" + functionSymbol.getName() +
                        "': Expected '" + expectedType + "', but received '" + actualType + "'.");
            }
        }
    }

    // Helper to find all EVAL nodes representing arguments in a call structure
    // THIS IS HIGHLY DEPENDENT ON YOUR AST STRUCTURE FOR FUNCTION_CALL / NEXT_ARG
    private List<TreeNode> findArgumentExpressions(TreeNode callArgsNode) {
        List<TreeNode> argExprs = new ArrayList<>();
        // Example Logic: Assumes callArgsNode contains LPAREN, optional EVAL, optional NEXT_ARG chain
        TreeNode firstEval = findNode(callArgsNode, "EVAL");
        if (firstEval != null) {
            argExprs.add(firstEval);
            TreeNode nextArgNode = findNode(callArgsNode, "NEXT_ARG");
            while (nextArgNode != null && !isEpsilon(nextArgNode)) {
                TreeNode nextEval = findNode(nextArgNode, "EVAL");
                if (nextEval != null) {
                    argExprs.add(nextEval);
                } else {
                    reportError(getLine(nextArgNode), "Malformed argument list: missing expression after comma.");
                    break; // Stop processing malformed list
                }
                nextArgNode = findNode(nextArgNode, "NEXT_ARG"); // Move to the next link
            }
        }
        return argExprs;
    }


    // --- Type System Helpers ---

    private String extractType(TreeNode varTypeNode) {
        // Assumes VAR_TYPE has one child which is INT, FLT, or CHR token node
        if (varTypeNode == null || varTypeNode.getChildren().isEmpty()) return "unknown";
        TreeNode typeTokenNode = varTypeNode.getChildren().get(0);
        switch (typeTokenNode.getValue()) {
            case "INT":   return "int";
            case "FLOAT": return "flt";
            case "CHAR":  return "chr"; // Make sure this matches your token name exactly
            default:      return "unknown";
        }
    }

    // Basic type compatibility check (extend as needed, e.g., allow int->flt)
    private boolean isTypeCompatible(String expected, String actual) {
        if (expected == null || actual == null) return false; // Error somewhere
        if (expected.equals(actual)) return true;
        // Allow assigning int to float?
        if (expected.equals("flt") && actual.equals("int")) return true;
        // Add other compatibility rules if needed
        return false;
    }

    // Check if a type is numeric
    private boolean isNumeric(String type) {
        return "int".equals(type) || "flt".equals(type);
    }

    // Check if types are comparable (e.g., for ==, !=, <, >)
    private boolean areTypesComparable(String type1, String type2) {
        if (type1 == null || type2 == null) return false;
        // Allow comparing same types
        if (type1.equals(type2)) return true;
        // Allow comparing int and float
        if (isNumeric(type1) && isNumeric(type2)) return true;
        // Add other comparable types if needed (e.g., char with char)
        return false;
    }


    // Check if a type can be used in boolean contexts (if, while, AND, OR, NOT)
    private boolean isBooleanConvertible(String type) {
        if (type == null) return false;
        // Explicit boolean type?
        if ("bool".equals(type)) return true;
        // Allow integers? (0 is false, non-zero is true - depends on language rules)
        // if ("int".equals(type)) return true;
        return false;
    }

    // Promote numeric types (e.g., int + float -> float)
    private String promoteNumericType(String type1, String type2) {
        if ("flt".equals(type1) || "flt".equals(type2)) return "flt";
        if ("int".equals(type1) && "int".equals(type2)) return "int";
        return "unknown"; // Should not happen if isNumeric check passed
    }

    private boolean isComparisonOperator(String op) {
        return "EQUALS".equals(op) || "NOT_EQUAL".equals(op) || "LOWER".equals(op) ||
                "LOWER_EQUAL".equals(op) || "GREATER".equals(op) || "GREATER_EQUAL".equals(op);
    }

    private boolean isBooleanOperator(String op) {
        return "AND".equals(op) || "OR".equals(op);
    }


    // --- Robust Tree Navigation Helpers (Replace findFirstChild/findSiblingAfter) ---

    /** Finds the first child node with the specified value/type */
    private TreeNode findNode(TreeNode parent, String value) {
        if (parent == null) return null;
        for (TreeNode child : parent.getChildren()) {
            if (value.equals(child.getValue())) {
                return child;
            }
        }
        return null;
    }

    /** Finds the first node with the specified value/type anywhere in the subtree */
    private TreeNode findNodeRecursive(TreeNode startNode, String value) {
        if (startNode == null) return null;
        if (value.equals(startNode.getValue())) return startNode;
        for (TreeNode child : startNode.getChildren()) {
            TreeNode found = findNodeRecursive(child, value);
            if (found != null) return found;
        }
        return null;
    }

    /** Finds the CODE block node, typically after START */
    private TreeNode findCodeBlock(TreeNode parent) {
        // Assumes CODE is a child, possibly after a START node
        TreeNode startNode = findNode(parent, "START");
        if (startNode != null) {
            // Look for CODE as a sibling of START or child of parent after START
            // This needs adjustment based on your exact AST. Example:
            boolean foundStart = false;
            for(TreeNode child : parent.getChildren()) {
                if(child == startNode) {
                    foundStart = true;
                } else if (foundStart && "CODE".equals(child.getValue())) {
                    return child;
                }
            }
            // Fallback: Maybe CODE is a direct child of START?
            TreeNode codeInChildren = findNode(startNode, "CODE");
            if(codeInChildren != null) return codeInChildren;

        }
        // Fallback: Maybe CODE is a direct child of the parent (e.g. for program root)?
        return findNode(parent, "CODE");
    }

    private String getFunctionName(TreeNode funcNode) {
        TreeNode fnTokenNode = findNode(funcNode, "FN");
        TreeNode funcPrimeNode = null;
        TreeNode idNode = null;
        TreeNode mainNode = null;

        if (fnTokenNode != null) {
            // Assume FUNCTION_PRIME is a child of funcNode
            funcPrimeNode = findNode(funcNode, "FUNCTION_PRIME");
            if (funcPrimeNode != null) {
                idNode = findNode(funcPrimeNode, "ID");
                mainNode = findNode(funcPrimeNode, "MAIN");
            }
        }

        if(idNode != null) {
            return idNode.getAttribute();
        }
        if(mainNode != null) {
            return "main";
        }

        // If it still fails, add specific debug info
        System.err.println("DEBUG getFunctionName: Failed to find ID or MAIN under FUNCTION_PRIME for node: "
                + funcNode.getValue() + " at line " + getLine(funcNode)
                + ". FN found: " + (fnTokenNode != null)
                + ", FUNC_PRIME found: " + (funcPrimeNode != null));
        return "??unknown??"; // The fallback indicating failure
    }

    private int getLine(TreeNode node) {
        return (node != null && node.getToken() != null) ? node.getToken().getLine() : -1; // Safer line access
    }

    private boolean isEpsilon(TreeNode node) {
        // Check if node represents an epsilon production
        // Common ways: Node value is "EPSILON", or it's a non-terminal node with no children
        if (node == null) return false; // Or true depending on definition
        if ("EPSILON".equals(node.getValue())) return true;
        // Heuristic: Non-terminal with no children might represent epsilon
        // This depends heavily on how your parser builds the AST for epsilon rules.
        // return !node.isTerminal() && node.getChildren().isEmpty(); // Example heuristic
        return node.getChildren().isEmpty(); // Simplest check if parser omits children for epsilon

    }

    // --- Error Reporting ---
    private void reportError(int line, String message) {
        semanticErrors.add("Semantic Error at line " + line + ": " + message);
    }
}