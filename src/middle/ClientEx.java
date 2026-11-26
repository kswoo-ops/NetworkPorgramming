package middle;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

public class ClientEx extends JFrame {
    private static final String BACKGROUND_PATH = "mainGameBackground.png";
    private static final Color INK = new Color(76, 53, 83);
    private static final Color PAPER = new Color(250, 241, 215, 205);
    private static final Color EDGE = new Color(173, 142, 125, 180);
    private final BufferedImage themeBackground = loadBackground();
    private final BufferedImage grain = createGrain();

    private Socket socket;                  // 💡 대기실에서 전달받을 소켓
    private ObjectOutputStream out;         // 💡 대기실에서 전달받을 출력 스트림
    private ObjectInputStream in;           // 💡 대기실에서 전달받을 입력 스트림
    private String myNick;                  // 💡 내 닉네임
    
    private List<BufferedImage> sketchBook = new ArrayList<>();
    private List<String> pageTypes = new ArrayList<>(); 
    private List<String> textPagesContent = new ArrayList<>(); 
    
    private int myCurrentWriteIndex = 0; 
    private int viewingPageIndex = 0;    
    
    private JPanel centerCardPanel; 
    private CardLayout cardLayout;
    
    private BufferedImage canvasImage;
    private Graphics2D g2d;
    private int prevX = -1, prevY = -1;
    
    private JLabel infoLabel;
    private JLabel wordLabel;
    
    private JButton passButton;
    
    private boolean isGameStarted = false;
    private boolean isWaitingForSwap = false;
    private String myOriginalOwnerId = "TEMP_ID";
    private Color currentDrawColor = Color.BLACK;
    private Color penColor = Color.BLACK;
    private int brushSize = 3;
    private boolean erasing = false;
    private String mySecretWord = "";

    // UI 테스트용 생성자
    public ClientEx() {
        this.myNick = "테스트";
        this.mySecretWord = "사과";
        this.isGameStarted = true;

        initUI();

        wordLabel.setText("내 제시어: [사과]");
        updateUIState();
    }

    // UI 단독 실행용 main 메서드
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ClientEx();
        });
    }

    // 기존 생성자 (서버 연동용)
    public ClientEx(Socket socket, ObjectOutputStream out,
                    ObjectInputStream in, String myNick) {
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.myNick = myNick;

        initUI();
        startListeningServer();
        updateUIState();
    }

    // UI 초기화 공통 메서드
    private void initUI() {
        setTitle("텔레스트레이션 릴레이 스케치북 [" + (myNick != null ? myNick : "플레이어") + "]");
        setSize(1050, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 650));
        BoardPanel board = new BoardPanel();
        setContentPane(board);
        cardLayout = new CardLayout();
        centerCardPanel = new JPanel(cardLayout);
        centerCardPanel.setOpaque(false);
        board.add(centerCardPanel, "canvas");
        addNewPageLocally("DRAW", null, "");

        JPanel topPanel = new PaperPanel(new BorderLayout());
        wordLabel = new JLabel("내 제시어: [게임 준비 후 공개됩니다]", JLabel.CENTER);
        wordLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        wordLabel.setForeground(INK);
        topPanel.add(wordLabel);
        board.add(topPanel, "topic");

        // 왼쪽: 원래 화면 초안처럼 색상 팔레트 배치
        JPanel palette = new PaperPanel(new BorderLayout(0, 5));
        JLabel paletteTitle = new JLabel("색상", JLabel.CENTER);
        paletteTitle.setForeground(INK);
        palette.add(paletteTitle, BorderLayout.NORTH);
        JPanel colors = new JPanel(new GridLayout(5, 3, 4, 4));
        colors.setOpaque(false);
        Color[] swatches = { Color.BLACK, Color.GRAY, Color.WHITE,
            new Color(222, 49, 59), new Color(255, 155, 39), new Color(250, 214, 51),
            new Color(48, 155, 83), new Color(90, 205, 130), new Color(52, 176, 209),
            new Color(51, 86, 193), new Color(132, 74, 180), new Color(234, 107, 164),
            new Color(130, 81, 48), new Color(222, 171, 115), new Color(255, 209, 219) };
        JPanel preview = new JPanel();
        preview.setBackground(Color.BLACK);
        preview.setPreferredSize(new Dimension(20, 24));
        preview.setBorder(BorderFactory.createLineBorder(EDGE));
        for (Color color : swatches) {
            JButton button = new JButton();
            button.setBackground(color);
            button.setOpaque(true);
            button.setContentAreaFilled(true);
            button.setBorder(BorderFactory.createLineBorder(EDGE));
            button.setFocusPainted(false);
            button.setToolTipText(String.format("#%06X", color.getRGB() & 0xffffff));
            button.addActionListener(e -> {
                penColor = color; erasing = false; currentDrawColor = color;
                preview.setBackground(color); updateBrush();
            });
            colors.add(button);
        }
        palette.add(colors, BorderLayout.CENTER);
        palette.add(preview, BorderLayout.SOUTH);
        board.add(palette, "palette");

        infoLabel = new JLabel("", JLabel.CENTER);
        infoLabel.setForeground(new Color(255, 246, 219));
        infoLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        updateInfoLabel();
        board.add(infoLabel, "info");

        // 오른쪽 위: 펜/지우개. 오른쪽 아래는 칠판 장식 전용으로 비워 둠.
        JPanel rightTools = new PaperPanel(new GridLayout(2, 1, 0, 5));
        JButton pen = new JButton("펜");
        JButton eraser = new JButton("지우개");
        styleButton(pen); styleButton(eraser);
        pen.addActionListener(e -> { erasing = false; currentDrawColor = penColor; updateBrush(); });
        eraser.addActionListener(e -> { erasing = true; currentDrawColor = Color.WHITE; updateBrush(); });
        rightTools.add(pen); rightTools.add(eraser);
        board.add(rightTools, "tools");

        JPanel controlPanel = new PaperPanel(new GridBagLayout());
        JLabel sizeLabel = new JLabel("굵기"); sizeLabel.setForeground(INK);
        JSlider brush = new JSlider(1, 24, brushSize);
        brush.setOpaque(false); brush.setPreferredSize(new Dimension(150, 24));
        brush.addChangeListener(e -> { brushSize = brush.getValue(); updateBrush(); });
        passButton = new JButton("제출");
        passButton.setEnabled(false);
        styleButton(passButton);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 6, 0, 6);
        c.gridx = 0; controlPanel.add(sizeLabel, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        controlPanel.add(brush, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        controlPanel.add(passButton, c);
        board.add(controlPanel, "controls");

        passButton.addActionListener(e -> {
            if (!isGameStarted || isWaitingForSwap) return;
            if (out != null) {
                try {
                    syncCurrentPageComponent(); 

                    List<byte[]> allPagesBytes = new ArrayList<>();
                    for (int i = 0; i <= myCurrentWriteIndex; i++) {
                        BufferedImage pageImg = sketchBook.get(i);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(pageImg, "png", baos);
                        allPagesBytes.add(baos.toByteArray());
                    }

                    SketchMessage msg = new SketchMessage("PASS_PAGE", allPagesBytes, myCurrentWriteIndex, myOriginalOwnerId);
                    msg.setTextContents(textPagesContent);
                    
                    synchronized (out) {
                        out.reset();
                        out.writeObject(msg);
                        out.flush();
                    }

                    isWaitingForSwap = true;
                    updateUIState();
                    JOptionPane.showMessageDialog(this, "페이지를 제출했습니다! 다른 참가자들의 작성을 기다립니다.");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        setVisible(true);
    }

    // 💡 대기실(LobbyScreen)에서 게임 시작 시 호출해 주는 메서드
    public void handleGameStart(SketchMessage msg) {
        this.isGameStarted = true;
        this.mySecretWord = msg.getSecretWord();
        this.myOriginalOwnerId = msg.getOwnerId();

        SwingUtilities.invokeLater(() -> {
            wordLabel.setText("🎯 내 제시어: [" + mySecretWord + "] (비밀!)");
            wordLabel.setForeground(new Color(0, 128, 0));
            updateUIState();
            JOptionPane.showMessageDialog(this, "🎮 게임 시작! 첫 번째 페이지(0번)에 제시어 [" + mySecretWord + "]를 그림으로 그려보세요!");
        });
    }

    // 서버 메시지 수신 전용 스레드 메서드
    private void startListeningServer() {
        new Thread(() -> {
            try {
                Object obj;
                while ((obj = in.readObject()) != null) {
                    if (obj instanceof String) {
                        String line = (String) obj;
                        if (line.startsWith("NOTIFY:")) {
                            String msgStr = line.substring(7);
                            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, msgStr));
                        }
                    } else if (obj instanceof SketchMessage) {
                        SketchMessage msg = (SketchMessage) obj;
                        
                        if ("GAME_START".equals(msg.getType())) {
                            isGameStarted = true;
                            mySecretWord = msg.getSecretWord();
                            myOriginalOwnerId = msg.getOwnerId();

                            SwingUtilities.invokeLater(() -> {
                                wordLabel.setText("🎯 내 제시어: [" + mySecretWord + "] (비밀!)");
                                wordLabel.setForeground(new Color(0, 128, 0));
                                updateUIState();
                                JOptionPane.showMessageDialog(this, "🎮 게임 시작! 첫 번째 페이지(0번)에 제시어 [" + mySecretWord + "]를 그림으로 그려보세요!");
                            });
                        } 
                        else if ("RECEIVE_BOOK".equals(msg.getType())) {
                            List<byte[]> receivedPagesBytes = msg.getAllPagesData();
                            List<String> receivedTexts = msg.getTextContents(); 
                            myOriginalOwnerId = msg.getOwnerId(); 
                            boolean isCompleted = msg.isCompleted();

                            SwingUtilities.invokeLater(() -> {
                                sketchBook.clear();
                                pageTypes.clear();
                                textPagesContent.clear();
                                centerCardPanel.removeAll();
                                
                                try {
                                    for (int i = 0; i < receivedPagesBytes.size(); i++) {
                                        byte[] bArr = receivedPagesBytes.get(i);
                                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bArr));
                                        
                                        String pType = (i % 2 == 0) ? "DRAW" : "TEXT";
                                        String tContent = (receivedTexts != null && i < receivedTexts.size()) ? receivedTexts.get(i) : "";
                                        
                                        addNewPageLocally(pType, img, tContent);
                                    }

                                    String nextType = (sketchBook.size() % 2 == 0) ? "DRAW" : "TEXT";
                                    addNewPageLocally(nextType, null, "");
                                    
                                    // 💡 [수정] 서버에 정의되지 않은 "SLIDE_READY" 전송 코드를 제거하여 스트림 충돌 및 연결 끊김 방지
                                    /*
                                    synchronized (out) {
                                        out.writeObject("SLIDE_READY");
                                        out.flush();
                                        out.reset();
                                    }
                                    */

                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                isWaitingForSwap = false;
                                updateUIState();
                                switchPageToView(myCurrentWriteIndex);

                                if (isCompleted) {
                                    JOptionPane.showMessageDialog(this, 
                                        "🎉 릴레이 완료! 처음의 내 스케치북이 돌아왔습니다!", 
                                        "게임 종료", 
                                        JOptionPane.INFORMATION_MESSAGE);
                                } else {
                                    JOptionPane.showMessageDialog(this, "✨ 새로운 스케치북을 전달받았습니다!");
                                }
                            });
                        }
                        else if ("SHOW_SLIDE".equals(msg.getType())) {
                            byte[] pageBytes = msg.getSinglePageData();
                            String bookOwner = msg.getOwnerId();
                            int bIdx = msg.getSlideBookIndex();
                            int pIdx = msg.getSlidePageIndex();

                            try {
                                BufferedImage slideImg = ImageIO.read(new ByteArrayInputStream(pageBytes));
                                
                                SwingUtilities.invokeLater(() -> {
                                    setTitle("텔레스트레이션 [결과 감상 중] - " + (bIdx + 1) + "번째 스케치북 (" + (pIdx + 1) + "페이지)");
                                    showSlideImageOnScreen(slideImg, bookOwner, bIdx + 1, pIdx + 1);
                                });
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        else if ("SLIDE_END".equals(msg.getType())) {
                            SwingUtilities.invokeLater(() -> {
                                setTitle("텔레스트레이션 끝");
                            });
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("서버와의 연결이 끊어졌습니다. 디버깅");
                e.printStackTrace(); // 어떤 예외 때문에 끊겼는지 확인하기 위해 추가
            }
        }).start();
    }

    private void showSlideImageOnScreen(BufferedImage img, String ownerId, int bookNum, int pageNum) {
        JPanel slideViewPanel = new PaperPanel(new BorderLayout(0, 8));
        
        JLabel infoLabel = new JLabel("📖 [" + bookNum + "번째 스케치북] (작성자 ID: " + ownerId.substring(0, Math.min(6, ownerId.length())) + "...) - " + pageNum + "번째 페이지 감상 중", JLabel.CENTER);
        infoLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        infoLabel.setForeground(new Color(0, 102, 204));
        
        JPanel drawArea = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (img != null) {
                    double scale = Math.min((double)getWidth()/img.getWidth(), (double)getHeight()/img.getHeight());
                    int w = (int)(img.getWidth()*scale), h = (int)(img.getHeight()*scale);
                    g.drawImage(img, (getWidth()-w)/2, (getHeight()-h)/2, w, h, this);
                }
            }
        };
        drawArea.setOpaque(false);

        slideViewPanel.add(infoLabel, BorderLayout.NORTH);
        slideViewPanel.add(drawArea, BorderLayout.CENTER);

        centerCardPanel.add(slideViewPanel, "SLIDE_VIEW");
        cardLayout.show(centerCardPanel, "SLIDE_VIEW");
        repaint();
    }

    private void syncCurrentPageComponent() {
        if (myCurrentWriteIndex < pageTypes.size() && "DRAW".equals(pageTypes.get(myCurrentWriteIndex))) {
            return; 
        }

        try {
            JPanel pagePanel = (JPanel) centerCardPanel.getComponent(myCurrentWriteIndex);
            JTextArea targetTextArea = findTextAreaInComponent(pagePanel);
            
            if (targetTextArea != null) {
                String text = targetTextArea.getText();
                
                while (textPagesContent.size() <= myCurrentWriteIndex) {
                    textPagesContent.add("");
                }
                textPagesContent.set(myCurrentWriteIndex, text);
                
                BufferedImage img = sketchBook.get(myCurrentWriteIndex);
                Graphics2D g = img.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, img.getWidth(), img.getHeight());
                g.setColor(Color.BLACK);
                g.setFont(new Font("SansSerif", Font.BOLD, 24));
                
                FontMetrics fm = g.getFontMetrics();
                int y = 80;
                for (String line : text.split("\n")) {
                    g.drawString(line, 50, y);
                    y += fm.getHeight();
                }
                g.dispose();
            } else {
                System.out.println("디버깅 - JTextArea를 찾지 못했습니다!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JTextArea findTextAreaInComponent(Component comp) {
        if (comp instanceof JTextArea) {
            return (JTextArea) comp;
        }
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                JTextArea found = findTextAreaInComponent(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void addNewPageLocally(String type, BufferedImage loadedImage, String initialText) {
        BufferedImage newPage;
        if (loadedImage != null) {
            newPage = loadedImage;
        } else {
            newPage = new BufferedImage(850, 480, BufferedImage.TYPE_INT_RGB);
            Graphics2D gTemp = newPage.createGraphics();
            gTemp.setColor(Color.WHITE);
            gTemp.fillRect(0, 0, 850, 480);
            gTemp.dispose();
        }
        
        sketchBook.add(newPage);
        pageTypes.add(type);
        textPagesContent.add(initialText);
        
        JPanel pagePanel = createPagePanel(type, newPage, sketchBook.size() - 1, initialText);
        centerCardPanel.add(pagePanel, "PAGE_" + (sketchBook.size() - 1));

        myCurrentWriteIndex = sketchBook.size() - 1;
        switchPageToView(myCurrentWriteIndex);
    }

    private JPanel createPagePanel(String type, BufferedImage img, int index, String initialText) {
        JPanel panel = new PaperPanel(new BorderLayout(0, 8));
        if ("DRAW".equals(type)) {
            DrawingPanel drawP = new DrawingPanel(index);
            panel.add(drawP, BorderLayout.CENTER);
            JLabel hint = new JLabel("🎨 [그림 그리기] 앞사람이 적은 글자를 보고 그림으로 표현하세요!", JLabel.CENTER);
            hint.setFont(new Font("SansSerif", Font.BOLD, 14));
            hint.setForeground(INK);
            panel.add(hint, BorderLayout.NORTH);
        } else {
            JPanel textHolder = new JPanel(new GridBagLayout());
            textHolder.setOpaque(false);
            JTextArea textArea = new JTextArea(4, 25);
            textArea.setFont(new Font("SansSerif", Font.BOLD, 22));
            textArea.setLineWrap(true);
            
            if (index < textPagesContent.size() && textPagesContent.get(index) != null && !textPagesContent.get(index).isEmpty()) {
                textArea.setText(textPagesContent.get(index));
                textArea.setEditable(false);
            } else {
                textArea.setText(initialText != null ? initialText : "");
            }
            
            JLabel hint = new JLabel("✍️ [글자 적기] 앞사람이 그린 그림을 보고 무엇인지 글자로 적어주세요!", JLabel.CENTER);
            hint.setFont(new Font("SansSerif", Font.BOLD, 14));
            hint.setForeground(INK);
            
            textArea.setOpaque(false);
            textArea.setForeground(INK);
            textArea.setCaretColor(INK);
            textArea.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            JScrollPane scroll = new JScrollPane(textArea);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setBorder(BorderFactory.createLineBorder(EDGE));
            PaperPanel writingPaper = new PaperPanel(new BorderLayout());
            writingPaper.add(scroll);
            textHolder.add(writingPaper);
            panel.add(hint, BorderLayout.NORTH);
            panel.add(textHolder, BorderLayout.CENTER);
        }
        return panel;
    }

    private void switchPageToView(int targetIndex) {
        if (sketchBook.isEmpty()) return;
        if (targetIndex < 0 || targetIndex >= sketchBook.size()) {
            targetIndex = sketchBook.size() - 1;
        }
        
        viewingPageIndex = targetIndex;
        String currentType = pageTypes.get(viewingPageIndex);
        
        if ("DRAW".equals(currentType)) {
            canvasImage = sketchBook.get(viewingPageIndex);
            if (g2d != null) g2d.dispose();
            g2d = canvasImage.createGraphics();
            updateBrush();
        }
        
        cardLayout.show(centerCardPanel, "PAGE_" + viewingPageIndex);
        updateInfoLabel();
        repaint();
    }

    private void updateInfoLabel() {
        if (infoLabel != null) {
            boolean isMyTurnPage = (viewingPageIndex == myCurrentWriteIndex);
            String statusStr = isMyTurnPage ? "✍️ 내 작성 칸" : "👀 앞사람 기록 보기 중";
            infoLabel.setText("<html><center>페이지 " + (viewingPageIndex + 1) + " / " + sketchBook.size() + "<br>" + statusStr + "</center></html>");
        }
    }

    private void updateUIState() {
        if (!isGameStarted) {
            passButton.setEnabled(false);
            setTitle("텔레스트레이션 [" + (myNick != null ? myNick : "") + " - 게임 대기 중]");
        } else {
            passButton.setEnabled(!isWaitingForSwap);
            if (isWaitingForSwap) {
                setTitle("텔레스트레이션 [" + myNick + " - 제출 완료, 다음 스케치북 대기 중... ⏳]");
            } else {
                setTitle("텔레스트레이션 [" + myNick + " - 작성 중 🎨✍️]");
            }
        }
    }


    private void updateBrush() {
        if (g2d != null) {
            g2d.setColor(currentDrawColor);
            g2d.setStroke(new BasicStroke(erasing ? Math.max(12, brushSize * 2) : brushSize,
                    BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }
    }

    // Layout and artwork share the same coordinate system: no background cropping.
    private class BoardPanel extends JPanel {
        private final Map<String, Component> regions = new HashMap<>();
        BoardPanel() { super(null); setBackground(new Color(15, 73, 48)); }
        @Override protected void addImpl(Component c, Object constraint, int index) {
            super.addImpl(c, constraint, index);
            if (constraint instanceof String) regions.put((String) constraint, c);
        }
        private Rectangle artwork() {
            double aspect = themeBackground == null ? 1.55 : (double) themeBackground.getWidth() / themeBackground.getHeight();
            int w = getWidth(), h = Math.min(getHeight(), (int)Math.round(w / aspect));
            w = Math.min(w, (int)Math.round(h * aspect));
            return new Rectangle((getWidth()-w)/2, (getHeight()-h)/2, w, h);
        }
        private void place(String key, Rectangle r, double x, double y, double w, double h) {
            Component c = regions.get(key);
            if (c != null) c.setBounds(r.x+(int)(r.width*x), r.y+(int)(r.height*y),
                    (int)(r.width*w), (int)(r.height*h));
        }
        @Override public void doLayout() {
            Rectangle r = artwork();
            // Top 15%: original logo. Bottom-right: original chalk writing.
            place("info", r, .025, .15, .145, .09);
            place("palette", r, .035, .28, .135, .43);
            place("topic", r, .19, .15, .59, .075);
            place("canvas", r, .19, .235, .59, .51);
            place("controls", r, .19, .755, .59, .15);
            place("tools", r, .81, .24, .16, .19);
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Rectangle r = artwork();
            Graphics2D gg = (Graphics2D) g.create();
            gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (themeBackground != null) gg.drawImage(themeBackground, r.x, r.y, r.width, r.height, this);
            gg.dispose();
        }
    }

    // Theme assets: project working directory, src/middle, or classpath.
    private BufferedImage loadBackground() {
        String[] fileCandidates = {
                BACKGROUND_PATH,
                "src/middle/" + BACKGROUND_PATH,
                "middle/" + BACKGROUND_PATH,
                "image/" + BACKGROUND_PATH,
                "src/image/" + BACKGROUND_PATH,
                "src/middle/image/" + BACKGROUND_PATH,
                "../image/" + BACKGROUND_PATH,
                "../src/middle/" + BACKGROUND_PATH,
                "../../image/" + BACKGROUND_PATH,
                "../../src/middle/image/" + BACKGROUND_PATH
        };
        for (String path : fileCandidates) {
            try {
                File f = new File(path);
                if (f.isFile()) {
                    BufferedImage img = ImageIO.read(f);
                    if (img != null) return img;
                }
            } catch (IOException ignored) { }
        }
        String[] resourceCandidates = {"/" + BACKGROUND_PATH, "/middle/" + BACKGROUND_PATH, "/image/" + BACKGROUND_PATH, "/middle/image/" + BACKGROUND_PATH};
        for (String path : resourceCandidates) {
            try (InputStream stream = getClass().getResourceAsStream(path)) {
                if (stream != null) {
                    BufferedImage img = ImageIO.read(stream);
                    if (img != null) return img;
                }
            } catch (IOException ignored) { }
        }
        return null;
    }

    private BufferedImage createGrain() {
        BufferedImage tile = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(42);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            int v = random.nextBoolean() ? 75 : 255;
            tile.setRGB(x, y, new Color(v, v, v, random.nextInt(19)).getRGB());
        }
        return tile;
    }

    private class PaperPanel extends JPanel {
        PaperPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D gg = (Graphics2D) g.create();
            gg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            java.awt.geom.RoundRectangle2D shape = new java.awt.geom.RoundRectangle2D.Float(1, 1, Math.max(0,getWidth()-3), Math.max(0,getHeight()-3), 18, 18);
            gg.setColor(PAPER); gg.fill(shape);
            gg.setPaint(new TexturePaint(grain, new Rectangle(0, 0, 128, 128))); gg.fill(shape);
            gg.setColor(EDGE); gg.draw(shape);
            gg.dispose();
        }
    }

    private void styleButton(JButton button) {
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setForeground(INK);
        button.setBackground(new Color(250, 227, 230));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(EDGE), BorderFactory.createEmptyBorder(7, 11, 7, 11)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // Only the display is changed. The PNG sent to the server keeps its original white background.
    private void paintOnPaper(Graphics g, BufferedImage image, int x, int y) {
        BufferedImage display = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        for (int i = 0; i < pixels.length; i++) {
            if ((pixels[i] & 0x00ffffff) == 0x00ffffff) pixels[i] = 0;
        }
        display.setRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
        g.drawImage(display, x, y, this);
    }

    private class DrawingPanel extends JPanel {
        private int pageIdx;
        public DrawingPanel(int pageIdx) {
            this.pageIdx = pageIdx;
            setOpaque(false);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!isGameStarted || isWaitingForSwap || viewingPageIndex != myCurrentWriteIndex) return;
                    prevX = imageX(e.getX());
                    prevY = imageY(e.getY());
                }
                @Override
                public void mouseReleased(MouseEvent e) {
                    prevX = -1; prevY = -1;
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!isGameStarted || isWaitingForSwap || viewingPageIndex != myCurrentWriteIndex) return;
                    int x = imageX(e.getX()); int y = imageY(e.getY());
                    if (prevX != -1 && prevY != -1 && g2d != null && viewingPageIndex == pageIdx) {
                        g2d.setColor(currentDrawColor);
                        g2d.drawLine(prevX, prevY, x, y);
                        repaint();
                    }
                    prevX = x; prevY = y;
                }
            });
        }
        private int imageX(int x) {
            BufferedImage image = sketchBook.get(pageIdx);
            return Math.max(0, Math.min(image.getWidth()-1, x * image.getWidth() / Math.max(1, getWidth())));
        }
        private int imageY(int y) {
            BufferedImage image = sketchBook.get(pageIdx);
            return Math.max(0, Math.min(image.getHeight()-1, y * image.getHeight() / Math.max(1, getHeight())));
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (pageIdx < sketchBook.size() && sketchBook.get(pageIdx) != null) {
                g.drawImage(sketchBook.get(pageIdx), 0, 0, getWidth(), getHeight(), this);
            }
        }
    }
}


