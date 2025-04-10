package Lexicon;

public class Line {

    private final int lineNum;
    private final String lineContent;

    public Line(int lineNum, String lineContent) {
        this.lineNum = lineNum;
        this.lineContent = lineContent;
    }

    public int getLineNum() {
        return lineNum;
    }

    public String getLineContent() {
        return lineContent;
    }
}
