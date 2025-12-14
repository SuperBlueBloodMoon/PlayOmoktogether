package omok_server;

import omok_shared.MoveRecord;
import omok_shared.OmokMsg;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;

public class OmokServer extends JFrame {
    private int port;
    private ServerSocket serverSocket = null;
    private Vector<ClientHandler> users;           // 접속한 모든 클라이언트
    private Vector<GameRoom> rooms = new Vector<>();  // 생성된 모든 게임 방
    private Map<String, UserStats> userStatsMap = new HashMap<>();  // 사용자별 전적

    private Thread acceptThread = null;
    private JTextArea textArea;
    private JButton connectButton;
    private JButton disconnectButton;
    private JButton exitButton;

    public OmokServer(int port) {
        super("Omok Server");

        setSize(400, 300);
        setLocationRelativeTo(null);

        buildGUI();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setVisible(true);

        this.port = port;
    }

    private void buildGUI() {
        add(createDisplayPanel(), BorderLayout.CENTER);
        add(createControlPanel(), BorderLayout.SOUTH);
    }

    // 서버 로그 출력 패널
    private JPanel createDisplayPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    // 서버 제어 버튼 패널
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 0));

        connectButton = new JButton("서버 시작");
        disconnectButton = new JButton("서버 종료");
        exitButton = new JButton("종료");

        // 서버 시작 버튼
        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                acceptThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        startServer();
                    }
                });
                acceptThread.start();

                connectButton.setEnabled(false);
                disconnectButton.setEnabled(true);
                exitButton.setEnabled(false);
            }
        });

        // 서버 종료 버튼
        disconnectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                disconnect();

                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
                exitButton.setEnabled(true);
            }
        });

        // 프로그램 종료 버튼
        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                try {
                    if (serverSocket != null) {
                        serverSocket.close();
                    }
                } catch (IOException e) {
                    System.err.println("닫기 오류> " + e.getMessage());
                }
                System.exit(-1);
            }
        });

        panel.add(connectButton);
        panel.add(disconnectButton);
        panel.add(exitButton);

        disconnectButton.setEnabled(false);

        return panel;
    }

    // 서버 시작 및 클라이언트 연결 대기
    private void startServer() {
        Socket clientSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            printDisplay("서버가 시작되었습니다.");
            users = new Vector<ClientHandler>();

            // 클라이언트 연결 대기
            while (acceptThread == Thread.currentThread()) {
                clientSocket = serverSocket.accept();
                printDisplay("클라이언트가 연결되었습니다: "
                        + clientSocket.getInetAddress().getHostAddress());

                // 각 클라이언트를 별도 스레드로 처리
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                users.add(clientHandler);
                clientHandler.start();
            }
        } catch(SocketException e) {
            printDisplay("서버 소켓 종료");
        } catch(IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                System.err.println("서버 닫기 오류> " + e.getMessage());
                System.exit(-1);
            }
        }
    }

    // 서버 종료
    private void disconnect() {
        try {
            acceptThread = null;
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("서버 소켓 닫기 오류> " + e.getMessage());
            System.exit(-1);
        }
    }

    // 서버 로그 출력
    private void printDisplay(String message) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(message + "\n");
            textArea.setCaretPosition(textArea.getDocument().getLength());
        });
    }

    // 게임 방 제거 (참가자가 모두 나간 경우)
    public void removeRoom(GameRoom room) {
        synchronized (rooms) {
            if (rooms.remove(room)) {
                printDisplay(room.getTitle() + " 방이 제거됨");
                broadcastRoomListToAll();
            }
        }
    }

    // 사용자 전적 정보를 저장하는 클래스
    class UserStats {
        private int wins;
        private int losses;

        public UserStats() {
            this.wins = 0;
            this.losses = 0;
        }

        public void addWin() { wins++; }
        public void addLoss() { losses++; }

        public String getStatsString() {
            return "(승:" + wins + " 패:" + losses + ")";
        }
    }

    // 사용자 전적 업데이트
    public synchronized void updateUserStats(String userId, boolean won) {
        if (!userStatsMap.containsKey(userId)) {
            userStatsMap.put(userId, new UserStats());
        }

        if (won) {
            userStatsMap.get(userId).addWin();
        } else {
            userStatsMap.get(userId).addLoss();
        }

        // 모든 클라이언트에게 업데이트된 전적 전송
        broadcastUserStats();
    }

    // 사용자의 전적 문자열 가져오기
    public String getUserStatsString(String userId) {
        if (userStatsMap.containsKey(userId)) {
            return " " + userStatsMap.get(userId).getStatsString();
        }
        return "";
    }

    // 모든 클라이언트에게 방 목록 전송
    public void broadcastRoomListToAll() {
        String allRooms = getAllRooms();
        OmokMsg roomListMsg = new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_ROOM_LIST, allRooms);
        synchronized (users) {
            for (ClientHandler user : users) {
                user.send(roomListMsg);
            }
        }
    }

    // 모든 클라이언트에게 전적 정보 전송
    private void broadcastUserStats() {
        StringBuilder statsBuilder = new StringBuilder();
        for (Map.Entry<String, UserStats> entry : userStatsMap.entrySet()) {
            if (statsBuilder.length() > 0) {
                statsBuilder.append(",");
            }
            statsBuilder.append(entry.getKey()).append(":")
                    .append(entry.getValue().getStatsString());
        }

        OmokMsg statsMsg = new OmokMsg("SERVER", OmokMsg.MODE_USER_STATS, statsBuilder.toString());
        for (ClientHandler user : users) {
            user.send(statsMsg);
        }
    }

    // 클라이언트 연결을 처리하는 스레드
    public class ClientHandler extends Thread {
        private Socket clientSocket;
        private String uid;
        private Player myPlayer;
        private ObjectOutputStream out;
        private GameRoom myRoom = null;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        // 클라이언트로부터 메시지 수신 및 처리
        private void receiveMessages(Socket cs) {
            try {
                out = new ObjectOutputStream(new BufferedOutputStream(cs.getOutputStream()));
                out.flush();
                ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(cs.getInputStream()));
                String message;
                OmokMsg msg;

                // 메시지 수신 루프
                while ((msg = (OmokMsg) in.readObject()) != null) {
                    if (msg.getMode() == OmokMsg.MODE_LOGIN) {
                        // 로그인 처리
                        uid = msg.getUserID();
                        printDisplay("새 참가자: " + uid);
                        printDisplay("현재 참가자수: " + users.size());

                        // 전적 초기화
                        if (!userStatsMap.containsKey(uid)) {
                            userStatsMap.put(uid, new UserStats());
                        }

                        // 현재 사용자 목록과 방 목록 전송
                        String allUserIds = OmokServer.this.getAllLUsers();
                        broadcastLobby(new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_USER_LIST, allUserIds));
                        String allRooms = OmokServer.this.getAllRooms();
                        broadcastLobby(new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_ROOM_LIST, allRooms));
                        broadcastUserStats();

                    } else if (msg.getMode() == OmokMsg.MODE_LOGOUT) {
                        // 로그아웃
                        break;

                    } else if (msg.getMode() == OmokMsg.MODE_LOBBY_STRING) {
                        // 로비 채팅
                        message = uid + ": " + msg.getMessage();
                        printDisplay(message);
                        broadcastLobby(msg);

                    } else if (msg.getMode() == OmokMsg.MODE_MAKE_ROOM) {
                        // 방 만들기
                        String roomTitle = msg.getMessage();
                        Player owner = new Player(this);
                        this.myPlayer = owner;

                        GameRoom newRoom = new GameRoom(roomTitle, owner, OmokServer.this);
                        synchronized (OmokServer.this.rooms) {
                            OmokServer.this.rooms.add(newRoom);
                        }
                        this.myRoom = newRoom;
                        printDisplay(roomTitle + " 생성 완료");

                        // 모든 클라이언트에게 방 목록 갱신
                        String allRooms = OmokServer.this.getAllRooms();
                        broadcastLobby(new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_ROOM_LIST, allRooms));
                        broadcastLobby(new OmokMsg("SERVER", OmokMsg.MODE_LOBBY_STRING, roomTitle + " 생성 완료"));

                        // 방 생성자에게 방 정보 전송
                        send(new OmokMsg("SERVER", OmokMsg.MODE_ROOM_ENTERED, newRoom.getTitle()));
                        String roomInfo = roomTitle + ", " + owner.getClientHandler().getUid();
                        send(new OmokMsg("SERVER", OmokMsg.MODE_ROOM_INFO, roomInfo));
                        newRoom.broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_GAME_USER_LIST, newRoom.getPlayersForClient()));

                    } else if (msg.getMode() == OmokMsg.MODE_JOIN_ROOM) {
                        // 방 입장
                        GameRoom gameRoom = findGameRoom(msg.getMessage());

                        if (gameRoom == null) {
                            send(new OmokMsg("SERVER", OmokMsg.MODE_LOBBY_STRING, "존재하지 않는 방입니다."));
                            continue;
                        }

                        this.myPlayer = new Player(this);
                        this.myRoom = gameRoom;
                        boolean joinSuccess = gameRoom.enterPlayer(myPlayer);

                        if (!joinSuccess) {
                            // 게임 진행 중이라 입장 실패
                            send(new OmokMsg("SERVER", OmokMsg.MODE_LOBBY_STRING, "게임이 진행 중이라 입장할 수 없습니다."));
                            this.myRoom = null;
                            this.myPlayer = null;
                        } else {
                            // 입장 성공
                            send(new OmokMsg("SERVER", OmokMsg.MODE_ROOM_ENTERED, msg.getMessage()));
                            String roomInfo = gameRoom.getTitle() + ", " + gameRoom.getOwner().getClientHandler().getUid();
                            send(new OmokMsg("SERVER", OmokMsg.MODE_ROOM_INFO, roomInfo));
                            gameRoom.broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_GAME_USER_LIST, gameRoom.getPlayersForClient()));
                        }

                    } else if (msg.getMode() == OmokMsg.MODE_EXIT_ROOM) {
                        // 방 나가기
                        this.myRoom.exitPlayer(myPlayer);
                        if (this.myRoom.getPlayerCount() > 0) {
                            String roomInfo = this.myRoom.getTitle() + ", " + this.myRoom.getOwner().getClientHandler().getUid();
                            this.myRoom.broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_ROOM_INFO, roomInfo));
                            this.myRoom.broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_GAME_USER_LIST, this.myRoom.getPlayersForClient()));
                        }
                        this.myRoom = null;
                        this.myPlayer = null;

                        String allRooms = OmokServer.this.getAllRooms();
                        broadcastLobby(new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_ROOM_LIST, allRooms));
                        send(new OmokMsg("SERVER", OmokMsg.MODE_EXIT_ROOM, "SUCCESS"));

                    } else if(msg.getMode() == OmokMsg.MODE_WAITING_STRING) {
                        // 대기실 채팅
                        message = uid + ": " + msg.getMessage();
                        printDisplay(message);
                        this.myRoom.broadcastGameRoom(msg);

                    } else if(msg.getMode() == OmokMsg.MODE_GAME_START) {
                        // 게임 시작
                        if (this.myRoom.getPlayerCount() > 1) {
                            if (this.myRoom.getOwner().equals(myPlayer)) {
                                this.myRoom.startGame();
                            } else {
                                send(new OmokMsg("SERVER", OmokMsg.MODE_START, "FAILED:방장만 게임을 시작할 수 있습니다."));
                            }
                        } else {
                            send(new OmokMsg("SERVER", OmokMsg.MODE_START, "FAILED:플레이어가 2명이 되어야 시작할 수 있습니다."));
                        }

                    } else if (msg.getMode() == OmokMsg.MODE_PLACE_STONE) {
                        // 돌 놓기
                        if (this.myRoom != null && this.myRoom.isGameStarted()) {
                            int x = msg.getX();
                            int y = msg.getY();
                            boolean success = this.myRoom.placeStone(uid, x, y);

                            if (!success) {
                                send(new OmokMsg("SERVER", OmokMsg.MODE_WAITING_STRING,
                                        "잘못된 위치이거나 당신의 차례가 아닙니다."));
                            }
                        }

                    } else if (msg.getMode() == OmokMsg.MODE_SUGGEST_MOVE) {
                        // 관전자 훈수
                        if (this.myRoom != null && this.myRoom.isGameStarted()) {
                            int x = msg.getX();
                            int y = msg.getY();
                            this.myRoom.handleSuggestion(uid, x, y);
                        }

                    } else if(msg.getMode() == OmokMsg.MODE_REQUEST_ADVICE) {
                        // 플레이어 훈수 요청
                        if (this.myRoom != null && this.myRoom.isGameStarted()) {
                            this.myRoom.requestAdvice(uid);
                        }

                    } else if (msg.getMode() == OmokMsg.MODE_OFFER_ADVICE) {
                        // 관전자 훈수 제공 의사 표시
                        if (this.myRoom != null && this.myRoom.isGameStarted()) {
                            this.myRoom.offerAdvice(uid);
                        }

                    } else if (msg.getMode() == OmokMsg.MODE_SELECT_ADVISOR) {
                        // 플레이어가 관전자 선택
                        if (this.myRoom != null && this.myRoom.isGameStarted()) {
                            String selectedAdvisorId = msg.getMessage();
                            this.myRoom.selectAdvisor(uid, selectedAdvisorId);
                        }

                    } else if (msg.getMode() == OmokMsg.MODE_GAME_CHAT) {
                        // 게임 중 채팅
                        if (this.myRoom != null && this.myRoom.isGameStarted()) {
                            String chatMessage = msg.getMessage();
                            OmokMsg chatBroadcast = new OmokMsg(uid, OmokMsg.MODE_GAME_CHAT, chatMessage);
                            this.myRoom.broadcastGameRoom(chatBroadcast);
                            printDisplay("[게임 채팅] " + uid + ": " + chatMessage);
                        }

                    } else if (msg.getMode() == OmokMsg.MODE_REPLAY_PREV) {
                        // 복기 - 이전 수
                        int currentIndex = Integer.parseInt(msg.getCurrentIndex()); // 현재의 Index 값을 받아오기
                        MoveRecord prevRecord;
                        prevRecord = this.myRoom.getGameRecord().getAllMoves().get(currentIndex); // Index 값에 해당하는 MoveRecord 객체 가져오기
                        // 해당 객체의 ID 값이 Owner와 같은지 비교 후 알맞은 색을 선택해서 클라이언트에 전송
                        if (Objects.equals(prevRecord.getPlayerId(), this.myRoom.getOwner().getClientHandler().getUid())) {
                            send(new OmokMsg("SERVER", OmokMsg.MODE_REPLAY_PREV, prevRecord.getX(), prevRecord.getY(), 1));
                        } else {
                            send(new OmokMsg("SERVER", OmokMsg.MODE_REPLAY_PREV, prevRecord.getX(), prevRecord.getY(), 2));
                        }
                        --currentIndex; // Index 값을 하나 낮추기
                        if (currentIndex != -1) {
                            prevRecord = this.myRoom.getGameRecord().getAllMoves().get(currentIndex); // Index 값에 해당하는 MoveRecord 객체 가져오기
                            // 반복문을 통해서 훈수에 대한 기록을 전부 클라이언트에 전송
                            while (prevRecord.isSpectator()) {
                                send(new OmokMsg("SERVER", OmokMsg.MODE_REPLAY_PREV, prevRecord.getX(), prevRecord.getY(), 3, prevRecord.getSpectatorColor()));
                                --currentIndex;
                                if (currentIndex == -1) {
                                    break;
                                }
                                prevRecord = this.myRoom.getGameRecord().getAllMoves().get(currentIndex);
                            }
                        }
                        // 현재까지 진행한 Index 값을 클라이언트에 전송
                        send(new OmokMsg("SERVER", OmokMsg.MODE_CURRENT_COUNT, String.valueOf(currentIndex)));

                    } else if (msg.getMode() == OmokMsg.MODE_REPLAY_NEXT) {
                        // 복기 - 다음 수
                        int currentIndex = Integer.parseInt(msg.getCurrentIndex()); // 현재의 Index 값을 받아오기
                        int endIndex = Integer.parseInt(msg.getEndIndex()); // 해당 게임에 대한 마지막 수의 Index 값을 받아오기
                        // 현재 수 Index가 마지막 수의 Index와 같지 않으면 진행
                        if (currentIndex != endIndex - 1) {
                            MoveRecord nextRecord;
                            ++currentIndex;
                            nextRecord = this.myRoom.getGameRecord().getAllMoves().get(currentIndex);
                            // 만약 현재의 수가 훈수이면 진행
                            while (nextRecord.isSpectator()) {
                                send(new OmokMsg("SERVER", OmokMsg.MODE_REPLAY_NEXT, nextRecord.getX(), nextRecord.getY(), 3, nextRecord.getSpectatorColor()));
                                ++currentIndex;
                                nextRecord = this.myRoom.getGameRecord().getAllMoves().get(currentIndex);
                            }
                            // 해당 객체의 ID 값이 Owner와 같은지 비교 후 알맞은 색을 선택해서 클라이언트에 전송
                            if (Objects.equals(nextRecord.getPlayerId(), this.myRoom.getOwner().getClientHandler().getUid())) {
                                send(new OmokMsg("SERVER", OmokMsg.MODE_REPLAY_NEXT, nextRecord.getX(), nextRecord.getY(), 1));
                            } else {
                                send(new OmokMsg("SERVER", OmokMsg.MODE_REPLAY_NEXT, nextRecord.getX(), nextRecord.getY(), 2));
                            }
                        }
                        // 현재까지 진행한 Index 값을 클라이언트에 전송
                        send(new OmokMsg("SERVER", OmokMsg.MODE_CURRENT_COUNT, String.valueOf(currentIndex)));

                    } else if(msg.getMode() == OmokMsg.MODE_SURRENDER) {
                        // 기권
                        if (this.myRoom != null && this.myRoom.isGameStarted()) {
                            this.myRoom.handleSurrender(uid);
                        }
                    }
                }

                // 정상 종료
                handleDisconnect();
                printDisplay(uid + " 퇴장. 현재 참가자 수: " + users.size());

            } catch (IOException e) {
                // 연결 끊김 처리
                handleDisconnect();
                printDisplay("클라이언트 연결 끊김: " + uid);

            } catch (ClassNotFoundException e) {
                printDisplay("잘못된 객체가 전달되었습니다.");

            } finally {
                try {
                    if (cs != null) {
                        cs.close();
                    }
                } catch (IOException e) {
                    System.err.println("닫기 오류> " + e.getMessage());
                }
            }
        }

        // 연결 끊김 처리
        private void handleDisconnect() {
            // 중복 실행 방지
            if (!users.contains(this)) {
                return;
            }

            users.remove(this);

            // 게임 룸에서 제거 (게임 중이면 자동 기권 처리됨)
            if (this.myRoom != null && this.myPlayer != null) {
                this.myRoom.exitPlayer(myPlayer);

                if (this.myRoom.getPlayerCount() > 0) {
                    String roomInfo = this.myRoom.getTitle() + ", " +
                            this.myRoom.getOwner().getClientHandler().getUid();
                    this.myRoom.broadcastGameRoom(
                            new OmokMsg("SERVER", OmokMsg.MODE_ROOM_INFO, roomInfo)
                    );
                    this.myRoom.broadcastGameRoom(
                            new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_GAME_USER_LIST,
                                    this.myRoom.getPlayersForClient())
                    );
                }

                this.myRoom = null;
                this.myPlayer = null;
            }

            // 전체 사용자 목록 갱신
            String allUserIds = OmokServer.this.getAllLUsers();
            broadcastLobby(new OmokMsg("SERVER", OmokMsg.MODE_REFRESH_USER_LIST, allUserIds));

            // 방 목록 갱신
            OmokServer.this.broadcastRoomListToAll();
        }

        // 클라이언트에게 메시지 전송
        synchronized void send(OmokMsg msg) {
            try {
                if (out == null) {
                    return;
                }
                out.writeObject(msg);
                out.flush();
                out.reset();
            } catch (IOException e) {
                System.err.println("전송 오류 (" + uid + "): " + e.getMessage());
            }
        }

        // 로비에 있는 모든 클라이언트에게 메시지 전송
        private void broadcastLobby(OmokMsg msg) {
            for (ClientHandler user : users) {
                user.send(msg);
            }
        }

        String getUid() {
            return uid;
        }

        @Override
        public void run() {
            receiveMessages(clientSocket);
        }
    }

    // 방 ID로 게임 방 찾기
    public GameRoom findGameRoom(String roomId) {
        synchronized (rooms) {
            for (GameRoom room : rooms) {
                if (room.getRoomId().equals(roomId)) {
                    return room;
                }
            }
        }
        return null;
    }

    // 접속한 모든 사용자 ID를 쉼표로 구분한 문자열로 반환
    public String getAllLUsers() {
        StringBuilder allLUsers = new StringBuilder();
        synchronized (users) {
            for (ClientHandler user : users) {
                if (!allLUsers.isEmpty()) {
                    allLUsers.append(",");
                }
                allLUsers.append(user.getUid());
            }
        }
        return allLUsers.toString();
    }

    // 모든 게임 방 정보를 문자열로 반환  (roomId|제목|상태)
    public String getAllRooms() {
        StringBuilder allRooms = new StringBuilder();
        synchronized (rooms) {
            if (rooms.isEmpty()) {
                return allRooms.toString();
            }
            for (GameRoom room : rooms) {
                if (!allRooms.isEmpty()) {
                    allRooms.append(",");
                }
                allRooms.append(room.getRoomId());
                allRooms.append("|");
                allRooms.append(room.getTitle());
                allRooms.append("|");
                allRooms.append(room.getRoomStatus());
            }
        }
        return allRooms.toString();
    }

    public static void main(String[] args) {
        new OmokServer(54322);
    }
}