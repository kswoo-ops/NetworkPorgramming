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
    private static final String BACKGROUND_PATH = "/image/mainGameBackground.png";
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
    private JButton viewPrevButton;  
    private JButton goMyPageButton;  
    
    private boolean isGameStarted = false;
    private boolean isWaitingForSwap = false;
    private String myOriginalOwnerId = "TEMP_ID";
    private Color currentDrawColor = Color.BLACK;
    private String mySecretWord = ""; 

    // 💡 대기실(LobbyScreen)에서 호출할 수 있도록 인자를 받는 생성자 추가
    public ClientEx(Socket socket, ObjectOutputStream out, ObjectInputStream in, String myNick) {
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.myNick = myNick;

        initUI();
        
        // 이미 대기실을 통해 서버에 접속되어 있으므로, 수신 스레드만 곧바로 시작
        startListeningServer();
        updateUIState();
    }

    // UI 초기화 공통 메서드
    private void initUI() {
        setTitle("텔레스트레이션 릴레이 스케치북 [" + (myNick != null ? myNick : "플레이어") + "]");
        setSize(1050, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(new JPanel(new BorderLayout(10, 10)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D gg = (Graphics2D) g.create();
                gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                gg.setPaint(new GradientPaint(0, 0, new Color(211, 177, 224), getWidth(), getHeight(), new Color(250, 207, 219)));
                gg.fillRect(0, 0, getWidth(), getHeight());
                if (themeBackground != null) {
                    double scale = Math.max((double)getWidth()/themeBackground.getWidth(), (double)getHeight()/themeBackground.getHeight());
                    int w = (int)Math.ceil(themeBackground.getWidth()*scale), h = (int)Math.ceil(themeBackground.getHeight()*scale);
                    gg.drawImage(themeBackground, (getWidth()-w)/2, (getHeight()-h)/2, w, h, this);
                }
                gg.dispose();
            }
        });
        ((JComponent)getContentPane()).setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        cardLayout = new CardLayout();
        centerCardPanel = new JPanel(cardLayout);
        centerCardPanel.setOpaque(false);
        add(centerCardPanel, BorderLayout.CENTER);

        addNewPageLocally("DRAW", null, "");

        JPanel topPanel = new PaperPanel(new FlowLayout(FlowLayout.CENTER, 10, 12));
        wordLabel = new JLabel("🎯 내 제시어: [게임 준비 후 공개됩니다]");
        wordLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        wordLabel.setForeground(Color.DARK_GRAY);
        topPanel.add(wordLabel);
        add(topPanel, BorderLayout.NORTH);

        JPanel controlPanel = new PaperPanel(new GridLayout(2, 1, 0, 4));
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 3));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 3));
        tools.setOpaque(false);
        actions.setOpaque(false);
        controlPanel.add(tools);
        controlPanel.add(actions);
        
        JButton blackBtn = new JButton("검정");
        blackBtn.setForeground(Color.BLACK);
        JButton redBtn = new JButton("빨강");
        redBtn.setForeground(Color.RED);
        JButton blueBtn = new JButton("파랑");
        blueBtn.setForeground(Color.BLUE);

        blackBtn.addActionListener(e -> { currentDrawColor = Color.BLACK; if (g2d != null) g2d.setColor(currentDrawColor); });
        redBtn.addActionListener(e -> { currentDrawColor = Color.RED; if (g2d != null) g2d.setColor(currentDrawColor); });
        blueBtn.addActionListener(e -> { currentDrawColor = Color.BLUE; if (g2d != null) g2d.setColor(currentDrawColor); });

        viewPrevButton = new JButton("📖 앞사람 기록 보기");
        viewPrevButton.setBackground(new Color(200, 220, 255));
        viewPrevButton.addActionListener(e -> {
            if (sketchBook.size() <= 1) {
                JOptionPane.showMessageDialog(this, "아직 앞사람이 남긴 기록이 없습니다!");
                return;
            }
            viewingPageIndex = (viewingPageIndex > 0) ? viewingPageIndex - 1 : 0;
            switchPageToView(viewingPageIndex);
        });

        goMyPageButton = new JButton("✍️ 내 페이지로");
        goMyPageButton.setBackground(new Color(255, 230, 150));
        goMyPageButton.addActionListener(e -> {
            switchPageToView(myCurrentWriteIndex);
        });

        infoLabel = new JLabel();
        updateInfoLabel();
        
        passButton = new JButton("🔄 이 페이지 작성 완료 및 넘기기");
        passButton.setEnabled(false);
        passButton.setBackground(new Color(100, 200, 100));

        tools.add(blackBtn);
        tools.add(redBtn);
        tools.add(blueBtn);
        tools.add(viewPrevButton);
        tools.add(goMyPageButton); 
        actions.add(infoLabel);
        actions.add(passButton);
        
        for (JButton b : new JButton[]{blackBtn, redBtn, blueBtn, viewPrevButton, goMyPageButton, passButton}) styleButton(b);
        blackBtn.setForeground(Color.BLACK);
        redBtn.setForeground(new Color(180, 48, 68));
        blueBtn.setForeground(new Color(48, 82, 160));
        infoLabel.setForeground(INK);
        wordLabel.setForeground(INK);
        add(controlPanel, BorderLayout.SOUTH);

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
                    int x = (getWidth() - img.getWidth()) / 2;
                    int y = (getHeight() - img.getHeight()) / 2;
                    paintOnPaper(g, img, Math.max(0, x), Math.max(0, y));
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
            g2d = canvasImage.createGraphics();
            g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(currentDrawColor);
        }
        
        cardLayout.show(centerCardPanel, "PAGE_" + viewingPageIndex);
        updateInfoLabel();
        repaint();
    }

    private void updateInfoLabel() {
        if (infoLabel != null) {
            boolean isMyTurnPage = (viewingPageIndex == myCurrentWriteIndex);
            String statusStr = isMyTurnPage ? "✍️ 내 작성 칸" : "👀 앞사람 기록 보기 중";
            infoLabel.setText("📖 페이지 (" + (viewingPageIndex + 1) + "/" + sketchBook.size() + ") [" + statusStr + "]");
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


    // Theme assets: project working directory, src/middle, or classpath.
    private BufferedImage loadBackground() {
        String[] fileCandidates = {
                BACKGROUND_PATH,
                "src/middle/" + BACKGROUND_PATH,
                "middle/" + BACKGROUND_PATH,
                "/mnt/data/ghostwriter_images/context/b28cf400-7bfb-544e-a074-bc0a00305a12.png"
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
        String[] resourceCandidates = {"/" + BACKGROUND_PATH, "/middle/" + BACKGROUND_PATH};
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
                    prevX = e.getX();
                    prevY = e.getY();
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
                    int x = e.getX(); int y = e.getY();
                    if (prevX != -1 && prevY != -1 && g2d != null && viewingPageIndex == pageIdx) {
                        g2d.setColor(currentDrawColor);
                        g2d.drawLine(prevX, prevY, x, y);
                        repaint();
                    }
                    prevX = x; prevY = y;
                }
            });
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (pageIdx < sketchBook.size() && sketchBook.get(pageIdx) != null) {
                paintOnPaper(g, sketchBook.get(pageIdx), 0, 0);
            }
        }
    }
}