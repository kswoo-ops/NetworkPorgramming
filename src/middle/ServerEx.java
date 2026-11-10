package middle;

import java.io.*;
import java.net.*;
import java.util.*;

public class ServerEx {
    private static final int PORT = 5000;
    private static List<ClientHandler> clients = new ArrayList<>();
    private static final String[] WORDS = {"감자", "김치", "아이스크림"};

    public static void main(String[] args) {
        System.out.println("텔레스트레이션 릴레이 서버 시작...");
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

    public static class ClientHandler extends Thread {
        private Socket socket;
        private ObjectInputStream reader;
        private ObjectOutputStream writer;
        
        private String clientId = UUID.randomUUID().toString(); 
        private List<byte[]> currentBookPages = new ArrayList<>();
        private String currentBookOwnerId; 
        private boolean isReady = false;
        private boolean readyToSwap = false;

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
                        if ("PLAYER_READY".equals(msgStr)) {
                            this.isReady = true;
                            synchronized (clients) {
                                boolean allReady = true;
                                for (ClientHandler c : clients) {
                                    if (!c.isReady) { allReady = false; break; }
                                }

                                if (allReady && clients.size() >= 2) {
                                    Random rand = new Random();
                                    int totalPlayers = clients.size();
                                    
                                    for (int i = 0; i < totalPlayers; i++) {
                                        ClientHandler c = clients.get(i);
                                        String assignedWord = WORDS[rand.nextInt(WORDS.length)];
                                        
                                        c.currentBookOwnerId = c.clientId;
                                        c.currentBookPages = new ArrayList<>();
                                        
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
                                } else {
                                    writer.writeObject("NOTIFY:다른 참가자들의 준비를 기다리는 중...");
                                    writer.flush();
                                    writer.reset();
                                }
                            }
                        }
                    } else if (obj instanceof SketchMessage) {
                        SketchMessage msg = (SketchMessage) obj;
                        
                        if ("PASS_PAGE".equals(msg.getType())) {
                            this.currentBookPages = msg.getAllPagesData();
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
                                        String ownerId;
                                        BookUnit(List<byte[]> p, String o) { pages = p; ownerId = o; }
                                    }

                                    BookUnit[] incoming = new BookUnit[size];
                                    for (int i = 0; i < size; i++) {
                                        ClientHandler c = clients.get(i);
                                        incoming[i] = new BookUnit(c.currentBookPages, c.currentBookOwnerId);
                                    }

                                    BookUnit[] rotated = new BookUnit[size];
                                    for (int i = 0; i < size; i++) {
                                        int nextIndex = (i + 1) % size;
                                        rotated[nextIndex] = incoming[i];
                                    }

                                    for (int i = 0; i < size; i++) {
                                        ClientHandler c = clients.get(i);
                                        BookUnit u = rotated[i];
                                        c.currentBookPages = u.pages;
                                        c.currentBookOwnerId = u.ownerId;

                                        boolean isCompleted = u.ownerId.equals(c.clientId);
                                        int nextPageIndex = u.pages.size();

                                        SketchMessage outMsg = new SketchMessage("RECEIVE_BOOK", u.pages, nextPageIndex, u.ownerId);
                                        outMsg.setCompleted(isCompleted);

                                        c.writer.writeObject(outMsg);
                                        c.writer.flush();
                                        c.writer.reset();
                                        c.readyToSwap = false;
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
                System.out.println("클라이언트 연결 종료");
            } finally {
                synchronized (clients) {
                    clients.remove(this);
                }
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }
}