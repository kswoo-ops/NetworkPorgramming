package middle;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServerEx {
    private static final int PORT = 5000;
    private static final List<ClientHandler> clients = new ArrayList<>();
    private static final String[] WORDS = {"감자", "김치", "아이스크림", "바나나", "자전거", "컴퓨터", "축구공", "피아노"};

    public static void main(String[] args) {
        System.out.println("텔레스트레이션 릴레이 서버 시작... (포트: " + PORT + ")");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                synchronized (clients) {
                    clients.add(handler);
                }
                handler.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendToClient(ClientHandler c, Object obj) {
        if (c == null || c.writer == null || obj == null) {
            return;
        }
        synchronized (c.writer) {
            try {
                c.writer.writeObject(obj);
                c.writer.flush();
                c.writer.reset();
            } catch (IOException e) {
                System.out.println("[전송 실패] " + c.nickName + " : " + e.getMessage());
                c.closeQuietly();
            }
        }
    }

    // 모든 접속자에게 현재 플레이어 목록과 준비 상태를 브로드캐스트하는 메서드
    public static void broadcastUserList() {
        List<ClientHandler> snapshot;
        String userListMsg;

        synchronized (clients) {
            StringBuilder sb = new StringBuilder("USERS:");
            for (int i = 0; i < clients.size(); i++) {
                ClientHandler c = clients.get(i);
                sb.append(c.nickName).append("=").append(c.isReady ? "1" : "0");
                if (i < clients.size() - 1) {
                    sb.append(",");
                }
            }
            userListMsg = sb.toString();
            snapshot = new ArrayList<>(clients);
        }

        for (ClientHandler c : snapshot) {
            sendToClient(c, userListMsg);
        }
    }

    public static class ClientHandler extends Thread {
        private Socket socket;
        private ObjectInputStream reader;
        private ObjectOutputStream writer;

        private String clientId = UUID.randomUUID().toString();
        private String nickName = "손님";
        private List<byte[]> currentBookPages = new ArrayList<>();
        private List<String> currentTextContents = new ArrayList<>();
        private String currentBookOwnerId;
        private boolean isReady = false;
        private boolean readyToSwap = false;
        public boolean isSlideReady = false;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            this.currentBookOwnerId = this.clientId;
        }

        private void closeQuietly() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
        }

        @Override
        public void run() {
            try {
                writer = new ObjectOutputStream(socket.getOutputStream());
                writer.flush();
                writer.reset();
                reader = new ObjectInputStream(socket.getInputStream());

                Object obj;
                while ((obj = reader.readObject()) != null) {
                    if (obj instanceof String) {
                        String msgStr = (String) obj;

                        // 1. 접속 시 "JOIN:닉네임" 처리
                        if (msgStr.startsWith("JOIN:")) {
                            String parsedNick = msgStr.substring(5).trim();
                            this.nickName = parsedNick.isEmpty() ? "손님" : parsedNick;
                            sendToClient(this, "JOIN_OK");
                            System.out.println("[접속 성공] " + nickName);
                            ServerEx.broadcastUserList();
                        }
                        // 2. 대기실 채팅 처리
                        else if (msgStr.startsWith("CHAT:")) {
                            String chatContent = msgStr.substring(5);
                            String broadcastChat = "CHAT:" + nickName + ":" + chatContent;
                            List<ClientHandler> snapshot;
                            synchronized (clients) {
                                snapshot = new ArrayList<>(clients);
                            }
                            for (ClientHandler c : snapshot) {
                                sendToClient(c, broadcastChat);
                            }
                        }
                        // 3. 게임 준비 완료 처리
                        else if ("PLAYER_READY".equals(msgStr)) {
                            this.isReady = true;
                            ServerEx.broadcastUserList();

                            List<ClientHandler> snapshot;
                            boolean allReady = true;
                            synchronized (clients) {
                                snapshot = new ArrayList<>(clients);
                                for (ClientHandler c : snapshot) {
                                    if (!c.isReady) {
                                        allReady = false;
                                        break;
                                    }
                                }
                            }

                            if (allReady && snapshot.size() >= 2) {
                                Random rand = new Random();
                                int totalPlayers = snapshot.size();
                                System.out.println("[게임 시작] 총 " + totalPlayers + "명 참여");

                                for (int i = 0; i < totalPlayers; i++) {
                                    ClientHandler c = snapshot.get(i);
                                    String assignedWord = WORDS[rand.nextInt(WORDS.length)];

                                    c.currentBookOwnerId = c.clientId;
                                    c.currentBookPages = new ArrayList<>();
                                    c.currentTextContents = new ArrayList<>();
                                    c.readyToSwap = false;

                                    SketchMessage startMsg = new SketchMessage(
                                            "GAME_START", c.currentBookPages, 0, c.currentBookOwnerId);
                                    startMsg.setSecretWord(assignedWord);
                                    sendToClient(c, startMsg);
                                }
                            } else if (allReady && snapshot.size() < 2) {
                                sendToClient(this, "NOTIFY:게임을 시작하려면 최소 2명 이상 필요합니다.");
                                this.isReady = false;
                                ServerEx.broadcastUserList();
                            } else {
                                sendToClient(this, "NOTIFY:다른 참가자들의 준비를 기다리는 중...");
                            }
                        }
                        // 4. 게임 준비 취소 처리
                        else if ("PLAYER_UNREADY".equals(msgStr)) {
                            this.isReady = false;
                            ServerEx.broadcastUserList();
                        }

                    } else if (obj instanceof SketchMessage) {
                        SketchMessage msg = (SketchMessage) obj;

                        if ("PASS_PAGE".equals(msg.getType())) {
                            this.currentBookPages = (msg.getAllPagesData() != null)
                                    ? new ArrayList<>(msg.getAllPagesData())
                                    : new ArrayList<>();
                            this.currentTextContents = (msg.getTextContents() != null)
                                    ? new ArrayList<>(msg.getTextContents())
                                    : new ArrayList<>();
                            if (msg.getOwnerId() != null) {
                                this.currentBookOwnerId = msg.getOwnerId();
                            }
                            this.readyToSwap = true;

                            List<ClientHandler> snapshot;
                            boolean allReady = true;
                            synchronized (clients) {
                                snapshot = new ArrayList<>(clients);
                                for (ClientHandler c : snapshot) {
                                    if (!c.readyToSwap) {
                                        allReady = false;
                                        break;
                                    }
                                }
                            }

                            if (allReady && !snapshot.isEmpty()) {
                                int size = snapshot.size();

                                class BookUnit {
                                    final List<byte[]> pages;
                                    final List<String> texts;
                                    final String ownerId;

                                    BookUnit(List<byte[]> p, List<String> t, String o) {
                                        pages = p;
                                        texts = t;
                                        ownerId = o;
                                    }
                                }

                                BookUnit[] incoming = new BookUnit[size];
                                for (int i = 0; i < size; i++) {
                                    ClientHandler c = snapshot.get(i);
                                    incoming[i] = new BookUnit(
                                            c.currentBookPages, c.currentTextContents, c.currentBookOwnerId);
                                }

                                BookUnit[] rotated = new BookUnit[size];
                                for (int i = 0; i < size; i++) {
                                    int nextIndex = (i + 1) % size;
                                    rotated[nextIndex] = incoming[i];
                                }

                                boolean isAllGameCompleted = true;

                                for (int i = 0; i < size; i++) {
                                    ClientHandler c = snapshot.get(i);
                                    BookUnit u = rotated[i];
                                    c.currentBookPages = u.pages;
                                    c.currentTextContents = u.texts;
                                    c.currentBookOwnerId = u.ownerId;

                                    boolean isCompleted = u.ownerId != null && u.ownerId.equals(c.clientId);
                                    if (!isCompleted) {
                                        isAllGameCompleted = false;
                                    }

                                    int nextPageIndex = u.pages.size();

                                    SketchMessage outMsg = new SketchMessage(
                                            "RECEIVE_BOOK", u.pages, nextPageIndex, u.ownerId);
                                    outMsg.setCompleted(isCompleted);
                                    outMsg.setTextContents(u.texts);

                                    c.readyToSwap = false;
                                    sendToClient(c, outMsg);
                                }

                                if (isAllGameCompleted) {
                                    new java.util.Timer().schedule(new java.util.TimerTask() {
                                        private int currentBookIdx = 0;
                                        private int currentPageIdx = 0;

                                        @Override
                                        public void run() {
                                            List<ClientHandler> slideSnapshot;
                                            synchronized (clients) {
                                                slideSnapshot = new ArrayList<>(clients);
                                            }
                                            if (slideSnapshot.isEmpty()) {
                                                this.cancel();
                                                return;
                                            }

                                            if (currentBookIdx >= slideSnapshot.size()) {
                                                SketchMessage endMsg = new SketchMessage("SLIDE_END", null);
                                                for (ClientHandler c : slideSnapshot) {
                                                    sendToClient(c, endMsg);
                                                }
                                                this.cancel();
                                                return;
                                            }

                                            ClientHandler targetClient = slideSnapshot.get(currentBookIdx);
                                            List<byte[]> pages = targetClient.currentBookPages;

                                            if (pages != null && currentPageIdx < pages.size()) {
                                                byte[] pageImgBytes = pages.get(currentPageIdx);
                                                if (pageImgBytes != null && pageImgBytes.length > 0) {
                                                    SketchMessage slideMsg = new SketchMessage("SHOW_SLIDE", pageImgBytes);
                                                    slideMsg.setOwnerId(targetClient.clientId);
                                                    slideMsg.setSlideInfo(currentBookIdx, currentPageIdx, pages.size());

                                                    for (ClientHandler c : slideSnapshot) {
                                                        sendToClient(c, slideMsg);
                                                    }
                                                }
                                                currentPageIdx++;
                                            } else {
                                                currentPageIdx = 0;
                                                currentBookIdx++;
                                            }
                                        }
                                    }, 5000, 1500);
                                }
                            } else {
                                sendToClient(this, "NOTIFY:모든 참가자가 페이지 작성을 마칠 때까지 대기 중입니다...");
                            }
                        }
                    }
                }
            } catch (EOFException e) {
                System.out.println("[연결 종료 - EOF] " + nickName);
            } catch (Exception e) {
                System.out.println("[연결 종료 - 오류] " + nickName);
                e.printStackTrace();
            } finally {
                synchronized (clients) {
                    clients.remove(this);
                }
                ServerEx.broadcastUserList();
                closeQuietly();
            }
        }
    }
}