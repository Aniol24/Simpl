package Utils;

public class Pair {
    private Token fila;
    private Token columna;

    public Pair(Token fila, Token columna) {
        this.fila = fila;
        this.columna = columna;
    }

    public Token getFila() {
        return fila;
    }

    public Token getColumna() {
        return columna;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Pair pair = (Pair) o;

        if (fila != pair.fila) return false;
        return columna == pair.columna;
    }

    @Override
    public int hashCode() {
        int result = fila != null ? fila.hashCode() : 0;
        result = 31 * result + (columna != null ? columna.hashCode() : 0);
        return result;
    }
}
