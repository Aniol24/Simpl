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
            if(result == null){
                return "call " + arg1;
            }else{
                return result + " = call " + arg1;
            }

        }
        // 6) Return
        if ("return".equals(op)) {
            return "return " + arg1;
        }
        // 7) Simple assignment
        if ("=".equals(op)) {
            return result + " = " + arg1;
        }
        // 8) Binary operations and comparisons
        String sym = mapSymbol(op);
        if (sym != null) {
            return result + " = " + arg1 + " " + sym + " " + arg2;
        }
        // 9) Error si queda algo sin mapear
        throw new IllegalStateException("Operación desconocida en TAC: " + op);
    }

    private String mapSymbol(String op) {
        switch (op) {
            case "SUM":        return "+";
            case "SUB":        return "-";
            case "MULT":        return "*";
            case "DIV":        return "/";
            case "EQ":         // si tu gramática emite "EQ"
            case "EQUALS":     // ya lo tenías para "=="
                return "EQUALS";
            case "NOT_EQUAL":  return "NOT_EQUAL";// nuevo para "!="
            case "LOWER":         return "LOWER";
            case "GREATER":         return "GREATER";
            case "LOWER_EQUAL":         return "LOWER_EQUAL";
            case "GREATER_EQUAL":         return "GREATER_EQUAL";
            default:           return null;
        }
    }




}
