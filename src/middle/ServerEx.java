package middle;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServerEx {
    private static final int PORT = 5000;
    private static List<ClientHandler> clients = new ArrayList<>();
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

    // 💡 모든 접속자에게 현재 플레이어 목록과 준비 상태를 브로드캐스트하는 메서드
    public static void broadcastUserList() {
        synchronized (clients) {
            StringBuilder sb = new StringBuilder("USERS:");
            for (int i = 0; i < clients.size(); i++) {
                ClientHandler c = clients.get(i);
                sb.append(c.nickName).append("=").append(c.isReady ? "1" : "0");
                if (i < clients.size() - 1) {
                    sb.append(",");
                }
            }
            
            String userListMsg = sb.toString();
            for (ClientHandler c : clients) {
                try {
                    c.writer.writeObject(userListMsg);
                    c.writer.flush();
                    c.writer.reset();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
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

        public void run() {
            try {
                writer = new ObjectOutputStream(socket.getOutputStream());
                writer.flush();
                reader = new ObjectInputStream(socket.getInputStream());

                Object obj;
                while ((obj = reader.readObject()) != null) {
                    if (obj instanceof String) {
                        String msgStr = (String) obj;

                        // 1. 접속 시 "JOIN:닉네임" 처리
                        if (msgStr.startsWith("JOIN:")) {
                            this.nickName = msgStr.substring(5);
                            writer.writeObject("JOIN_OK");
                            writer.flush();
                            writer.reset();
                            System.out.println("[접속 성공] " + nickName);
                            ServerEx.broadcastUserList(); // 접속자 목록 갱신 전송
                        }
                        // 2. 대기실 채팅 처리
                        else if (msgStr.startsWith("CHAT:")) {
                            String chatContent = msgStr.substring(5);
                            String broadcastChat = "CHAT:" + nickName + ":" + chatContent;
                            synchronized (clients) {
                                for (ClientHandler c : clients) {
                                    c.writer.writeObject(broadcastChat);
                                    c.writer.flush();
                                    c.writer.reset();
                                }
                            }
                        }
                        // 3. 게임 준비 완료 처리
                        else if ("PLAYER_READY".equals(msgStr)) {
                            this.isReady = true;
                            ServerEx.broadcastUserList();

                            synchronized (clients) {
                                boolean allReady = true;
                                for (ClientHandler c : clients) {
                                    if (!c.isReady) { allReady = false; break; }
                                }

                                if (allReady && clients.size() >= 2) {
                                    Random rand = new Random();
                                    int totalPlayers = clients.size();
                                    
                                    System.out.println("[게임 시작] 총 " + totalPlayers + "명 참여");
                                    for (int i = 0; i < totalPlayers; i++) {
                                        ClientHandler c = clients.get(i);
                                        String assignedWord = WORDS[rand.nextInt(WORDS.length)];
                                        
                                        c.currentBookOwnerId = c.clientId;
                                        c.currentBookPages = new ArrayList<>();
                                        c.currentTextContents = new ArrayList<>();
                                        
                                        SketchMessage startMsg = new SketchMessage("GAME_START", c.currentBookPages, 0, c.currentBookOwnerId);
                                        startMsg.setSecretWord(assignedWord);
                                        
                                        c.writer.writeObject(startMsg);
                                        c.writer.flush();
                                        c.writer.reset();
                                    }
                                } else if (allReady && clients.size() < 2) {
                                    writer.writeObject("NOTIFY:게임을 시작하려면 최소 2명 이상 필요합니다.");
                                    writer.flush();
                                    writer.reset();
                                    this.isReady = false;
                                    ServerEx.broadcastUserList();
                                } else {
                                    writer.writeObject("NOTIFY:다른 참가자들의 준비를 기다리는 중...");
                                    writer.flush();
                                    writer.reset();
                                }
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
                            this.currentBookPages = msg.getAllPagesData();
                            this.currentTextContents = msg.getTextContents(); 
                            this.currentBookOwnerId = msg.getOwnerId();
                            this.readyToSwap = true;

                            synchronized (clients) {
                                boolean allReady = true;
                                for (ClientHandler c : clients) {
                                    if (!c.readyToSwap) { allReady = false; break; }
                                }

                                if (allReady) {
                                    int size = clients.size();
                                    
                                    class BookUnit {
                                        List<byte[]> pages;
                                        List<String> texts;
                                        String ownerId;
                                        BookUnit(List<byte[]> p, List<String> t, String o) { 
                                            pages = p; texts = t; ownerId = o; 
                                        }
                                    }

                                    BookUnit[] incoming = new BookUnit[size];
                                    for (int i = 0; i < size; i++) {
                                        ClientHandler c = clients.get(i);
                                        incoming[i] = new BookUnit(c.currentBookPages, c.currentTextContents, c.currentBookOwnerId);
                                    }

                                    BookUnit[] rotated = new BookUnit[size];
                                    for (int i = 0; i < size; i++) {
                                        int nextIndex = (i + 1) % size;
                                        rotated[nextIndex] = incoming[i];
                                    }

                                    boolean isAllGameCompleted = true; // 게임 전체 종료 여부 체크

                                    for (int i = 0; i < size; i++) {
                                        ClientHandler c = clients.get(i);
                                        BookUnit u = rotated[i];
                                        c.currentBookPages = u.pages;
                                        c.currentTextContents = u.texts;
                                        c.currentBookOwnerId = u.ownerId;

                                        boolean isCompleted = u.ownerId.equals(c.clientId);
                                        if (!isCompleted) {
                                            isAllGameCompleted = false; // 아직 제 주인에게 안 돌아왔다면 게임 진행 중
                                        }

                                        int nextPageIndex = u.pages.size();

                                        SketchMessage outMsg = new SketchMessage("RECEIVE_BOOK", u.pages, nextPageIndex, u.ownerId);
                                        outMsg.setCompleted(isCompleted);
                                        outMsg.setTextContents(u.texts); 

                                        c.writer.writeObject(outMsg);
                                        c.writer.reset();
                                        c.writer.flush();
                                       
                                        c.readyToSwap = false;
                                    }

                                    // 💡 모든 스케치북이 한 바퀴를 완전히 돌아 원래 주인에게 돌아갔을 때만 슬라이드쇼 시작!
                                    if (isAllGameCompleted) {
                                        new java.util.Timer().schedule(new java.util.TimerTask() {
                                            private int currentBookIdx = 0;
                                            private int currentPageIdx = 0;

                                            @Override
                                            public void run() {
                                                synchronized (clients) {
                                                    if (currentBookIdx >= clients.size()) {
                                                        SketchMessage endMsg = new SketchMessage("SLIDE_END", null);
                                                        for (ClientHandler c : clients) {
                                                            try {
                                                                c.writer.writeObject(endMsg);
                                                                c.writer.flush();
                                                                c.writer.reset();
                                                            } catch (IOException e) { e.printStackTrace(); }
                                                        }
                                                        this.cancel();
                                                        return;
                                                    }

                                                    ClientHandler targetClient = clients.get(currentBookIdx);
                                                    List<byte[]> pages = targetClient.currentBookPages;

                                                    if (currentPageIdx < pages.size()) {
                                                        byte[] pageImgBytes = pages.get(currentPageIdx);
                                                        
                                                        SketchMessage slideMsg = new SketchMessage("SHOW_SLIDE", pageImgBytes);
                                                        slideMsg.setOwnerId(targetClient.clientId);
                                                        slideMsg.setSlideInfo(currentBookIdx, currentPageIdx, pages.size());

                                                        for (ClientHandler c : clients) {
                                                            try {
                                                                c.writer.writeObject(slideMsg);
                                                                c.writer.flush();
                                                                c.writer.reset();
                                                            } catch (IOException e) { e.printStackTrace(); }
                                                        }
                                                        currentPageIdx++;
                                                    } else {
                                                        currentPageIdx = 0;
                                                        currentBookIdx++;
                                                    }
                                                }
                                            }
                                        }, 5000, 1500); 
                                    }

                                } else {
                                    writer.writeObject("NOTIFY:모든 참가자가 페이지 작성을 마칠 때까지 대기 중입니다...");
                                    writer.flush();
                                    writer.reset();
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[연결 종료] " + nickName);
            } finally {
                synchronized (clients) {
                    clients.remove(this);
                }
                ServerEx.broadcastUserList(); // 나간 사람 반영해서 대기실 목록 갱신
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }
}