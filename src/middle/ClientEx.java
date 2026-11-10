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
    private ObjectOutputStream out;
    private ObjectInputStream in;
    
    private List<BufferedImage> sketchBook = new ArrayList<>();
    private List<String> pageTypes = new ArrayList<>(); 
    
    private int myCurrentWriteIndex = 0; // 내가 작성 중인 페이지 인덱스
    private int viewingPageIndex = 0;    // 현재 화면에 띄워져 있는 페이지 인덱스 (조회용)
    
    private JPanel centerCardPanel; 
    private CardLayout cardLayout;
    
    private BufferedImage canvasImage;
    private Graphics2D g2d;
    private int prevX = -1, prevY = -1;
    
    private JLabel infoLabel;
    private JLabel wordLabel;
    
    private JButton readyButton;
    private JButton passButton;
    private JButton viewPrevButton;  // 앞사람 기록 보기 버튼
    private JButton goMyPageButton;  // 💡 내 페이지로 돌아가기 버튼 추가
    
    private boolean isGameStarted = false;
    private boolean isWaitingForSwap = false;
    private String myOriginalOwnerId = "TEMP_ID";
    private Color currentDrawColor = Color.BLACK;
    private String mySecretWord = ""; 

    public ClientEx() {
        setTitle("텔레스트레이션 릴레이 스케치북 [대기실]");
        setSize(1050, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        cardLayout = new CardLayout();
        centerCardPanel = new JPanel(cardLayout);
        add(centerCardPanel, BorderLayout.CENTER);

        addNewPageLocally("DRAW", null);

        JPanel topPanel = new JPanel();
        wordLabel = new JLabel("🎯 내 제시어: [게임 준비 후 공개됩니다]");
        wordLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        wordLabel.setForeground(Color.DARK_GRAY);
        topPanel.add(wordLabel);
        add(topPanel, BorderLayout.NORTH);

        JPanel controlPanel = new JPanel();
        
        readyButton = new JButton("🎮 게임 준비");
        readyButton.setBackground(Color.ORANGE);
        
        JButton blackBtn = new JButton("검정");
        blackBtn.setForeground(Color.BLACK);
        JButton redBtn = new JButton("빨강");
        redBtn.setForeground(Color.RED);
        JButton blueBtn = new JButton("파랑");
        blueBtn.setForeground(Color.BLUE);

        blackBtn.addActionListener(e -> { currentDrawColor = Color.BLACK; if (g2d != null) g2d.setColor(currentDrawColor); });
        redBtn.addActionListener(e -> { currentDrawColor = Color.RED; if (g2d != null) g2d.setColor(currentDrawColor); });
        blueBtn.addActionListener(e -> { currentDrawColor = Color.BLUE; if (g2d != null) g2d.setColor(currentDrawColor); });

        // 앞사람 기록 보기 버튼
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

        // 💡 내 페이지로 돌아가기 버튼
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

        readyButton.addActionListener(e -> {
            if (out != null) {
                try {
                    out.writeObject("PLAYER_READY");
                    out.flush();
                    out.reset();
                    readyButton.setEnabled(false);
                    readyButton.setText("✔ 준비 완료");
                    JOptionPane.showMessageDialog(this, "준비 완료! 다른 참가자들을 기다립니다.");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        controlPanel.add(readyButton);
        controlPanel.add(Box.createHorizontalStrut(5));
        controlPanel.add(blackBtn);
        controlPanel.add(redBtn);
        controlPanel.add(blueBtn);
        controlPanel.add(Box.createHorizontalStrut(10));
        controlPanel.add(viewPrevButton);
        controlPanel.add(goMyPageButton); // 컨트롤 패널에 추가
        controlPanel.add(Box.createHorizontalStrut(10));
        controlPanel.add(infoLabel);
        controlPanel.add(Box.createHorizontalStrut(10));
        controlPanel.add(passButton);
        
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
                    out.writeObject(msg);
                    out.flush();
                    out.reset();

                    isWaitingForSwap = true;
                    updateUIState();
                    JOptionPane.showMessageDialog(this, "페이지를 제출했습니다! 다른 참가자들의 작성을 기다립니다.");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        setVisible(true);
        connectToServer();
        updateUIState();
    }

    private void syncCurrentPageComponent() {
        Component comp = ((JPanel)centerCardPanel.getComponent(myCurrentWriteIndex)).getComponent(1);
        if (comp instanceof JScrollPane) {
            JViewport vp = ((JScrollPane) comp).getViewport();
            if (vp.getView() instanceof JTextArea) {
                JTextArea ta = (JTextArea) vp.getView();
                BufferedImage img = sketchBook.get(myCurrentWriteIndex);
                Graphics2D g = img.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, img.getWidth(), img.getHeight());
                g.setColor(Color.BLACK);
                g.setFont(new Font("SansSerif", Font.BOLD, 24));
                
                FontMetrics fm = g.getFontMetrics();
                int y = 50;
                for (String line : ta.getText().split("\n")) {
                    g.drawString(line, 40, y);
                    y += fm.getHeight();
                }
                g.dispose();
            }
        }
    }

    private void addNewPageLocally(String type, BufferedImage loadedImage) {
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
        
        JPanel pagePanel = createPagePanel(type, newPage, sketchBook.size() - 1);
        centerCardPanel.add(pagePanel, "PAGE_" + (sketchBook.size() - 1));

        myCurrentWriteIndex = sketchBook.size() - 1;
        switchPageToView(myCurrentWriteIndex);
    }

    private JPanel createPagePanel(String type, BufferedImage img, int index) {
        JPanel panel = new JPanel(new BorderLayout());
        if ("DRAW".equals(type)) {
            DrawingPanel drawP = new DrawingPanel(index);
            panel.add(drawP, BorderLayout.CENTER);
            JLabel hint = new JLabel("🎨 [그림 그리기] 앞사람이 적은 글자를 보고 그림으로 표현하세요!", JLabel.CENTER);
            hint.setFont(new Font("SansSerif", Font.BOLD, 14));
            panel.add(hint, BorderLayout.NORTH);
        } else {
            JPanel textHolder = new JPanel(new GridBagLayout());
            JTextArea textArea = new JTextArea(4, 25);
            textArea.setFont(new Font("SansSerif", Font.BOLD, 22));
            textArea.setLineWrap(true);
            
            JLabel hint = new JLabel("✍️ [글자 적기] 앞사람이 그린 그림을 보고 무엇인지 글자로 적어주세요!", JLabel.CENTER);
            hint.setFont(new Font("SansSerif", Font.BOLD, 14));
            
            textHolder.add(new JScrollPane(textArea));
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
            setTitle("텔레스트레이션 [게임 대기 중]");
        } else {
            passButton.setEnabled(!isWaitingForSwap);
            if (isWaitingForSwap) {
                setTitle("텔레스트레이션 [제출 완료, 다음 스케치북 대기 중... ⏳]");
            } else {
                setTitle("텔레스트레이션 [작성 중 🎨✍️]");
            }
        }
    }

    private void connectToServer() {
        try {
            Socket socket = new Socket("localhost", 5000);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            new Thread(() -> {
                try {
                    Object obj;
                    while ((obj = in.readObject()) != null) {
                        if (obj instanceof String) {
                            String line = (String) obj;
                            if (line.startsWith("NOTIFY:")) {
                                String msg = line.substring(7);
                                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, msg));
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
                            } else if ("RECEIVE_BOOK".equals(msg.getType())) {
                                List<byte[]> receivedPagesBytes = msg.getAllPagesData();
                                myOriginalOwnerId = msg.getOwnerId(); 
                                boolean isCompleted = msg.isCompleted();

                                SwingUtilities.invokeLater(() -> {
                                    sketchBook.clear();
                                    pageTypes.clear();
                                    centerCardPanel.removeAll();
                                    
                                    try {
                                        for (int i = 0; i < receivedPagesBytes.size(); i++) {
                                            byte[] bArr = receivedPagesBytes.get(i);
                                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bArr));
                                            
                                            String pType = (i % 2 == 0) ? "DRAW" : "TEXT";
                                            addNewPageLocally(pType, img);
                                        }

                                        String nextType = (sketchBook.size() % 2 == 0) ? "DRAW" : "TEXT";
                                        addNewPageLocally(nextType, null);

                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }

                                    isWaitingForSwap = false;
                                    updateUIState();
                                    switchPageToView(myCurrentWriteIndex);

                                    if (isCompleted) {
                                        JOptionPane.showMessageDialog(this, 
                                            "🎉 릴레이 완료! 처음의 내 스케치북이 돌아왔습니다! 앞사람들이 남긴 그림과 글을 순서대로 넘겨보며 확인해보세요! 🏁", 
                                            "게임 종료", 
                                            JOptionPane.INFORMATION_MESSAGE);
                                    } else {
                                        JOptionPane.showMessageDialog(this, "✨ 새로운 스케치북을 전달받았습니다! 앞사람이 그린/적은 내용을 확인하고 다음 페이지를 작성해주세요.");
                                    }
                                });
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("서버와의 연결이 끊어졌습니다.");
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "서버에 연결할 수 없습니다.");
        }
    }

    private class DrawingPanel extends JPanel {
        private int pageIdx;
        public DrawingPanel(int pageIdx) {
            this.pageIdx = pageIdx;
            setBackground(Color.WHITE);

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
                g.drawImage(sketchBook.get(pageIdx), 0, 0, this);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientEx::new);
    }
}