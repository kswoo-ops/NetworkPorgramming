package middle;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class LobbyScreen extends JFrame {
    // 칠판 + 오래된 출석부(종이) 테마
    private static final Color BOARD = new Color(35, 92, 66);
    private static final Color BOARD_DARK = new Color(24, 70, 51);
    private static final Color PAPER = new Color(246, 236, 205);
    private static final Color PAPER_LIGHT = new Color(255, 250, 233);
    private static final Color PAPER_EDGE = new Color(194, 167, 113);
    private static final Color INK = new Color(73, 58, 38);
    private static final Color CHALK = new Color(249, 246, 225);
    private static final Color READY_COLOR = new Color(210, 232, 199);
    private static final Color WAIT_COLOR = new Color(245, 226, 204);
    private static final Color HOST_COLOR = new Color(251, 224, 148);

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final String myName;
    private boolean isReady = false;
    private String hostName = null;
    private List<String> lastNames = new ArrayList<>();
    private List<Boolean> lastReady = new ArrayList<>();
    private volatile boolean running = true;

    private final JPanel userListPanel = new JPanel();
    private final JLabel readyCountLabel = new JLabel("출석 0/0");
    private final JTextArea chatArea = new JTextArea();
    private final JTextField chatField = new JTextField();
    private final JLabel statusLabel = new JLabel("모두 출석하면 수업을 시작할 수 있어요!");
    private final JButton readyButton = new JButton("출석하기");

    public LobbyScreen(Socket socket, ObjectOutputStream out, ObjectInputStream in, String myName) {
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.myName = myName;

        setTitle("마법소녀 - 교실 대기실 (" + myName + ")");
        setSize(900, 530);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel background = new JPanel(new BorderLayout(16, 12));
        background.setBackground(BOARD);
        background.setBorder(BorderFactory.createEmptyBorder(18, 20, 20, 20));
        setContentPane(background);

        JLabel heading = new JLabel("마법소녀 교실  ·  수업 준비 시간", SwingConstants.CENTER);
        heading.setFont(new Font("맑은 고딕", Font.BOLD, 23));
        heading.setForeground(CHALK);
        background.add(heading, BorderLayout.NORTH);
        background.add(buildLeftPanel(), BorderLayout.WEST);
        background.add(buildRightPanel(), BorderLayout.CENTER);

        startReceiveThread();
    }

    private static JPanel paperPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(PAPER);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PAPER_EDGE, 2),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        return panel;
    }

    private static void styleButton(JButton button) {
        button.setFont(new Font("맑은 고딕", Font.BOLD, 14));
        button.setBackground(new Color(236, 205, 135));
        button.setForeground(INK);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PAPER_EDGE),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
    }

    private JPanel buildLeftPanel() {
        JPanel left = paperPanel(new BorderLayout(0, 12));
        left.setPreferredSize(new Dimension(280, 0));

        JLabel title = new JLabel("출석부", SwingConstants.CENTER);
        title.setFont(new Font("맑은 고딕", Font.BOLD, 21));
        title.setForeground(INK);

        userListPanel.setLayout(new BoxLayout(userListPanel, BoxLayout.Y_AXIS));
        userListPanel.setBackground(PAPER_LIGHT);
        userListPanel.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));

        JScrollPane scroll = new JScrollPane(userListPanel);
        scroll.getViewport().setBackground(PAPER_LIGHT);
        scroll.setBorder(BorderFactory.createLineBorder(PAPER_EDGE));

        left.add(title, BorderLayout.NORTH);
        left.add(scroll, BorderLayout.CENTER);
        return left;
    }

    private JPanel buildRightPanel() {
        JPanel right = paperPanel(new BorderLayout(0, 12));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel title = new JLabel("교실 채팅");
        title.setFont(new Font("맑은 고딕", Font.BOLD, 20));
        title.setForeground(INK);
        readyCountLabel.setFont(new Font("맑은 고딕", Font.BOLD, 15));
        readyCountLabel.setForeground(BOARD_DARK);
        top.add(title, BorderLayout.WEST);
        top.add(readyCountLabel, BorderLayout.EAST);

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        chatArea.setBackground(PAPER_LIGHT);
        chatArea.setForeground(INK);
        chatArea.setMargin(new Insets(12, 12, 12, 12));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createLineBorder(PAPER_EDGE));

        JPanel chatInput = new JPanel(new BorderLayout(7, 0));
        chatInput.setOpaque(false);
        chatField.setFont(new Font("맑은 고딕", Font.PLAIN, 14));
        chatField.setBackground(PAPER_LIGHT);
        chatField.setForeground(INK);
        chatField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PAPER_EDGE),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        JButton sendButton = new JButton("전송");
        styleButton(sendButton);
        chatInput.add(chatField, BorderLayout.CENTER);
        chatInput.add(sendButton, BorderLayout.EAST);
        sendButton.addActionListener(e -> sendChat());
        chatField.addActionListener(e -> sendChat());

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(chatScroll, BorderLayout.CENTER);
        center.add(chatInput, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        statusLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
        statusLabel.setForeground(INK);
        readyButton.setPreferredSize(new Dimension(150, 44));
        styleButton(readyButton);
        readyButton.addActionListener(e -> onMainButton());
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(readyButton, BorderLayout.EAST);

        right.add(top, BorderLayout.NORTH);
        right.add(center, BorderLayout.CENTER);
        right.add(bottom, BorderLayout.SOUTH);
        return right;
    }

    private void onMainButton() {
        if (isHost()) send("START_GAME");
        else toggleReady();
    }

    private boolean isHost() {
        return myName.equals(hostName);
    }

    private void toggleReady() {
        // 서버 프로토콜은 변경하지 않음
        send(isReady ? "PLAYER_UNREADY" : "PLAYER_READY");
    }

    private void sendChat() {
        String text = chatField.getText().trim();
        if (text.isEmpty()) return;
        send("CHAT:" + text);
        chatField.setText("");
    }

    private void send(Object obj) {
        try {
            synchronized (out) {
                out.writeObject(obj);
                out.flush();
                out.reset();
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> statusLabel.setText("서버로 전송하지 못했습니다."));
        }
    }

    private void startReceiveThread() {
        new Thread(() -> {
            try {
                Object obj;
                while (running && (obj = in.readObject()) != null) {
                    handleMessage(obj);
                }
            } catch (Exception e) {
                if (!running) return;
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "서버와의 연결이 끊어졌습니다.");
                    dispose();
                    new ConnectScreen().setVisible(true);
                });
            }
        }, "lobby-receive").start();
    }

    private void handleMessage(Object obj) {
        if (obj instanceof String) {
            String line = (String) obj;
            if (line.startsWith("USERS:")) {
                updateUsers(line.substring(6));
            } else if (line.startsWith("HOST:")) {
                String host = line.substring(5);
                SwingUtilities.invokeLater(() -> {
                    hostName = host;
                    refreshLobby();
                });
            } else if (line.startsWith("NOTIFY:")) {
                String msg = line.substring(7);
                SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
            } else if (line.startsWith("CHAT:")) {
                String body = line.substring(5);
                int idx = body.indexOf(':');
                String who = idx >= 0 ? body.substring(0, idx) : "?";
                String text = idx >= 0 ? body.substring(idx + 1) : body;
                SwingUtilities.invokeLater(() -> {
                    chatArea.append(who + ": " + text + "\n");
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                });
            }
        } else if (obj instanceof SketchMessage) {
            SketchMessage msg = (SketchMessage) obj;
            if ("GAME_START".equals(msg.getType())) {
                SwingUtilities.invokeLater(() -> {
                    dispose();
                    ClientEx gameScreen = new ClientEx(socket, out, in, myName);
                    gameScreen.handleGameStart(msg);
                    gameScreen.setVisible(true);
                    running = false;
                });
            }
        }
    }

    private void updateUsers(String body) {
        List<String> names = new ArrayList<>();
        List<Boolean> ready = new ArrayList<>();
        if (!body.isEmpty()) {
            for (String item : body.split(",")) {
                int eq = item.lastIndexOf('=');
                if (eq < 0) continue;
                names.add(item.substring(0, eq));
                ready.add("1".equals(item.substring(eq + 1)));
            }
        }
        SwingUtilities.invokeLater(() -> {
            lastNames = names;
            lastReady = ready;
            refreshLobby();
        });
    }

    private void refreshLobby() {
        userListPanel.removeAll();
        int guestCount = 0;
        int readyCount = 0;
        boolean me = false;
        for (int i = 0; i < lastNames.size(); i++) {
            String name = lastNames.get(i);
            boolean ready = lastReady.get(i);
            boolean host = name.equals(hostName);
            if (!host) {
                guestCount++;
                if (ready) readyCount++;
            }
            if (name.equals(myName)) {
                me = true;
                isReady = ready;
            }
            userListPanel.add(makeUserRow(name, ready, host));
            userListPanel.add(Box.createVerticalStrut(7));
        }
        if (me) {
            if (isHost()) {
                readyButton.setText("수업 시작");
                statusLabel.setText("다른 학생이 모두 출석하면 수업을 시작할 수 있어요!");
            } else {
                readyButton.setText(isReady ? "출석 취소" : "출석하기");
                statusLabel.setText(isReady ? "출석 완료! 방장이 시작하기를 기다리는 중이에요."
                                            : "출석 버튼을 누르면 수업을 시작할 수 있어요!");
            }
        }
        // 기존과 동일하게 방장 제외 인원을 집계
        readyCountLabel.setText("출석 " + readyCount + "/" + guestCount);
        userListPanel.revalidate();
        userListPanel.repaint();
    }

    private JPanel makeUserRow(String name, boolean ready, boolean host) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(host ? HOST_COLOR : ready ? READY_COLOR : WAIT_COLOR);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PAPER_EDGE),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(name + (name.equals(myName) ? " (나)" : ""));
        nameLabel.setForeground(INK);
        nameLabel.setFont(new Font("맑은 고딕", Font.BOLD, 13));
        JLabel stateLabel = new JLabel(host ? "방장" : ready ? "출석" : "결석");
        stateLabel.setForeground(host ? new Color(116, 75, 24) : ready ? new Color(36, 110, 59) : new Color(162, 72, 57));
        stateLabel.setFont(new Font("맑은 고딕", Font.BOLD, 12));

        row.add(nameLabel, BorderLayout.CENTER);
        row.add(stateLabel, BorderLayout.EAST);
        return row;
    }
}
