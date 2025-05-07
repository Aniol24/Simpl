package BackEnd;

public class TACInstruction {
    private final String op, arg1, arg2, result;

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
            if (result == null || result.isEmpty()) { // Check if result is effectively null or empty
                return "call " + arg1;
            } else {
                return result + " = call " + arg1;
            }
        }
        // 6) Return
        if ("return".equals(op)) {
            return (arg1 != null ? "return " + arg1 : "return"); // Handle return with no value
        }
        // 7) Simple assignment
        if ("=".equals(op)) {
            return result + " = " + arg1;
        }
        // 8) Unary operations
        if ("NOT".equals(op)) {
            return result + " = NOT " + arg1;
        }
        // 9) Binary operations and comparisons
        String sym = mapSymbol(op);
        if (sym != null) {
            return result + " = " + arg1 + " " + sym + " " + arg2;
        }
        // 10) Error si queda algo sin mapear
        System.err.println("Operación desconocida en TACInstruction.toString(): " + op); // Log error
        return String.format("%s = %s %s %s (Unknown OP: %s)", result, arg1, op, arg2, op); // Fallback representation
    }

    private String mapSymbol(String op) {
        switch (op) {
            case "SUM": return "+";
            case "SUB": return "-";
            case "MULT": return "*";
            case "DIV": return "/";
            case "MOD": return "%"; // Added MOD
            case "EQ": // Fallthrough
            case "EQUALS": return "=="; // Using == for clarity, adjust if TAC interpreter expects "EQUALS"
            case "NOT_EQUAL": return "!=";
            case "LOWER": return "<";
            case "GREATER": return ">";
            case "LOWER_EQUAL": return "<=";
            case "GREATER_EQUAL": return ">=";
            case "AND": return "&&"; // Common symbols for logical ops
            case "OR": return "||";
            default: return null;
        }
    }
}
