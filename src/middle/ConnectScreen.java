//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package middle;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class ConnectScreen extends JFrame {
    private static final String DEFAULT_IP = "localhost";
    private static final String DEFAULT_PORT = "5000";
    private JTextField nickField = new JTextField(16);
    private JTextField ipField = new JTextField("localhost", 16);
    private JTextField portField = new JTextField("5000", 16);
    private JButton startButton = new JButton("시작");
    private JLabel messageLabel = new JLabel(" ", 0);

    public ConnectScreen() {
        this.setTitle("갈틱폰 - 접속");
        this.setSize(420, 360);
        this.setLocationRelativeTo((Component)null);
        this.setDefaultCloseOperation(3);
        this.setLayout(new BorderLayout(10, 10));
        JLabel var1 = new JLabel("닉네임과 접속 정보 입력", 0);
        var1.setFont(new Font("SansSerif", 1, 20));
        var1.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));
        this.add(var1, "North");
        JPanel var2 = new JPanel(new GridLayout(3, 1, 0, 8));
        var2.add(this.makeRow("닉네임", this.nickField));
        var2.add(this.makeRow("IP주소", this.ipField));
        var2.add(this.makeRow("포트번호", this.portField));
        var2.setBorder(BorderFactory.createEmptyBorder(10, 40, 0, 40));
        this.add(var2, "Center");
        JPanel var3 = new JPanel(new GridLayout(2, 1, 0, 8));
        this.startButton.setFont(new Font("SansSerif", 1, 16));
        this.startButton.addActionListener((var1x) -> this.tryConnect());
        this.messageLabel.setForeground(Color.RED);
        var3.add(this.startButton);
        var3.add(this.messageLabel);
        var3.setBorder(BorderFactory.createEmptyBorder(0, 100, 20, 100));
        this.add(var3, "South");
        this.nickField.addActionListener((var1x) -> this.tryConnect());
    }

    private JPanel makeRow(String var1, JTextField var2) {
        JPanel var3 = new JPanel(new BorderLayout(0, 2));
        JLabel var4 = new JLabel(var1);
        var4.setFont(new Font("SansSerif", 1, 13));
        var3.add(var4, "North");
        var3.add(var2, "Center");
        return var3;
    }

    private void tryConnect() {
        String var1 = this.nickField.getText().trim();
        String var2 = this.ipField.getText().trim();
        String var3 = this.portField.getText().trim();
        if (var1.isEmpty()) {
            this.messageLabel.setText("닉네임을 입력하세요.");
        } else if (!var1.contains(":") && !var1.contains(",")) {
            int var4;
            try {
                var4 = Integer.parseInt(var3);
            } catch (NumberFormatException var6) {
                this.messageLabel.setText("포트번호는 숫자로 입력하세요.");
                return;
            }

            this.startButton.setEnabled(false);
            this.messageLabel.setText("접속 중...");
            (new Thread(() -> this.connect(var1, var2, var4))).start();
        } else {
            this.messageLabel.setText("닉네임에는 ':' 와 ',' 를 쓸 수 없습니다.");
        }
    }

    private void connect(String var1, String var2, int var3) {
        Socket var4 = null;

        try {
            var4 = new Socket();
            var4.connect(new InetSocketAddress(var2, var3), 3000);
            ObjectOutputStream var5 = new ObjectOutputStream(var4.getOutputStream());
            var5.flush();
            ObjectInputStream var6 = new ObjectInputStream(var4.getInputStream());
            var5.writeObject("JOIN:" + var1);
            var5.flush();
            var5.reset();
            Object var7 = var6.readObject();
            if ("JOIN_OK".equals(var7)) {
                final Socket socket = var4;

                SwingUtilities.invokeLater(() -> {
                    new LobbyScreen(socket, var5, var6, var1).setVisible(true);
                });
            } else {
                String var9 = var7 instanceof String && ((String)var7).startsWith("JOIN_FAIL:") ? ((String)var7).substring(10) : "알 수 없는 응답입니다.";

                try {
                    var4.close();
                } catch (IOException var12) {
                }

                this.fail(var9);
            }
        } catch (Exception var13) {
            try {
                if (var4 != null) {
                    var4.close();
                }
            } catch (IOException var11) {
            }

            this.fail("서버에 연결할 수 없습니다. IP와 포트를 확인하세요.");
        }

    }

    private void fail(String var1) {
        SwingUtilities.invokeLater(() -> {
            this.messageLabel.setText(var1);
            this.startButton.setEnabled(true);
        });
    }

    public static void main(String[] var0) {
        SwingUtilities.invokeLater(() -> (new ConnectScreen()).setVisible(true));
    }
}
