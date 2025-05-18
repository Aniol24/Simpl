package FrontEnd.TAC;

public class TACInstruction {
    /**
     * L'operació TAC
     */
    private final String op, arg1, arg2, result;

    /**
     * Constructor de la classe TACInstruction
     *
     * @param op    Operació TAC
     * @param arg1  Primer argument
     * @param arg2  Segon argument
     * @param result Resultat
     */
    public TACInstruction(String op, String arg1, String arg2, String result) {
        this.op = op;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.result = result;
    }

    @Override
    public String toString() {
        // 1) Label
        if ("label".equals(op)) {
            return result + ":";
        }
        // 2) Unconditional jump
        if ("goto".equals(op)) {
            return "goto " + result;
        }
        // 3) Conditional jump
        if ("ifFalse".equals(op)) {
            return "ifFalse " + arg1 + " goto " + result;
        }
        // 4) Param passing
        if ("param".equals(op)) {
            return "param " + arg1;
        }
        // 5) Call with return
        if ("call".equals(op)) {
            if (result == null || result.isEmpty()) {
                return "call " + arg1;
            } else {
                return result + " = call " + arg1;
            }
        }
        // 6) Return
        if ("return".equals(op)) {
            return (arg1 != null && !arg1.isEmpty()) ? "return " + arg1 : "return";
        }
        // 7) Simple assignment
        if ("=".equals(op)) {
            return result + " = " + arg1;
        }
        // 8) Unary NOT
        if ("NOT".equals(op)) {
            return result + " = NOT " + arg1;
        }
        // 9) Binary ops/comparisons
        String sym = mapSymbol(op);
        if (sym != null) {
            return result + " = " + arg1 + " " + sym + " " + arg2;
        }
        // 10) Fallback
        throw new RuntimeException("Operación desconocida en TACInstruction.toString(): " + op);
    }

    /**
     * Mapa d'operadors TAC
     *
     * @param op Operador a comprovar
     * @return Operador TAC corresponent
     */
    private String mapSymbol(String op) {
        switch (op) {
            // arithmetic
            case "SUM":       return "+";
            case "SUB":       return "-";
            case "MULT":      return "*";
            case "DIV":       return "/";
            case "MOD":       return "%";

            // equality
            case "EQ":
            case "EQUALS":
            case "==":        return "==";
            case "NOT_EQUAL":
            case "!=":        return "!=";

            // relational
            case "LOWER":
            case "<":         return "<";
            case "LOWER_EQUAL":
            case "<=":        return "<=";
            case "GREATER":
            case ">":         return ">";
            case "GREATER_EQUAL":
            case ">=":        return ">=";

            // boolean
            case "AND":
            case "&&":        return "&&";
            case "OR":
            case "||":        return "||";

            default:
                return null;
        }
    }

    /**
     * Getters
     */
    public String getOp()    { return op;    }
    public String getArg1()  { return arg1;  }
    public String getArg2()  { return arg2;  }
    public String getResult(){ return result;}
}
