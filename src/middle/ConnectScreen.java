package middle;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class ConnectScreen extends JFrame {
    private static final String DEFAULT_IP = "localhost";
    private static final String DEFAULT_PORT = "5000";

    private JTextField nickField = new JTextField(16);
    private JTextField ipField = new JTextField(DEFAULT_IP, 16);
    private JTextField portField = new JTextField(DEFAULT_PORT, 16);
    private JButton startButton = new JButton("시작");
    private JLabel messageLabel = new JLabel(" ", JLabel.CENTER);

    public ConnectScreen() {
        setTitle("갈틱폰 - 접속");
        setSize(420, 360);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JLabel title = new JLabel("닉네임과 접속 정보 입력", JLabel.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
        add(title, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(3, 1, 0, 8));
        form.add(makeRow("닉네임", nickField));
        form.add(makeRow("IP주소", ipField));
        form.add(makeRow("포트번호", portField));
        form.setBorder(BorderFactory.createEmptyBorder(10, 40, 0, 40));
        add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new GridLayout(2, 1, 0, 8));
        startButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        startButton.addActionListener(e -> tryConnect());
        messageLabel.setForeground(Color.RED);
        bottom.add(startButton);
        bottom.add(messageLabel);
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 100, 20, 100));
        add(bottom, BorderLayout.SOUTH);

        nickField.addActionListener(e -> tryConnect());
    }

    private JPanel makeRow(String label, JTextField field) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        JLabel l = new JLabel(label);
        l.setFont(new Font("SansSerif", Font.BOLD, 13));
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private void tryConnect() {
        String nick = nickField.getText().trim();
        String ip = ipField.getText().trim();
        String portText = portField.getText().trim();

        if (nick.isEmpty()) { messageLabel.setText("닉네임을 입력하세요."); return; }
        if (nick.contains(":") || nick.contains(",")) {
            messageLabel.setText("닉네임에는 ':' 와 ',' 를 쓸 수 없습니다.");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            messageLabel.setText("포트번호는 숫자로 입력하세요.");
            return;
        }

        startButton.setEnabled(false);
        messageLabel.setText("접속 중...");

        new Thread(() -> connect(nick, ip, port)).start();
    }

    private void connect(String nick, String ip, int port) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), 3000);   // 3초 타임아웃
            
            // 💡 주의: OutputStream을 먼저 생성하고 flush()를 한 뒤 InputStream을 생성해야 합니다!
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            out.writeObject("JOIN:" + nick);
            out.flush();
            out.reset();

            Object resp = in.readObject();       // 서버의 첫 응답 대기
            final Socket sock = socket;
            if ("JOIN_OK".equals(resp)) {
                SwingUtilities.invokeLater(() -> {
                    new LobbyScreen(sock, out, in, nick).setVisible(true);
                    dispose();
                });
            } else {
                String reason = (resp instanceof String && ((String) resp).startsWith("JOIN_FAIL:"))
                        ? ((String) resp).substring(10) : "알 수 없는 응답입니다.";
                try { socket.close(); } catch (IOException ignore) {}
                fail(reason);
            }
        } catch (Exception e) {
            try { if (socket != null) socket.close(); } catch (IOException ignore) {}
            fail("서버에 연결할 수 없습니다. IP와 포트를 확인하세요.");
        }
    }

    private void fail(String msg) {
        SwingUtilities.invokeLater(() -> {
            messageLabel.setText(msg);
            startButton.setEnabled(true);
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ConnectScreen().setVisible(true));
    }
}