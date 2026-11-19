package middle;

import java.io.Serializable;
import java.util.List;

public class SketchMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private List<String> textContents;
    private String type;
    private List<byte[]> allPagesData;
    private int pageIndex;
    private int x1, y1, x2, y2;
    private String ownerId;
    private boolean completed;
    private String secretWord;
    private byte[] singlePageData; // 단일 페이지 전송용
    private String ownerName;      // 누구의 스케치북인지 표시용
    private int slideBookIndex;    // 현재 몇 번째 책인지
    private int slidePageIndex;    // 현재 책의 몇 번째 페이지인지

    public List<String> getTextContents() { return textContents; }
    public void setTextContents(List<String> textContents) { this.textContents = textContents; }
    public SketchMessage(String type, List<byte[]> allPagesData, int pageIndex, String ownerId) {
        this.type = type;
        this.allPagesData = allPagesData;
        this.pageIndex = pageIndex;
        this.ownerId = ownerId;
    }
    public SketchMessage(String type, byte[] singlePageData) {
        this.type = type;
        this.singlePageData = singlePageData;
    }
    public SketchMessage(int x1, int y1, int x2, int y2) {
        this.type = "DRAW_LINE";
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }
    
 // 슬라이드 정보(책 번호, 페이지 번호, 총 페이지 수 등)를 세팅하는 메서드
    public void setSlideInfo(int bookIndex, int pageIndex, int totalPages) {
        this.slideBookIndex = bookIndex;
        this.slidePageIndex = pageIndex;
    }

    // 데이터를 꺼내 쓸 수 있는 Getter 메서드들도 함께 추가해 주세요!
    public byte[] getSinglePageData() {
        return singlePageData;
    }

    public void setSinglePageData(byte[] singlePageData) {
        this.singlePageData = singlePageData;
    }

    public int getSlideBookIndex() {
        return slideBookIndex;
    }

    public int getSlidePageIndex() {
        return slidePageIndex;
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