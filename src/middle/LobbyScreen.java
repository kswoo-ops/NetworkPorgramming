package middle;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class LobbyScreen extends JFrame {
    private static final Color READY_COLOR = new Color(190, 240, 210);
    private static final Color WAIT_COLOR = new Color(235, 230, 250);
    private static final Color HOST_COLOR = new Color(255, 235, 170);   // 방장 줄 색

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String myName;
    private boolean isReady = false;

    // 방장 기능: 서버가 보낸 HOST:이름 으로 갱신한다. (HOST 메시지가 없는 서버면 null = 방장 없음)
    private String hostName = null;
    // 마지막으로 받은 접속자 목록 (HOST 메시지가 나중에 와도 다시 그리기 위해 보관, 화면 스레드에서만 사용)
    private List<String> lastNames = new ArrayList<>();
    private List<Boolean> lastReady = new ArrayList<>();

    private volatile boolean running = true;
    // 초기 코드의 UI 요소들 복구
    private JPanel userListPanel = new JPanel();
    private JLabel readyCountLabel = new JLabel("준비 0/0");
    private JTextArea chatArea = new JTextArea();
    private JTextField chatField = new JTextField();
    private JLabel statusLabel = new JLabel("모두 준비하면 게임을 시작할 수 있어요!");
    private JButton readyButton = new JButton("준비완료");

    public LobbyScreen(Socket socket, ObjectOutputStream out, ObjectInputStream in, String myName) {
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.myName = myName;

        setTitle("갈틱폰 - 대기실 (" + myName + ")");
        setSize(820, 480);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // 좌측(참여자 목록)과 우측(채팅 및 컨트롤) 패널 부착
        add(buildLeftPanel(), BorderLayout.WEST);
        add(buildRightPanel(), BorderLayout.CENTER);

        // 서버 메시지 수신 스레드 시작
        startReceiveThread();
    }

    // ----- 화면 구성 (초기 코드 구조 완벽 복원) -----
    private JPanel buildLeftPanel() {
        JPanel left = new JPanel(new BorderLayout(0, 5));
        JLabel title = new JLabel("플레이어", JLabel.CENTER);   
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        userListPanel.setLayout(new BoxLayout(userListPanel, BoxLayout.Y_AXIS));
        JScrollPane sp = new JScrollPane(userListPanel);
        sp.setPreferredSize(new Dimension(240, 0));
        left.add(title, BorderLayout.NORTH);
        left.add(sp, BorderLayout.CENTER);
        left.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 0));
        return left;
    }

    private JPanel buildRightPanel() {
        JPanel right = new JPanel(new BorderLayout(5, 5));

        // 위: 제목 + 준비 n/N
        JPanel top = new JPanel(new BorderLayout());
        JLabel title = new JLabel("대기실 채팅");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        readyCountLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        top.add(title, BorderLayout.WEST);
        top.add(readyCountLabel, BorderLayout.EAST);

        // 가운데: 채팅 영역
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 14));

        // 채팅 입력
        JPanel chatInput = new JPanel(new BorderLayout(5, 0));
        JButton sendButton = new JButton("전송");
        chatInput.add(chatField, BorderLayout.CENTER);
        chatInput.add(sendButton, BorderLayout.EAST);
        sendButton.addActionListener(e -> sendChat());
        chatField.addActionListener(e -> sendChat());

        // 아래: 안내 문구 + 준비 버튼
        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        readyButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        readyButton.setPreferredSize(new Dimension(160, 44));
        readyButton.addActionListener(e -> onMainButton());
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(readyButton, BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout(0, 5));
        center.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        center.add(chatInput, BorderLayout.SOUTH);

        right.add(top, BorderLayout.NORTH);
        right.add(center, BorderLayout.CENTER);
        right.add(bottom, BorderLayout.SOUTH);
        right.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 10));
        return right;
    }

    // ----- 버튼 동작 -----
    // 방장이면 게임 시작 요청, 아니면 준비/준비 취소
    private void onMainButton() {
        if (isHost()) {
            send("START_GAME");
        } else {
            toggleReady();
        }
    }

    private boolean isHost() {
        return myName.equals(hostName);
    }

    private void toggleReady() {
        // 준비 중이면 취소, 아니면 준비 요청 전송
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
            statusLabel.setText("서버로 전송하지 못했습니다.");
        }
    }

    // ----- 서버 메시지 수신 (별도 스레드) -----
    private void startReceiveThread() {
        new Thread(() -> {
            try {
                Object obj;
                while (running && (obj = in.readObject()) != null) {
                    handleMessage(obj);
                }
            } catch (Exception e) {
                if (!running) return; // 정상 화면 전환 시에는 끊김 팝업 띄우지 않음
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "서버와의 연결이 끊어졌습니다.");
                    dispose();
                    new ConnectScreen().setVisible(true);
                });
            }
        }).start();
    }

    // 메시지 종류에 따라 화면 반영
    private void handleMessage(Object obj) {
        if (obj instanceof String) {
            String line = (String) obj;
            if (line.startsWith("USERS:")) {
                updateUsers(line.substring(6));
            } else if (line.startsWith("HOST:")) {
                String host = line.substring(5);
                SwingUtilities.invokeLater(() -> {
                    hostName = host;
                    refreshLobby();      // 방장이 바뀌면 목록과 버튼을 다시 그린다
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
                // 게임 시작 시 대기실 창을 닫고 ClientEx 게임 메인 화면으로 매끄럽게 전환
                SwingUtilities.invokeLater(() -> {
                    dispose(); 
                    ClientEx gameScreen = new ClientEx(socket, out, in, myName);
                    gameScreen.handleGameStart(msg);
                    gameScreen.setVisible(true);
                    running=false;
                });
            }
        }
    }

    // USERS 문자열("준모=1,동석=0")을 읽어 보관한 뒤 화면을 다시 그린다
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

    // 보관한 목록과 방장 정보로 왼쪽 목록, "준비 n/N", 버튼, 안내 문구를 다시 그린다 (화면 스레드에서만 호출)
    private void refreshLobby() {
        userListPanel.removeAll();
        int guestCount = 0;   // 방장을 뺀 인원
        int readyCount = 0;   // 그중 준비한 인원
        boolean me = false;
        for (int i = 0; i < lastNames.size(); i++) {
            String name = lastNames.get(i);
            boolean r = lastReady.get(i);
            boolean host = name.equals(hostName);
            if (!host) {
                guestCount++;
                if (r) readyCount++;
            }
            if (name.equals(myName)) { me = true; isReady = r; }
            userListPanel.add(makeUserRow(name, r, host));
            userListPanel.add(Box.createVerticalStrut(6));
        }
        if (me) {
            if (isHost()) {
                readyButton.setText("게임 시작");
                statusLabel.setText("다른 플레이어가 모두 준비하면 게임을 시작할 수 있어요!");
            } else {
                readyButton.setText(isReady ? "준비 취소" : "준비완료");
                statusLabel.setText(isReady ? "준비완료! 방장이 시작하기를 기다리는 중이에요."
                                            : "준비를 누르면 방장이 게임을 시작할 수 있어요!");
            }
        }
        readyCountLabel.setText("준비 " + readyCount + "/" + guestCount);
        userListPanel.revalidate();
        userListPanel.repaint();
    }

    private JPanel makeUserRow(String name, boolean ready, boolean host) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(host ? HOST_COLOR : (ready ? READY_COLOR : WAIT_COLOR));
        row.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        JLabel nameLabel = new JLabel(name + (name.equals(myName) ? "  (나)" : ""));
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        JLabel stateLabel = new JLabel(host ? "방장" : (ready ? "준비완료" : "대기 중"));
        row.add(nameLabel, BorderLayout.CENTER);
        row.add(stateLabel, BorderLayout.EAST);
        return row;
    }
}