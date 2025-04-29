package FrontEnd.Semantics;

import FrontEnd.Syntax.TreeNode;
import Global.SymbolTable.*;

import java.util.ArrayList;
import java.util.List;

public class SemanticAnalyzer {
    private SymbolTable symbolTable;
    private List<String> semanticErrors;
    private Symbol currentFunction;

    public SemanticAnalyzer(TreeNode root) {
        this.symbolTable = new SymbolTable();
        this.semanticErrors = new ArrayList<>();
        this.currentFunction = null;
        System.out.println("Starting semantic analysis...");
        analyzeNode(root);
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

    private void analyzeNode(TreeNode node) {
        if (node == null || "EPSILON".equals(node.getValue())) {
            return;
        }

        if ("INICIAL".equals(node.getValue())) {
            analyzeProgram(node);
            return;
        }

        boolean scopeEntered = false;
        String nodeValue = node.getValue();

       if ("IF_STATEMENT".equals(nodeValue) || "ELIF_BLOCKS".equals(nodeValue) || "ELSE_BLOCK".equals(nodeValue)
                || "WHILE_LOOP".equals(nodeValue) || "FOR_LOOP".equals(nodeValue) || "UNTIL_LOOP".equals(nodeValue)) {
            if (hasStartEnd(node)) {
                symbolTable.enterScope(nodeValue + "_scope@" + node.getToken().getLine());
                scopeEntered = true;
            }
       }

        switch (nodeValue) {
            case "ROOT":
                analyzeProgram(node);
                break;
            case "FUNCTION":
                analyzeFunction(node);
                break;
            case "MAIN_FUNCTION":
                analyzeMainFunction(node);
                break;
            case "DECLARATION":
                analyzeDeclaration(node);
                break;
            case "INSTRUCTION":
                TreeNode decl = findNode(node, "DECLARATION");
                if (decl != null) {
                    analyzeDeclaration(decl);
                    break;
                }
                TreeNode ret = findNode(node, "RETURN_STATEMENT");
                if (ret != null) {
                    analyzeReturnStatement(ret);
                    break;
                }
                TreeNode cond = findNode(node,"CONDITIONAL");
                if (cond!=null) {
                    analyzeNode(cond);
                    break;
                }
                TreeNode iter = findNode(node, "ITERATIVE");
                if (iter != null) {
                    analyzeNode(iter);
                    break;
                }
                analyzeIdInstruction(node);
                break;
            case "CONDITIONAL":
                TreeNode ifStmt = findNode(node,"IF_STATEMENT");
                if (ifStmt != null) {
                    analyzeNode(ifStmt);
                }
                TreeNode elifStmt = findNode(node,"ELIF_BLOCKS");
                if (elifStmt != null && findNode(elifStmt, "ELIF") != null) {
                    analyzeNode(elifStmt);
                }
                TreeNode elStmt = findNode(node,"ELSE_BLOCK");
                if (elStmt != null && findNode(elStmt, "ELSE") != null) {
                    analyzeNode(elStmt);
                }
                break;
            case "RETURN_STATEMENT":
                analyzeReturnStatement(node);
                break;
            case "ELSE_BLOCK":
                TreeNode elseCode = findCodeBlock(node);
                if (elseCode != null) {
                    analyzeCodeBlock(elseCode);
                } else {
                    reportError(getLine(node), "Missing code block in ELSE statement.");
                }
                break;
            case "IF_STATEMENT":
            case "ELIF_BLOCKS":
            case "WHILE_LOOP":
            case "UNTIL_LOOP":
                analyzeConditionalOrLoop(node);
                break;
            case "FOR_LOOP":
                analyzeForLoop(node);
                break;
            case "EVAL":
                analyzeEval(node);
                break;
            case "EXPR":
                analyzeExpr(node);
                break;
            case "TERM":
                analyzeTerm(node);
                break;
            case "FACTOR":
                analyzeFactor(node);
                break;
            default:
                for (TreeNode child : node.getChildren()) {
                    analyzeNode(child);
                }
                break;
        }

        if (scopeEntered) {
            symbolTable.exitScope();
        }
    }

    private boolean hasStartEnd(TreeNode node) {
        boolean hasStart = false;
        boolean hasEnd = false;
        for(TreeNode child : node.getChildren()) {
            if("START".equals(child.getValue())) hasStart = true;
            if("END".equals(child.getValue())) hasEnd = true;
        }
        return hasStart && hasEnd;
    }

    private void analyzeProgram(TreeNode programNode) {
        registerFunctionSignatures(programNode);
        analyzeFunctionBodies(programNode);
    }

    private void registerFunctionSignatures(TreeNode node) {
        if (node == null) return;
        if ("FUNCTION".equals(node.getValue())) {
            declareFunctionSignature(node);
        } else if ("MAIN_FUNCTION".equals(node.getValue())) {
            declareFunctionSignature(node);
        } else {
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
            for (TreeNode child : node.getChildren()) {
                analyzeFunctionBodies(child);
            }
        }
    }


    private void declareFunctionSignature(TreeNode funcNode) {
        TreeNode fnTokenNode = findNode(funcNode, "FN");
        TreeNode funcPrimeNode;
        TreeNode idNode = null;
        TreeNode mainNode = null;
        String funcName;
        int line;

        if (fnTokenNode != null) {
            funcPrimeNode = findNode(funcNode, "FUNCTION_PRIME");
            if (funcPrimeNode != null) {
                idNode = findNode(funcPrimeNode, "ID");
                mainNode = findNode(funcPrimeNode, "MAIN");
                line = getLine(funcPrimeNode);
            } else {
                reportError(getLine(fnTokenNode), "Expected FUNCTION_PRIME node after FN token.");
            }
        } else {
            reportError(getLine(funcNode), "Expected FN token node within FUNCTION node.");
        }

        if (idNode != null) {
            funcName = idNode.getAttribute();
            line = getLine(idNode);
        } else if (mainNode != null) {
            funcName = "main";
            line = getLine(mainNode);
        } else {
            reportError(getLine(funcNode), "Function definition missing ID or MAIN node within expected FN -> FUNCTION_PRIME structure.");
            return;
        }

        List<Symbol> parameters = extractParameters(funcNode);
        String returnType = determineReturnType(funcNode);

        Symbol funcSymbol = new Symbol(funcName, returnType, line, parameters);
        Scope globalScope = symbolTable.getGlobalScope();
        if (!globalScope.declareSymbol(funcSymbol)) {
            Symbol existing = globalScope.lookupSymbol(funcName);
            reportError(line, "Function '" + funcName + "' already declared globally at line " + (existing != null ? existing.getLineNumber() : "?"));
            return;
        }
        System.out.println("Successfully declared function signature: " + funcSymbol);
    }


    private void analyzeFunction(TreeNode funcNode) {
        Symbol funcSymbol = symbolTable.lookupSymbol(getFunctionName(funcNode));
        if (funcSymbol == null || !funcSymbol.isFunction()) {
            reportError(getLine(funcNode), "Internal error: Function '" + getFunctionName(funcNode) + "' not found during analysis phase.");
            return;
        }

        Symbol previousFunction = currentFunction;
        currentFunction = funcSymbol;

        symbolTable.enterScope(funcSymbol.getName());

        for (Symbol param : funcSymbol.getParameters()) {
            Symbol paramInScope = new Symbol(param.getName(), param.getType(), param.getLineNumber());
            paramInScope.setInitialized(true);
            if (!symbolTable.declareSymbol(paramInScope)) {
                reportError(param.getLineNumber(), "Duplicate parameter name '" + param.getName() + "' in function '" + funcSymbol.getName() + "'");
            }
        }

        TreeNode codeBlock = findCodeBlock(funcNode);
        if(codeBlock != null) {
            analyzeCodeBlock(codeBlock);
        } else {
            reportError(getLine(funcNode), "Function '" + funcSymbol.getName() + "' is missing its code block (START...END).");
        }

        symbolTable.exitScope();
        currentFunction = previousFunction;
    }

    private void analyzeMainFunction(TreeNode mainFuncNode) {
        Symbol mainSymbol = symbolTable.lookupSymbol("main");
        if (mainSymbol == null || !mainSymbol.isFunction()) {
            reportError(getLine(mainFuncNode), "Internal error: Main function not found during analysis phase.");
            return;
        }
        if (!mainSymbol.getReturnType().equals("void") && !mainSymbol.getReturnType().equals("int")) {
            reportError(mainSymbol.getLineNumber(), "Main function must return 'void' or 'int'.");
        }
        if (!mainSymbol.getParameters().isEmpty()) {
            reportError(mainSymbol.getLineNumber(), "Main function cannot take parameters.");
        }


        Symbol previousFunction = currentFunction;
        currentFunction = mainSymbol;

        symbolTable.enterScope("main");

        TreeNode codeBlock = findCodeBlock(mainFuncNode);
        if (codeBlock != null) {
            analyzeCodeBlock(codeBlock);
        } else {
            reportError(getLine(mainFuncNode), "Main function is missing its code block (START...END).");
        }

        symbolTable.exitScope();
        currentFunction = previousFunction;
    }
    private List<Symbol> extractParameters(TreeNode funcPrimeNode) {
        List<Symbol> params = new ArrayList<>();

        // Busca PARAM_DEF bajo FUNCTION_PRIME
        TreeNode paramDef = findNode(funcPrimeNode, "PARAM_DEF");
        if (paramDef == null) return params;

        // Dentro de PARAM_DEF, busca PARAMS
        TreeNode paramsContainer = findNode(paramDef, "PARAMS");
        if (paramsContainer == null) return params;

        // Recorre PARAMS y NEXT_PARAM para extraer cada PARAM
        collectParams(paramsContainer, params);
        return params;
    }

    private void collectParams(TreeNode node, List<Symbol> out) {
        for (TreeNode child : node.getChildren()) {
            if ("PARAM".equals(child.getValue())) {
                TreeNode typeNode = findNode(child, "VAR_TYPE");
                TreeNode idNode   = findNode(child, "ID");
                if (typeNode != null && idNode != null) {
                    String type = extractType(typeNode);
                    String name = idNode.getAttribute();
                    int    line = getLine(idNode);
                    out.add(new Symbol(name, type, line));
                } else {
                    reportError(getLine(child), "Malformed parameter definition.");
                }
            }
            else if ("NEXT_PARAM".equals(child.getValue())) {
                collectParams(child, out);
            }
        }
    }

    private String determineReturnType(TreeNode funcNode) {
        TreeNode typeDef = findNode(funcNode, "TYPE_DEF");
        if (typeDef != null && findNode(typeDef, "ARROW") != null) {
            TreeNode varType = findNode(typeDef, "VAR_TYPE");
            if (varType != null) {
                return extractType(varType);
            } else {
                reportError(getLine(typeDef), "Missing return VAR_TYPE after '->' in function declaration.");
                return "void";
            }
        }
        // Si no hay ARROW asumimos que es void (como por ejemplo el main) y no lo consideramos un error
        return "void";
    }

    private void analyzeDeclaration(TreeNode declNode) {
        TreeNode typeNode = findNode(declNode, "VAR_TYPE");
        TreeNode arrowNode = findNode(declNode, "ARROW");
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
        String initExprType;

        if (initOptNode != null && initOptNode.getChildren().size() > 1) { // Miramos si no es epsilon
            TreeNode eqNode = findNode(initOptNode, "EQ");
            TreeNode evalNode = findNode(initOptNode, "EVAL");
            if (eqNode != null && evalNode != null) {
                initExprType = analyzeEval(evalNode);
                if (initExprType != null) {
                    isInitialized = true;
                    if (!isTypeCompatible(varType, initExprType)) {
                        reportError(getLine(eqNode), "Type mismatch: Cannot assign expression of type '" + initExprType + "' to variable '" + varName + "' of type '" + varType + "'.");
                    }
                }
            } else {
                reportError(getLine(initOptNode), "Malformed initializer in declaration.");
            }
        }

        Symbol existing = symbolTable.lookupSymbol(varName);
        if (existing != null) {
            reportError(line, "Variable '" + varName + "' already declared in an outer scope at line " + existing.getLineNumber());
            return;
        }

        Symbol varSymbol = new Symbol(varName, varType, line);
        varSymbol.setInitialized(isInitialized);
        if (!symbolTable.declareSymbol(varSymbol)) {
            Symbol existingCurr = symbolTable.getCurrentScope().lookupSymbol(varName);
            reportError(line, "Variable '" + varName + "' already declared in this scope at line " + (existingCurr != null ? existingCurr.getLineNumber() : "?"));
        }
    }

    private boolean checkForwardCall(String name, int callLine, Symbol sym) {
        if (!sym.isFunction()) {
            reportError(callLine, "'" + name + "' is not a function, cannot call it.");
            return false;
        }
        int declLine = sym.getLineNumber();
        if (callLine < declLine) {
            reportError(callLine, "Function '" + name + "' called in line " + callLine + " before its declaration in line " + declLine + ".");
            return false;
        }
        return true;
    }

    private void analyzeFunctionCall(TreeNode idNode, TreeNode callNode) {
        String name = idNode.getAttribute();
        int line = getLine(idNode);
        Symbol sym = symbolTable.lookupSymbol(name);
        if (sym == null) {
            reportError(line, "Function '" + name + "' not declared.");
            return;
        }
        if (!checkForwardCall(name, line, sym)) return;
        analyzeFunctionCallArgs(callNode, sym);
    }

    private void analyzeIdInstruction(TreeNode idInstrNode) {
        TreeNode idNode = findNode(idInstrNode, "ID");
        TreeNode instructionPrimeNode = findNode(idInstrNode, "INSTRUCTION_PRIME");

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

        TreeNode assignmentNode = findNode(instructionPrimeNode, "ASSIGNMENT");
        TreeNode funcCallNode = findNode(instructionPrimeNode, "FUNCTION_CALL");

        if (assignmentNode != null) {
            // Assignment (ID = <eval> or ID++ etc.)
            if (symbol.isFunction()) {
                reportError(line, "Cannot assign to function '" + name + "'.");
                return;
            }

            TreeNode incNode = findNode(assignmentNode, "INC");
            TreeNode decNode = findNode(assignmentNode, "DEC");
            TreeNode eqNode   = findNode(assignmentNode, "EQ");
            TreeNode exprNode = findNode(assignmentNode, "EXPR");

            if (eqNode != null && exprNode != null) {
                String rhsType = analyzeExpr(exprNode);
                if (rhsType != null) {
                    if (!isTypeCompatible(symbol.getType(), rhsType)) {
                        reportError(getLine(eqNode), "Type mismatch: Cannot assign expression of type '" + rhsType + "' to variable '" + name + "' of type '" + symbol.getType() + "'.");
                    }
                    symbol.setInitialized(true);
                }
            } else if (incNode != null || decNode != null) {
                if(!symbol.isInitialized()) {
                    reportError(line, "Variable '" + name + "' might not have been initialized before increment/decrement.");
                }
                if (!symbol.getType().equals("int") && !symbol.getType().equals("flt")) {
                    reportError(line, "Operator '++/--' cannot be applied to type '" + symbol.getType() + "'.");
                }
                symbol.setInitialized(true);
            } else {
                reportError(getLine(assignmentNode), "Unrecognized assignment operation for '" + name + "'.");
            }

        } else if (funcCallNode != null) {
            if (!symbol.isFunction()) {
                reportError(line, "'" + name + "' is not a function, cannot call it.");
            } else {
                analyzeFunctionCall(idNode, funcCallNode);
            }
            return;
        } else {
            reportError(getLine(instructionPrimeNode), "Instruction after identifier '" + name + "' is neither assignment nor function call.");
        }
    }
    private void analyzeConditionalOrLoop(TreeNode node) {
        TreeNode evalNode = findNodeRecursive(node, "EVAL");
        if (evalNode != null) {
            analyzeEval(evalNode);
        } else {
            reportError(getLine(node), "Missing condition expression in " + node.getValue() + " statement.");
        }

        TreeNode codeBlock = findCodeBlock(node);
        if(codeBlock != null) {
            analyzeCodeBlock(codeBlock);
        } else if (hasStartEnd(node)){
            reportError(getLine(node), "Missing code block in " + node.getValue() + " statement.");
        }
    }

    private void analyzeForLoop(TreeNode forNode) {
        // FOR (<declaration> , <eval> , ID <assignment>) START <code> END

        symbolTable.enterScope("for_loop_scope@" + getLine(forNode));

        // Declaration
        TreeNode declNode = findNode(forNode, "DECLARATION");
        if (declNode != null) {
            analyzeDeclaration(declNode);
        } else {
            reportError(getLine(forNode), "Missing declaration part in for loop.");
        }

        // Condition
        TreeNode evalNode = findNode(forNode, "EVAL");
        if (evalNode != null) {
            analyzeEval(evalNode);
        } else {
            reportError(getLine(forNode), "Missing condition part in for loop.");
        }

        // Update (ID <assignment>)
        TreeNode idNode = findNode(forNode, "ID");
        TreeNode assignmentNode = findNode(forNode, "ASSIGNMENT");
        if (idNode != null && assignmentNode != null) {
            String idName = idNode.getAttribute();
            Symbol loopVar = symbolTable.lookupSymbol(idName);
            if (loopVar == null) {
                reportError(getLine(idNode), "Identifier '" + idName + "' in for loop update part not declared.");
            } else if (loopVar.isFunction()) {
                reportError(getLine(idNode), "Cannot use function '" + idName + "' in for loop update part.");
            } else {
                if(!loopVar.isInitialized()) {
                    reportError(getLine(idNode), "Variable '" + idName + "' might not be initialized before for loop update.");
                }
                // ++/--
                TreeNode incNode = findNode(assignmentNode, "INCREMENT");
                TreeNode decNode = findNode(assignmentNode, "DECREMENT");
                if(incNode != null || decNode != null) {
                    if (!loopVar.getType().equals("int") && !loopVar.getType().equals("flt")) {
                        reportError(getLine(idNode), "Operator '++/--' in for loop update cannot be applied to type '" + loopVar.getType() + "'.");
                    }
                }
            }
        } else {
            reportError(getLine(forNode), "Missing update part (ID assignment) in for loop.");
        }


        TreeNode codeBlock = findCodeBlock(forNode);
        if(codeBlock != null) {
            analyzeCodeBlock(codeBlock);
        } else {
            reportError(getLine(forNode), "Missing code block in for loop.");
        }


        symbolTable.exitScope();
    }
    private void analyzeReturnStatement(TreeNode returnNode) {
        if (currentFunction == null) {
            reportError(getLine(returnNode), "Return statement found outside of a function.");
            return;
        }

        String expected = currentFunction.getReturnType();
        TreeNode optEval = findNode(returnNode, "OPT_EVAL");
        TreeNode evalNode = (optEval != null) ? findNode(optEval, "EVAL") : null;

        if (evalNode != null) {
            String actual = analyzeEval(evalNode);
            if (actual != null) {
                if (expected.equals("void")) {
                    reportError(getLine(returnNode),
                            "Function '" + currentFunction.getName() + "' is void and cannot return a value.");
                } else if (!isTypeCompatible(expected, actual)) {
                    reportError(getLine(evalNode),
                            "Type mismatch: Cannot return value of type '" + actual +
                                    "' from function '" + currentFunction.getName() +
                                    "' expecting '" + expected + "'.");
                }
            }
        } else {
            if (!expected.equals("void")) {
                reportError(getLine(returnNode), "Function '" + currentFunction.getName() + "' expects a return value of type '" + expected + "', but return has no value.");
            }
        }
    }

    private String analyzeEval(TreeNode evalNode) {
        // <eval> ::= <expr> <eval'>
        TreeNode exprNode = findNode(evalNode, "EXPR");
        if (exprNode == null) {
            reportError(getLine(evalNode), "Invalid expression structure (missing EXPR in EVAL).");
            return null;
        }

        String currentType = analyzeExpr(exprNode);
        if (currentType == null) return null;

        // Iteramos <eval'>
        TreeNode evalPrimeNode = findNode(evalNode, "EVAL_PRIME");
        while (evalPrimeNode != null && evalPrimeNode.getChildren().size() > 1) {
            TreeNode operatorNode = evalPrimeNode.getChildren().getFirst();
            TreeNode nextExprNode = findNode(evalPrimeNode, "EXPR");

            if (nextExprNode == null) {
                reportError(getLine(evalPrimeNode), "Invalid expression structure (missing EXPR after operator '" + operatorNode.getValue() + "').");
                return null;
            }

            String nextType = analyzeExpr(nextExprNode);
            if (nextType == null) return null;

            String operator = operatorNode.getValue();
            if (isComparisonOperator(operator)) {
                if (!areTypesComparable(currentType, nextType)) {
                    reportError(getLine(operatorNode), "Cannot compare types '" + currentType + "' and '" + nextType + "' using operator '" + operator + "'.");
                    return null;
                }
                currentType = "bool";
            } else if (isBooleanOperator(operator)) {
                if (!isBooleanConvertible(currentType) || !isBooleanConvertible(nextType)) {
                    reportError(getLine(operatorNode), "Operator '" + operator + "' requires boolean-compatible operands, found '" + currentType + "' and '" + nextType + "'.");
                    return null;
                }
                currentType = "bool";
            } else {
                reportError(getLine(operatorNode), "Internal error: Unhandled operator '" + operator + "' in <eval'> chain.");
                return null;
            }

            evalPrimeNode = findNode(evalPrimeNode, "EVAL_PRIME");
        }

        return currentType;
    }

    private String analyzeExpr(TreeNode exprNode) {
        // <expr> ::= <term> <expr'>
        TreeNode termNode = findNode(exprNode, "TERM");
        if (termNode == null) {
            reportError(getLine(exprNode), "Invalid expression structure (missing TERM in EXPR).");
            return null;
        }
        String currentType = analyzeTerm(termNode);
        if (currentType == null) return null;

        TreeNode exprPrimeNode = findNode(exprNode, "EXPR_PRIME");
        while (exprPrimeNode != null && exprPrimeNode.getChildren().size() > 1) {
            TreeNode operatorNode = exprPrimeNode.getChildren().getFirst();
            TreeNode nextTermNode = findNode(exprPrimeNode, "TERM");

            if (nextTermNode == null) {
                reportError(getLine(exprPrimeNode), "Invalid expression structure (missing TERM after operator '" + operatorNode.getValue() + "').");
                return null;
            }
            String nextType = analyzeTerm(nextTermNode);
            if (nextType == null) return null;

            if (!isNumeric(currentType) || !isNumeric(nextType)) {
                reportError(getLine(operatorNode), "Operator '" + operatorNode.getValue() + "' requires numeric operands, found '" + currentType + "' and '" + nextType + "'.");
                return null;
            }
            currentType = promoteNumericType(currentType, nextType);

            exprPrimeNode = findNode(exprPrimeNode, "EXPR_PRIME");
        }
        return currentType;
    }
    private String analyzeTerm(TreeNode termNode) {
        TreeNode factorNode = findNode(termNode, "FACTOR");
        if (factorNode == null) {
            reportError(getLine(termNode), "Invalid expression structure (missing FACTOR in TERM).");
            return null;
        }
        String currentType = analyzeFactor(factorNode);
        if (currentType == null) return null;

        TreeNode termPrimeNode = findNode(termNode, "TERM_PRIME");
        while (termPrimeNode != null && termPrimeNode.getChildren().size() > 1) {
            TreeNode operatorNode = termPrimeNode.getChildren().get(0);
            TreeNode nextFactorNode = findNode(termPrimeNode, "FACTOR");

            if (nextFactorNode == null) {
                reportError(getLine(termPrimeNode), "Invalid expression structure (missing FACTOR after operator '" + operatorNode.getValue() + "').");
                return null;
            }
            String nextType = analyzeFactor(nextFactorNode);
            if (nextType == null) return null;

            if (!isNumeric(currentType) || !isNumeric(nextType)) {
                reportError(getLine(operatorNode), "Operator '" + operatorNode.getValue() + "' requires numeric operands, found '" + currentType + "' and '" + nextType + "'.");
                return null;
            }
            if ("MODULO".equals(operatorNode.getValue()) && (!currentType.equals("int") || !nextType.equals("int"))) {
                reportError(getLine(operatorNode), "Operator '%' requires integer operands, found '" + currentType + "' and '" + nextType + "'.");
                return null;
            }

            currentType = promoteNumericType(currentType, nextType);

            termPrimeNode = findNode(termPrimeNode, "TERM_PRIME");
        }
        return currentType;
    }

    private String analyzeFactor(TreeNode factorNode) {
        TreeNode firstChild = factorNode.getChildren().isEmpty() ? null : factorNode.getChildren().get(0);
        if (firstChild == null) {
            reportError(getLine(factorNode), "Empty factor node encountered.");
            return null;
        }

        String nodeType = firstChild.getValue();

        switch (nodeType) {
            case "LPAREN" -> {
                TreeNode evalNode = findNode(factorNode, "EVAL");
                if (evalNode == null) {
                    reportError(getLine(factorNode), "Malformed parenthesized expression.");
                    return null;
                }
                return analyzeEval(evalNode);
            }
            case "ID" -> {
                String name = firstChild.getAttribute();
                int line = firstChild.getToken().getLine();
                Symbol symbol = symbolTable.lookupSymbol(name);

                if (symbol == null) {
                    reportError(line, "Identifier '" + name + "' not declared.");
                    return null;
                }

                TreeNode fp = findNode(factorNode, "FACTOR_PRIME");
                TreeNode funcCallNode = fp != null ? findNode(fp, "FUNCTION_CALL") : null;
                if (funcCallNode != null) {
                    if (!symbol.isFunction()) {
                        reportError(line, "'" + name + "' is not a function and cannot be called.");
                        return null;
                    }
                    analyzeFunctionCall(firstChild, funcCallNode);
                    return symbol.getReturnType();
                } else {
                    if (symbol.isFunction()) {
                        reportError(line, "Cannot use function '" + name + "' as a value without calling it.");
                        return null;
                    }

                    if (!symbol.isInitialized()) {
                        reportError(line, "Variable '" + name + "' might not have been initialized before use.");
                    }
                    return symbol.getType();
                }
            }
            case "LITERAL" -> {
                return analyzeLiteral(firstChild);
            }
            case "NOT" -> {
                TreeNode nextFactor = findNode(factorNode, "FACTOR");
                if (nextFactor == null) {
                    reportError(getLine(firstChild), "Missing expression after NOT operator.");
                    return null;
                }
                String operandType = analyzeFactor(nextFactor);
                if (operandType != null) {
                    if (!isBooleanConvertible(operandType)) {
                        reportError(getLine(firstChild), "Operator 'NOT' requires a boolean-compatible operand, found '" + operandType + "'.");
                        return null;
                    }
                    return "bool";
                }
                return null;
            }
            case null, default -> {
                reportError(getLine(factorNode), "Unrecognized factor structure starting with '" + nodeType + "'.");
                return null;
            }
        }
    }

    private String analyzeLiteral(TreeNode literalNode) {
        if (literalNode.getChildren().isEmpty()) {
            reportError(getLine(literalNode), "Empty literal node.");
            return null;
        }
        TreeNode typeNode = literalNode.getChildren().getFirst();
        switch (typeNode.getValue()) {
            case "INTEGER_LITERAL": return "int";
            case "FLOAT_LITERAL":   return "flt";
            case "CHAR_LITERAL":    return "chr";
            default:
                reportError(getLine(typeNode), "Unknown literal type: " + typeNode.getValue());
                return null;
        }
    }

    private void analyzeFunctionCallArgs(TreeNode callArgsNode, Symbol functionSymbol) {
        List<String> argumentTypes = new ArrayList<>();
        List<TreeNode> argEvalNodes = findArgumentExpressions(callArgsNode);

        for (TreeNode evalNode : argEvalNodes) {
            String argType = analyzeEval(evalNode);
            if (argType == null) {
                return;
            }
            argumentTypes.add(argType);
        }

        List<Symbol> parameters = functionSymbol.getParameters();
        int callLine = getLine(callArgsNode);

        if (argumentTypes.size() != parameters.size()) {
            reportError(callLine, "Function '" + functionSymbol.getName() + "' expects " + parameters.size() +
                    " arguments, but received " + argumentTypes.size() + ".");
            return;
        }

        for (int i = 0; i < parameters.size(); i++) {
            String expectedType = parameters.get(i).getType();
            String actualType = argumentTypes.get(i);
            if (!isTypeCompatible(expectedType, actualType)) {
                reportError(callLine, "Type mismatch in argument " + (i + 1) + " for function '" + functionSymbol.getName() + "': Expected '" + expectedType + "', but received '" + actualType + "'.");
            }
        }
    }
    private List<TreeNode> findArgumentExpressions(TreeNode callArgsNode) {
        List<TreeNode> args = new ArrayList<>();
        TreeNode argList = findNode(callArgsNode, "ARG_LIST");
        if (argList == null) return args;

        TreeNode firstEval = findNode(argList, "EVAL");
        if (firstEval != null) args.add(firstEval);

        TreeNode next = findNode(argList, "NEXT_ARG");
        while (next != null) {
            TreeNode eval = findNode(next, "EVAL");
            if (eval != null) args.add(eval);
            next = findNode(next, "NEXT_ARG");
        }
        return args;
    }

    private String extractType(TreeNode varTypeNode) {
        if (varTypeNode == null || varTypeNode.getChildren().isEmpty()) return "unknown";
        TreeNode typeTokenNode = varTypeNode.getChildren().get(0);
        switch (typeTokenNode.getValue()) {
            case "INT":   return "int";
            case "FLOAT": return "flt";
            case "CHAR":  return "chr";
            default:      return "unknown";
        }
    }

    private boolean isTypeCompatible(String expected, String actual) {
        if (expected == null || actual == null) return false; // Error posible
        if (expected.equals(actual)) return true;

        if (expected.equals("flt") && actual.equals("int")) return true;

        return false;
    }

    private boolean isNumeric(String type) {
        return "int".equals(type) || "flt".equals(type);
    }

    private boolean areTypesComparable(String type1, String type2) {
        if (type1 == null || type2 == null) return false;

        if (type1.equals(type2)) return true;

        if (isNumeric(type1) && isNumeric(type2)) return true;

        return false;
    }

    private boolean isBooleanConvertible(String type) {
        if (type == null) return false;

        if ("bool".equals(type)) return true;
        return false;
    }

    private String promoteNumericType(String type1, String type2) {
        if ("flt".equals(type1) || "flt".equals(type2)) return "flt";
        if ("int".equals(type1) && "int".equals(type2)) return "int";
        return "unknown";
    }

    private boolean isComparisonOperator(String op) {
        return "EQUALS".equals(op) || "NOT_EQUAL".equals(op) || "LOWER".equals(op) ||
                "LOWER_EQUAL".equals(op) || "GREATER".equals(op) || "GREATER_EQUAL".equals(op);
    }

    private boolean isBooleanOperator(String op) {return "AND".equals(op) || "OR".equals(op);}

    private TreeNode findNode(TreeNode parent, String value) {
        if (parent == null) return null;
        for (TreeNode child : parent.getChildren()) {
            if (value.equals(child.getValue())) {
                return child;
            }
        }
        return null;
    }

    private TreeNode findNodeRecursive(TreeNode startNode, String value) {
        if (startNode == null) return null;
        if (value.equals(startNode.getValue())) return startNode;
        for (TreeNode child : startNode.getChildren()) {
            TreeNode found = findNodeRecursive(child, value);
            if (found != null) return found;
        }
        return null;
    }

    private void analyzeCodeBlock(TreeNode code) {
        for (TreeNode stmt : code.getChildren()) {
            if ("DECLARATION".equals(stmt.getValue())) {
                analyzeDeclaration(stmt);
            }
        }
        for (TreeNode stmt : code.getChildren()) {
            if (!"DECLARATION".equals(stmt.getValue())) {
                analyzeNode(stmt);
            }
        }
    }


    private TreeNode findCodeBlock(TreeNode parent) {
        TreeNode startNode = findNode(parent, "START");
        if (startNode != null) {
            boolean foundStart = false;
            for(TreeNode child : parent.getChildren()) {
                if(child == startNode) {
                    foundStart = true;
                } else if (foundStart && "CODE".equals(child.getValue())) {
                    return child;
                }
            }
            TreeNode codeInChildren = findNode(startNode, "CODE");
            if(codeInChildren != null) return codeInChildren;

        }
        return findNode(parent, "CODE");
    }

    private String getFunctionName(TreeNode funcNode) {
        TreeNode fnTokenNode = findNode(funcNode, "FN");
        TreeNode funcPrimeNode = null;
        TreeNode idNode = null;
        TreeNode mainNode = null;

        if (fnTokenNode != null) {
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

        System.err.println("DEBUG getFunctionName: Failed to find ID or MAIN under FUNCTION_PRIME for node: "
                + funcNode.getValue() + " at line " + getLine(funcNode)
                + ". FN found: " + (fnTokenNode != null)
                + ", FUNC_PRIME found: " + (funcPrimeNode != null));
        return "??unknown??";
    }

    private int getLine(TreeNode node) {
        return (node != null && node.getToken() != null) ? node.getToken().getLine() : -1;
    }

    private void reportError(int line, String message) {
        semanticErrors.add("Semantic Error at line " + line + ": " + message);
    }
}