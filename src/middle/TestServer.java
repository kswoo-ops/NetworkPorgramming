package middle;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * 테스트용 임시 서버 (A의 서버가 완성되기 전에 접속·대기실 화면을 확인하기 위한 것)
 * 합의표의 메시지를 그대로 구현: JOIN, PLAYER_READY, PLAYER_UNREADY, CHAT
 * 방장 기능: 가장 먼저 접속한 사람이 방장. HOST:이름 을 알리고, 방장의 START_GAME 요청이 오면 조건을 확인해 시작한다.
 * 실행: java middle.TestServer
 */
public class TestServer {
    private static final int PORT = 5000;
    private static final List<Handler> clients = new ArrayList<>();
    private static Handler host = null;   // 현재 방장 (clients 동기화 구간에서만 바꾼다)

    public static void main(String[] args) throws IOException {
        System.out.println("테스트 서버 시작 (port " + PORT + ")");
        try (ServerSocket ss = new ServerSocket(PORT)) {
            while (true) {
                Socket s = ss.accept();
                new Handler(s).start();
            }
        }
    }

    // 접속자 목록을 모든 클라이언트에게 전송 ("이름=0/1" 쉼표 구분)
    private static void broadcastUsers() {
        StringBuilder sb = new StringBuilder("USERS:");
        synchronized (clients) {
            for (int i = 0; i < clients.size(); i++) {
                Handler h = clients.get(i);
                if (h.name == null) continue;
                if (sb.length() > 6) sb.append(",");
                sb.append(h.name).append("=").append(h.ready ? 1 : 0);
            }
            for (Handler h : clients) {
                if (h.name == null) continue;
                if (host != null) h.send("HOST:" + host.name);
                h.send(sb.toString());
            }
        }
    }

    private static class Handler extends Thread {
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        String name = null;
        boolean ready = false;

        Handler(Socket s) { this.socket = s; }

        synchronized void send(Object obj) {
            try { out.writeObject(obj); out.flush(); out.reset(); } catch (IOException e) { }
        }

        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());
                synchronized (clients) { clients.add(this); }

                Object obj;
                while ((obj = in.readObject()) != null) {
                    if (!(obj instanceof String)) continue;
                    String msg = (String) obj;

                    if (msg.startsWith("JOIN:")) {
                        String nick = msg.substring(5).trim();
                        String error = null;
                        if (nick.isEmpty()) error = "닉네임이 비어 있습니다.";
                        else if (nick.contains(":") || nick.contains(",")) error = "사용할 수 없는 문자가 있습니다.";
                        else synchronized (clients) {
                            for (Handler h : clients) if (nick.equals(h.name)) error = "이미 사용 중인 닉네임입니다.";
                        }
                        if (error != null) { send("JOIN_FAIL:" + error); continue; }
                        name = nick;
                        synchronized (clients) {
                            if (host == null) host = this;   // 먼저 들어온 사람이 방장
                        }
                        send("JOIN_OK");
                        System.out.println("접속: " + name);
                        broadcastUsers();

                    } else if (name == null) {
                        continue;                                  // JOIN 전에는 다른 메시지 무시
                    } else if (msg.equals("PLAYER_READY")) {
                        if (this != host) { ready = true; broadcastUsers(); }   // 방장은 준비 대신 시작 버튼을 쓴다
                    } else if (msg.equals("PLAYER_UNREADY")) {
                        if (this != host) { ready = false; broadcastUsers(); }
                    } else if (msg.equals("START_GAME")) {
                        tryStart();
                    } else if (msg.startsWith("CHAT:")) {
                        String text = msg.substring(5);
                        synchronized (clients) {
                            for (Handler h : clients) if (h.name != null) h.send("CHAT:" + name + ":" + text);
                        }
                    }
                }
            } catch (Exception e) {
                // 연결 종료
            } finally {
                synchronized (clients) {
                    clients.remove(this);
                    if (host == this) {                     // 방장이 나가면 다음으로 먼저 접속한 사람에게 넘긴다
                        host = null;
                        for (Handler h : clients) {
                            if (h.name != null) { host = h; h.ready = false; break; }
                        }
                        if (host != null) System.out.println("방장 변경: " + host.name);
                    }
                }
                try { socket.close(); } catch (IOException e) { }
                if (name != null) { System.out.println("퇴장: " + name); broadcastUsers(); }
            }
        }

        // 방장이 시작을 요청했을 때: 방장인지, 2명 이상인지, 방장을 뺀 전원이 준비했는지 확인하고 시작
        private void tryStart() {
            synchronized (clients) {
                if (this != host) { send("NOTIFY:방장만 게임을 시작할 수 있습니다."); return; }
                int n = 0;
                for (Handler h : clients) if (h.name != null) n++;
                if (n < 2) { send("NOTIFY:게임을 시작하려면 최소 2명 이상 필요합니다."); return; }
                for (Handler h : clients) {
                    if (h.name != null && h != host && !h.ready) {
                        send("NOTIFY:아직 준비하지 않은 참가자가 있습니다.");
                        return;
                    }
                }
                System.out.println("게임 시작: " + n + "명");
                for (Handler h : clients) {
                    if (h.name == null) continue;
                    SketchMessage start = new SketchMessage("GAME_START", new ArrayList<byte[]>(), 0, h.name);
                    start.setSecretWord("테스트");   // 테스트 서버용 임시 제시어
                    h.send(start);
                }
            }
        }
    }
}
