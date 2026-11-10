package middle;

import java.io.Serializable;
import java.util.List;

public class SketchMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String type;
    private List<byte[]> allPagesData;
    private int pageIndex;
    private int x1, y1, x2, y2;
    private String ownerId;
    private boolean completed;
    private String secretWord;

    public SketchMessage(String type, List<byte[]> allPagesData, int pageIndex, String ownerId) {
        this.type = type;
        this.allPagesData = allPagesData;
        this.pageIndex = pageIndex;
        this.ownerId = ownerId;
    }

    public SketchMessage(int x1, int y1, int x2, int y2) {
        this.type = "DRAW_LINE";
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    public String getType() { return type; }
    public List<byte[]> getAllPagesData() { return allPagesData; }
    public int getPageIndex() { return pageIndex; }
    public int getX1() { return x1; }
    public int getY1() { return y1; }
    public int getX2() { return x2; }
    public int getY2() { return y2; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public String getSecretWord() { return secretWord; }
    public void setSecretWord(String secretWord) { this.secretWord = secretWord; }
}