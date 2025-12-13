package omok_client;

import omok_client.model.RoomEntry;
import omok_client.view.omokBoardView;
import omok_shared.OmokMsg;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.DefaultStyledDocument;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.List;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class OmokClient extends JFrame {
    private Socket socket;
    private String serverAddress;
    private int serverPort;
    private ObjectOutputStream out;
    private String uid;                           // 사용자 ID
    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);
    private Thread receiveThread;                 // 서버 메시지 수신 스레드
    private int endIndex;                         // 복기용: 전체 수의 개수
    private int currentIndex;                     // 복기용: 현재 보고 있는 수

    // 각 화면 패널
    private LoginPanel loginPanel;
    private LobbyPanel lobbyPanel;
    private WaitingRoomPanel waitingRoomPanel;
    private GamePanel gamePanel;

    // 화면 식별자 상수
    public static final String LOGIN_VIEW = "Login";
    public static final String LOBBY_VIEW = "Lobby";
    public static final String WAITING_VIEW = "WaitingRoom";
    public static final String GAME_VIEW = "Game";

    // 클라이언트 생성자
    public OmokClient(String address, String port) {
        setTitle("같이 둬 1.0");
        // 기본 종료 동작 막기 (종료 확인 대화상자 표시용)
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        serverAddress = address;
        serverPort = Integer.parseInt(port);

        // 윈도우 종료 리스너 추가
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClosing();
            }
        });

        // 각 화면 패널 생성
        loginPanel = new LoginPanel(this);
        lobbyPanel = new LobbyPanel(this);
        gamePanel = new GamePanel(this);
        waitingRoomPanel = new WaitingRoomPanel(this);

        // CardLayout에 패널 추가
        mainPanel.add(loginPanel, LOGIN_VIEW);
        mainPanel.add(lobbyPanel, LOBBY_VIEW);
        mainPanel.add(waitingRoomPanel, WAITING_VIEW);
        mainPanel.add(gamePanel, GAME_VIEW);

        // 프레임 설정
        add(mainPanel);
        setSize(800, 800);
        setLocationRelativeTo(null);
        setVisible(true);

        showView(LOGIN_VIEW);
    }

    // 윈도우 종료 처리
    private void handleWindowClosing() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "프로그램을 종료하시겠습니까?",
                "종료 확인",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            if (socket != null && !socket.isClosed()) {
                send(new OmokMsg(uid, OmokMsg.MODE_LOGOUT));
            }
            System.exit(0);
        }
    }

    // 화면 전환
    public void showView(String viewName) {
        cardLayout.show(mainPanel, viewName);

        // 각 화면에 맞는 프레임 크기 설정
        if (viewName.equals(GAME_VIEW)) {
            gamePanel.resetGamePanel();
            setSize(900, 650);
        } else if(viewName.equals(LOGIN_VIEW)) {
            setSize(400, 100);
        } else if(viewName.equals(LOBBY_VIEW)) {
            setSize(400, 500);
        } else if(viewName.equals(WAITING_VIEW)) {
            setSize(400, 500);
        }
        setLocationRelativeTo(null);
        revalidate();
        repaint();
    }

    // 서버 연결
    public void connectToServer(String userID) {
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            out.flush();
            sendUserID(userID);

            // 로비 화면으로 전환
            SwingUtilities.invokeLater(() -> {
                showView(LOBBY_VIEW);
            });

            // 서버 메시지 수신 스레드 시작
            startReceiveThread();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 서버 메시지 수신 스레드
    public void startReceiveThread() {
        receiveThread = new Thread(new Runnable() {
            private ObjectInputStream in;

            @Override
            public void run() {
                try {
                    in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
                } catch (IOException e) {
                    lobbyDisplay("입력 스트림이 열리지 않음.");
                }

                // 메시지 수신 루프
                while (true) {
                    try {
                        OmokMsg msg = (OmokMsg) in.readObject();
                        if (msg == null) {
                            disconnect();
                            lobbyDisplay("서버 연결 끊김");
                            return;
                        }

                        // 메시지 타입에 따라 처리
                        switch (msg.getMode()) {
                            case OmokMsg.MODE_REFRESH_USER_LIST:
                                // 로비 사용자 목록 갱신
                                String allUser = msg.getMessage();
                                String[] userArray = allUser.split(",");
                                lobbyPanel.updateUserList(userArray);
                                break;

                            case OmokMsg.MODE_LOBBY_STRING:
                                // 로비 채팅 메시지
                                lobbyDisplay(msg.getUserID() + ": " + msg.getMessage());
                                break;

                            case OmokMsg.MODE_LOBBY_IMAGE:
                                // 로비 이미지 메시지                 -이미지가 있어요 저희?
                                lobbyDisplay(msg.getUserID() + ": " + msg.getMessage());
                                break;

                            case OmokMsg.MODE_ROOM_ENTERED:
                                // 방 입장 성공 -> 대기실 화면으로 전환
                                waitingRoomPanel.clearChat();
                                SwingUtilities.invokeLater(() -> showView(WAITING_VIEW));
                                break;

                            case OmokMsg.MODE_REFRESH_ROOM_LIST:
                                // 방 목록 갱신
                                String allRoom = msg.getMessage();
                                String[] roomArray = (allRoom == null || allRoom.isEmpty())
                                        ? new String[0]
                                        : allRoom.split(",");
                                lobbyPanel.updateRoomList(roomArray);
                                break;

                            case OmokMsg.MODE_ROOM_INFO:
                                // 방 정보 (제목, 방장)
                                String roomInfoMessage = msg.getMessage();
                                String[] roomInfo = roomInfoMessage.split(",");
                                waitingRoomPanel.updateRoomInfo(roomInfo[0], roomInfo[1]);
                                break;

                            case OmokMsg.MODE_REFRESH_GAME_USER_LIST:
                                // 방 참가자 목록 갱신
                                String allGameUser = msg.getMessage();
                                String[] gameUserArray = allGameUser.split(",");
                                waitingRoomPanel.updatePlayerInfo(gameUserArray);
                                break;

                            case OmokMsg.MODE_EXIT_ROOM:
                                // 방 나가기 성공 -> 로비로 돌아가기
                                String exitRoom = msg.getMessage();
                                if (exitRoom.equals("SUCCESS")) {
                                    SwingUtilities.invokeLater(() -> showView(LOBBY_VIEW));
                                }
                                break;

                            case OmokMsg.MODE_WAITING_STRING:
                                // 대기실 메시지 처리
                                if (msg.getMessage().equals("SPECTATOR")) {
                                    // 관전자 모드 설정
                                    SwingUtilities.invokeLater(() -> {
                                        gamePanel.setSpectatorMode(true);
                                    });
                                } else if (msg.getUserID().equals("SERVER")) {
                                    // 서버 메시지
                                    String message = msg.getMessage();
                                    SwingUtilities.invokeLater(() -> {
                                        if (gamePanel != null) {
                                            gamePanel.appendMessage("[서버] " + message);
                                        }
                                    });
                                } else {
                                    // 일반 사용자 채팅
                                    waitingDisplay(msg.getUserID() + ": " + msg.getMessage());
                                }
                                break;

                            case OmokMsg.MODE_GAME_CHAT:
                                // 게임 중 채팅 메시지
                                SwingUtilities.invokeLater(() -> {
                                    if (msg.getUserID().equals("SERVER")) {
                                        gamePanel.appendMessage("[서버] " + msg.getMessage());
                                    } else {
                                        gamePanel.appendMessage(msg.getUserID() + ": " + msg.getMessage());
                                    }
                                });
                                break;

                            case OmokMsg.MODE_START:
                                // 게임 시작
                                if (msg.getMessage().equals("SUCCESS")) {
                                    SwingUtilities.invokeLater(() -> showView(GAME_VIEW));
                                } else {
                                    // 시작 실패 메시지 표시
                                    String errorMsg = msg.getMessage();
                                    SwingUtilities.invokeLater(() -> {
                                        if (errorMsg.startsWith("FAILED:")) {
                                            String actualMessage = errorMsg.substring(7);
                                            showMessage(waitingRoomPanel, actualMessage, "알림", JOptionPane.WARNING_MESSAGE);
                                        } else {
                                            showMessage(waitingRoomPanel, "게임을 시작할 수 없습니다.", "알림", JOptionPane.WARNING_MESSAGE);
                                        }
                                    });
                                }
                                break;

                            case OmokMsg.MODE_STONE_PLACED:
                                // 돌이 놓여짐
                                int x = msg.getX();
                                int y = msg.getY();
                                int color = msg.getColor();
                                SwingUtilities.invokeLater(() -> {
                                    gamePanel.placeStone(x, y, color);
                                    String colorName = (color == 1) ? "흑돌" : "백돌";
                                    gamePanel.appendMessage(msg.getUserID() + "님이 " + colorName + "을 (" + x + ", " + y + ")에 놓았습니다.");
                                });
                                break;

                            case OmokMsg.MODE_SUGGESTION_RECEIVED:
                                // 관전자 훈수 수신
                                int sugX = msg.getX();
                                int sugY = msg.getY();
                                int adviceColor = msg.getAdviceColor();
                                SwingUtilities.invokeLater(() -> {
                                    gamePanel.showSuggestion(sugX, sugY, msg.getUserID(), adviceColor);
                                });
                                break;

                            case OmokMsg.MODE_ADVICE_REQUEST_BROADCAST:
                                // 관전자가 훈수 요청을 받음
                                SwingUtilities.invokeLater(() -> {
                                    gamePanel.onAdviceRequested();
                                    gamePanel.appendMessage("[시스템] " + msg.getMessage());
                                });
                                break;

                            case OmokMsg.MODE_ADVICE_OFFERS_LIST:
                                // 플레이어가 훈수 제공자 목록을 받음
                                String offersStr = msg.getMessage();
                                List<String> advisors = new ArrayList<>();
                                if (offersStr != null && !offersStr.isEmpty()) {
                                    String[] advisorArray = offersStr.split(",");
                                    advisors.addAll(Arrays.asList(advisorArray));
                                }
                                if (!advisors.isEmpty()) {
                                    SwingUtilities.invokeLater(() -> {
                                        gamePanel.onAdvisorsListReceived(advisors);
                                    });
                                }
                                break;

                            case OmokMsg.MODE_ADVICE_SELECTED:
                                // 훈수 선택 완료 알림
                                SwingUtilities.invokeLater(() -> {
                                    gamePanel.appendMessage("[시스템] " + msg.getMessage());
                                });
                                break;

                            case OmokMsg.MODE_ADVICE_LIMIT_EXCEEDED:
                                // 훈수 횟수 초과
                                SwingUtilities.invokeLater(() -> {
                                    gamePanel.appendMessage("[시스템] " + msg.getMessage());
                                    showMessage(gamePanel, msg.getMessage(), "알림", JOptionPane.WARNING_MESSAGE);
                                });
                                break;

                            case OmokMsg.MODE_TURN_CHANGED:
                                // 턴 변경
                                SwingUtilities.invokeLater(() -> {
                                    gamePanel.updateTurn(msg.getMessage());
                                });
                                break;

                            case OmokMsg.MODE_GAME_OVER:
                                // 게임 종료
                                SwingUtilities.invokeLater(() -> {
                                    gamePanel.gameOver(msg.getMessage());
                                    showMessage(gamePanel, msg.getMessage(), "게임 종료", JOptionPane.INFORMATION_MESSAGE);
                                });
                                break;

                            case OmokMsg.MODE_RESULT_COUNT:
                                endIndex = Integer.parseInt(msg.getMessage());
                                currentIndex = -1;
                                gamePanel.getReviewButton().setEnabled(true);
                                break;

                            case OmokMsg.MODE_REPLAY_NEXT:
                                // 복기 - 다음 수
                                x = msg.getX();
                                y = msg.getY();
                                color = msg.getColor();
                                if (color == 3) {
                                    // 관전자 훈수 표시
                                    SwingUtilities.invokeLater(() -> {
                                        gamePanel.reviewShowSuggestion(x, y, msg.getAdviceColor());
                                    });
                                    break;
                                }
                                SwingUtilities.invokeLater(() -> {
                                    gamePanel.reviewPlaceStone(x, y, color);
                                });
                                break;

                            case OmokMsg.MODE_REPLAY_PREV:
                                // 복기 - 이전 수
                                x = msg.getX();
                                y = msg.getY();
                                color = msg.getColor();
                                if (color == 3) {
                                    // 관전자 훈수 표시
                                    SwingUtilities.invokeLater(() -> {
                                        gamePanel.reviewShowSuggestion(x, y, msg.getAdviceColor());
                                    });
                                    break;
                                }
                                SwingUtilities.invokeLater(() -> {
                                    gamePanel.reviewRemoveStone(x, y);
                                });
                                break;

                            case OmokMsg.MODE_CURRENT_COUNT:
                                // 현재 복기
                                currentIndex = Integer.parseInt(msg.getMessage());
                                break;

                            case OmokMsg.MODE_PLAYER_INFO:
                                // 플레이어 정보 수신 (이름과 전적 포함)
                                String playerInfoStr = msg.getMessage();
                                String[] playerInfoParts = playerInfoStr.split("\\|");
                                if (playerInfoParts.length >= 2) {
                                    String player1NameWithStats = playerInfoParts[0];
                                    String player2NameWithStats = playerInfoParts[1];
                                    SwingUtilities.invokeLater(() -> {
                                        gamePanel.updatePlayerNames(player1NameWithStats, player2NameWithStats);
                                    });
                                }
                                break;

                            case OmokMsg.MODE_SPECTATOR_COUNT:
                                // 관전자 수 업데이트
                                int spectatorCount = Integer.parseInt(msg.getMessage());
                                SwingUtilities.invokeLater(() -> {
                                    gamePanel.updateSpectatorAvailability(spectatorCount > 0);
                                });
                                break;

                            case OmokMsg.MODE_USER_STATS:
                                // 전적 정보 업데이트
                                String statsStr = msg.getMessage();
                                if (statsStr != null && !statsStr.isEmpty()) {
                                    String[] stats = statsStr.split(",");
                                    SwingUtilities.invokeLater(() -> {
                                        lobbyPanel.updateUserStats(stats);
                                    });
                                }
                                break;
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        receiveThread.start();
    }

    // 서버 연결 해제
    private void disconnect() {
        send(new OmokMsg(uid, OmokMsg.MODE_LOGOUT));
        try {
            receiveThread = null;
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("클라이언트 닫기 오류> " + e.getMessage());
            System.exit(-1);
        }
    }

    // 사용자 ID 전송 (로그인)
    private void sendUserID(String userID) {
        uid = userID;
        send(new OmokMsg(uid, OmokMsg.MODE_LOGIN));
    }

    // 서버에 메시지 전송
    public void send(OmokMsg msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            System.err.println("클라이언트 일반 전송 오류> " + e.getMessage());
        }
    }

    // server.txt 파일에서 서버 설정 로드  - 여기도 이제 바꾸면 될듯
    private static String[] loadServerConfig() {
        String fileName = "server.txt";
        String defaultIp = "127.0.0.1";
        String defaultPort = "54321";

        File file = new File(fileName);
        if (!file.exists()) {
            return new String[]{defaultIp, defaultPort};
        }
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String address = br.readLine();
            String port = br.readLine();
            return new String[]{address, port};
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 로비 채팅 영역에 메시지 출력
    private void lobbyDisplay(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                if (lobbyPanel == null || lobbyPanel.getChatArea() == null) return;

                int len = lobbyPanel.getChatArea().getDocument().getLength();
                lobbyPanel.getDocuments().insertString(len, message + "\n", null);
                lobbyPanel.getChatArea().setCaretPosition(len);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // 대기실 채팅 영역에 메시지 출력
    private void waitingDisplay(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                if (waitingRoomPanel == null || waitingRoomPanel.getChatArea() == null) {
                    return;
                }
                int len = waitingRoomPanel.getChatArea().getDocument().getLength();
                waitingRoomPanel.getDocuments().insertString(len, message + "\n", null);
                waitingRoomPanel.getChatArea().setCaretPosition(len);

            } catch (Exception e) {
                System.err.println("대기실 채팅 출력 오류: " + e.getMessage());
            }
        });
    }

    // Getters & Setters
    public String getUid() {
        return uid;
    }

    public void showMessage(Component parent, String message, String title, int messageType) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(parent, message, title, messageType);
        });
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public static void main(String[] args) {
        String[] config = loadServerConfig();
        new OmokClient(config[0],config[1]);
    }
}

// 로그인 화면
class LoginPanel extends JPanel {
    private OmokClient client;
    private JTextField idField;
    private JButton connectButton;

    public LoginPanel(OmokClient client) {
        this.client = client;

        // ID 입력 필드
        JPanel formPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        formPanel.add(new JLabel("User ID:"));
        idField = new JTextField(15);
        formPanel.add(idField);

        // 접속 버튼
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        connectButton = new JButton("접속하기");
        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleLogin();
            }
        });
        buttonPanel.add(connectButton);
        add(formPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    // 로그인 처리
    private void handleLogin() {
        String userID = idField.getText().trim();

        if (userID.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "ID를 입력해야 합니다.",
                    "입력 오류",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            client.connectToServer(userID);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "서버에 연결할 수 없습니다.\n오류: " + e.getMessage(),
                    "연결 오류",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
}
/*
 1. 방 생성/접속 화면 (LobbyPanel)
 - 사용자 목록, 방 목록, 방 만들기 버튼, 입장하기 버튼
*/
class LobbyPanel extends JPanel {
    private OmokClient client;
    private JTextPane chatArea;
    private DefaultStyledDocument document;

    private DefaultListModel<String> userListModel;
    private JList<String> userList;
    private Map<String, String> userStats = new HashMap<>();

    private DefaultListModel<RoomEntry> roomListModel;
    private JList<RoomEntry> roomList;

    public LobbyPanel(OmokClient client) {
        this.client = client;
        setLayout(new BorderLayout(10, 10)); // 전체 레이아웃

        // --- 상단 (정보 및 버튼) ---
        JPanel infoPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        // 사용자 목록  -> 서버로부터 Vector users 부분에서, 이름만 받아오기
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setBorder(new TitledBorder("접속자"));

        // 방 목록
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        JScrollPane roomScrollPane = new JScrollPane(roomList);
        roomScrollPane.setBorder(new TitledBorder("방 목록"));

        infoPanel.add(userScrollPane);
        infoPanel.add(roomScrollPane);

        // --- 하단 (채팅 및 버튼) ---
        JPanel chatAndControlPanel = new JPanel(new BorderLayout());

        // 채팅 영역
        document = new DefaultStyledDocument();
        chatArea = new JTextPane(document);
        chatArea.setEditable(false);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setBorder(new TitledBorder("채팅"));

        // 입력 및 버튼
        JPanel inputPanel = new JPanel(new BorderLayout());
        JTextField chatInput = new JTextField();
        JButton sendButton = new JButton("보내기");
        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_LOBBY_STRING, chatInput.getText()));
                chatInput.setText("");
            }
        });
        chatInput.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_LOBBY_STRING, chatInput.getText()));
                chatInput.setText("");
            }
        });

        JPanel controlPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        JButton createButton = new JButton("방 만들기");
        JButton joinButton = new JButton("입장하기");

        createButton.addActionListener(new ActionListener() {
            @Override
           public void actionPerformed(ActionEvent e) {
               String roomTitle = JOptionPane.showInputDialog(
                       LobbyPanel.this,
                       "생성할 방의 제목을 입력하세요:",
                       "방 만들기",
                       JOptionPane.QUESTION_MESSAGE
               );
               if (roomTitle != null && !roomTitle.trim().isEmpty()) {
                   try {
                       client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_MAKE_ROOM, roomTitle));
                   } catch (Exception ex) {
                       throw new RuntimeException(ex);
                   }
               }
           }
        });
        joinButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                RoomEntry selectedRoom = roomList.getSelectedValue();
                if (selectedRoom == null) {
                    JOptionPane.showMessageDialog(LobbyPanel.this,
                            "입장할 방을 먼저 선택해주세요.",
                            "알림",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String roomIdToSend = selectedRoom.getRoomId();
                try {
                    client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_JOIN_ROOM, roomIdToSend));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(LobbyPanel.this,
                            "방 입장 요청 중 오류 발생.",
                            "오류",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
       });
        controlPanel.add(createButton);
        controlPanel.add(joinButton);

        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        chatAndControlPanel.add(chatScrollPane, BorderLayout.CENTER);
        chatAndControlPanel.add(inputPanel, BorderLayout.SOUTH);

        // --- 최종 조립 ---
        add(infoPanel, BorderLayout.NORTH);
        add(chatAndControlPanel, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);
    }
    public JTextPane getChatArea() {
        return chatArea;
    }
    public DefaultStyledDocument getDocuments() {
        return document;
    }

    public void updateUserList(String[] users) {
        userListModel.clear();
        for (String user : users) {
            // 전적 정보 포함
            String displayText = user;
            if (userStats.containsKey(user)) {
                displayText += " " + userStats.get(user);
            }
            userListModel.addElement(displayText);
        }
    }
    public void updateUserStats(String[] stats) {
        userStats.clear();
        for (String stat : stats) {
            String[] parts = stat.split(":");
            if (parts.length == 2) {
                userStats.put(parts[0], parts[1]);
            }
        }
    }
    public void updateRoomList(String[] rooms) {
        roomListModel.clear();
        if (rooms == null) return;
        for (String room : rooms) {
            String[] parts = room.split("\\|");
            String roomId = parts[0];
            String title = parts[1];
            String status = parts.length > 2 ? parts[2] : "대기중"; // 게임 상태

            String displayTitle = title + " [" + status + "]";
            RoomEntry newEntry = new RoomEntry(roomId, displayTitle);
            roomListModel.addElement(newEntry);
        }
    }
}


/*
 2. 방 대기 화면 (WaitingRoomPanel)
 - 방 정보, 참가자 목록, 채팅, 게임 시작/나가기 버튼
*/

// 여기서 Player 생성
class WaitingRoomPanel extends JPanel {
    private OmokClient client;
    private JTextArea roomInfo;
    private JList<String> playerList;
    private DefaultListModel playerListModel;
    private JTextPane chatArea;
    private DefaultStyledDocument document;
    public WaitingRoomPanel(OmokClient client) {
        this.client = client;
        setLayout(new BorderLayout(10, 10));

        // --- 상단 (방 정보 및 참가자) ---
        JPanel topPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        // 방 정보/게임 설정
        roomInfo = new JTextArea();
        roomInfo.setEditable(false);
        JScrollPane roomInfoPane = new JScrollPane(roomInfo);
        roomInfoPane.setBorder(new TitledBorder("방 정보 / 설정"));

        // 참가자 목록

        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel);

        JScrollPane playerScrollPane = new JScrollPane(playerList);
        playerScrollPane.setBorder(new TitledBorder("참가자"));

        topPanel.add(roomInfoPane);
        topPanel.add(playerScrollPane);

        // --- 중간 (채팅) ---
        document = new DefaultStyledDocument();
        chatArea = new JTextPane(document);
        chatArea.setEditable(false);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setBorder(new TitledBorder("채팅"));

        // --- 하단 (입력 및 버튼) ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel inputPanel = new JPanel(new BorderLayout());
        JTextField chatInput = new JTextField();
        JButton sendButton = new JButton("보내기");

        JPanel controlPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        JButton startButton = new JButton("게임 시작");
        JButton exitButton = new JButton("나가기");
        controlPanel.add(startButton);
        controlPanel.add(exitButton);


        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        bottomPanel.add(inputPanel, BorderLayout.NORTH);
        bottomPanel.add(controlPanel, BorderLayout.SOUTH);

        // --- 최종 조립 ---
        add(topPanel, BorderLayout.NORTH);
        add(chatScrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // 게임 시작 버튼 (임시)
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_GAME_START,""));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        sendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_WAITING_STRING, chatInput.getText()));
                chatInput.setText("");
            }
        });
        chatInput.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_WAITING_STRING, chatInput.getText()));
                chatInput.setText("");
            }
        });

        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_EXIT_ROOM, ""));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }
    public void clearChat() {
        document = new DefaultStyledDocument();
        chatArea.setDocument(document);
    }

    public JTextPane getChatArea() {
        return chatArea;
    }
    public DefaultStyledDocument getDocuments() {
        return document;
    }

    public void updateRoomInfo(String title, String owner) {
        String text = String.format("방 제목: %s\n방장: %s", title, owner);
        roomInfo.setText(text);
    }
    public void updatePlayerInfo(String[] users) {
        playerListModel.clear();
        for (String user : users) {
            playerListModel.addElement(user);
        }
    }
}

/*
  3. 오목 게임 화면 (GamePanel)
  - 오목판, 게임 정보, 채팅/대화
*/
class GamePanel extends JPanel {
    private OmokClient client;
    private omokBoardView omokBoard;
    private JTextArea messageArea;
    private JLabel turnLabel;
    private JLabel player1Label;
    private JLabel player2Label;
    private JButton reviewButton;
    private JButton prevButton;
    private JButton nextButton;
    private boolean isSpectator;
    private List<Point> suggestions;

    // 훈수 시스템 필드
    private JButton requestAdviceButton;    // 플레이어용: 훈수 요청 버튼
    private JButton offerAdviceButton;      // 관전자용: 훈수 제공 버튼
    private List<String> availableAdvisors; // 훈수 제공 의사를 표시한 관전자 목록
    private Map<Point, Color> suggestionColors; // 훈수별 색상

    public GamePanel(OmokClient client) {
        this.client = client;
        this.suggestions = new ArrayList<>();
        this.availableAdvisors = new ArrayList<>();
        this.suggestionColors = new HashMap<>();
        this.isSpectator = false;

        setLayout(new BorderLayout(10, 10));

        // --- 오목판 영역 (중앙) ---
        omokBoard = new omokBoardView();
        omokBoard.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleBoardClick(e);
            }
        });

        JPanel boardPanel = new JPanel(new GridBagLayout());
        boardPanel.add(omokBoard);

        // --- 사이드 패널 (동쪽) ---
        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setPreferredSize(new Dimension(250, 0));

        // 현재 턴 표시
        turnLabel = new JLabel("게임 대기 중...");
        turnLabel.setMaximumSize(new Dimension(250, 30));

        // 플레이어 정보
        JPanel playerInfoPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        player1Label = new JLabel("● 흑돌: -");
        player2Label = new JLabel("○ 백돌: -");
        playerInfoPanel.add(player1Label);
        playerInfoPanel.add(player2Label);
        playerInfoPanel.setBorder(new TitledBorder("플레이어 정보"));
        playerInfoPanel.setMaximumSize(new Dimension(250, 80));

        // 게임 메시지/대화
        messageArea = new JTextArea(12, 20);
        messageArea.setEditable(false);
        messageArea.setText("[시스템] 게임 화면에 입장했습니다.\n");
        JScrollPane messageScrollPane = new JScrollPane(messageArea);
        messageScrollPane.setBorder(new TitledBorder("게임 메시지"));

        // 채팅 입력 패널
        JPanel chatInputPanel = new JPanel(new BorderLayout(5, 0));
        JTextField chatInput = new JTextField();
        JButton chatSendButton = new JButton("전송");

        chatSendButton.addActionListener(e -> {
            String text = chatInput.getText().trim();
            if (!text.isEmpty()) {
                client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_GAME_CHAT, text));
                chatInput.setText("");
            }
        });

        chatInput.addActionListener(e -> {
            String text = chatInput.getText().trim();
            if (!text.isEmpty()) {
                client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_GAME_CHAT, text));
                chatInput.setText("");
            }
        });

        chatInputPanel.add(chatInput, BorderLayout.CENTER);
        chatInputPanel.add(chatSendButton, BorderLayout.EAST);

        // 제어 버튼 패널
        JPanel controlPanel = new JPanel(new GridLayout(6, 1, 5, 5));

        JButton surrenderButton = new JButton("기권");
        JButton exitButton = new JButton("나가기");
        reviewButton = new JButton("복기");

        // 훈수 관련 버튼
        requestAdviceButton = new JButton("훈수 요청");
        offerAdviceButton = new JButton("훈수 제공");

        // 초기에는 둘 다 비활성화
        requestAdviceButton.setEnabled(false);
        offerAdviceButton.setEnabled(false);
        offerAdviceButton.setVisible(false);

        JPanel navPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        prevButton = new JButton("이전");
        nextButton = new JButton("이후");
        navPanel.add(prevButton);
        navPanel.add(nextButton);

        controlPanel.add(surrenderButton);
        controlPanel.add(requestAdviceButton);
        controlPanel.add(offerAdviceButton);
        controlPanel.add(exitButton);
        controlPanel.add(reviewButton);
        controlPanel.add(navPanel);

        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(turnLabel);
        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(playerInfoPanel);
        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(messageScrollPane);
        sidePanel.add(Box.createVerticalStrut(5));
        sidePanel.add(chatInputPanel);
        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(controlPanel);
        sidePanel.add(Box.createVerticalGlue());

        reviewButton.setEnabled(false);
        prevButton.setEnabled(false);
        nextButton.setEnabled(false);

        add(boardPanel, BorderLayout.CENTER);
        add(sidePanel, BorderLayout.EAST);

        surrenderButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "기권하시겠습니까?",
                    "기권 확인",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_SURRENDER));
            }
        });

        exitButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "게임을 종료하고 대기실로 돌아가시겠습니까?",
                    "게임 종료",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                client.setCurrentIndex(-1);
                client.setEndIndex(0);
                resetGamePanel();
                client.showView(OmokClient.WAITING_VIEW);
            }
        });

        // 훈수 요청 버튼 (플레이어용)
        requestAdviceButton.addActionListener(e -> {
            client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_REQUEST_ADVICE));
            requestAdviceButton.setEnabled(false);
        });

        // 훈수 제공 버튼 (관전자용)
        offerAdviceButton.addActionListener(e -> {
            client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_OFFER_ADVICE));
            offerAdviceButton.setEnabled(false);
            appendMessage("[시스템] 훈수 제공 의사를 표시했습니다.");
        });

        reviewButton.addActionListener(e -> {
            omokBoard.reset();
            reviewButton.setEnabled(false);
            nextButton.setEnabled(true);
        });

        prevButton.addActionListener(e -> {
            clearSuggestions();
            if (client.getCurrentIndex() == client.getEndIndex() - 1) {
                nextButton.setEnabled(true);
            }
            if (client.getCurrentIndex() == -1) {
                prevButton.setEnabled(false);
                return;
            }
            client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_REPLAY_PREV,
                    String.valueOf(client.getCurrentIndex()), String.valueOf(client.getEndIndex())));
        });

        nextButton.addActionListener(e -> {
            clearSuggestions();
            if (client.getCurrentIndex() == -1) {
                prevButton.setEnabled(true);
            }
            if (client.getCurrentIndex() == client.getEndIndex() - 1) {
                nextButton.setEnabled(false);
                return;
            }
            client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_REPLAY_NEXT,
                    String.valueOf(client.getCurrentIndex()), String.valueOf(client.getEndIndex())));
        });
    }

    // 관전자 선택 대화상자 표시
    public void showAdvisorSelectionDialog(List<String> advisors) {
        this.availableAdvisors = advisors;

        //dialog 통해서 목록 표시
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "훈수할 관전자 선택", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(300, 200);
        dialog.setLocationRelativeTo(this);

        JLabel label = new JLabel("훈수를 받을 관전자를 선택하세요:");
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String advisor : advisors) {
            listModel.addElement(advisor);
        }
        JList<String> advisorList = new JList<>(listModel);
        advisorList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(advisorList);

        JButton selectButton = new JButton("선택");
        JButton cancelButton = new JButton("취소");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.add(selectButton);
        buttonPanel.add(cancelButton);

        selectButton.addActionListener(e -> {
            String selected = advisorList.getSelectedValue();
            if (selected != null) {
                client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_SELECT_ADVISOR, selected));
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog, "관전자를 선택해주세요.",
                        "알림", JOptionPane.WARNING_MESSAGE);
            }
        });

        cancelButton.addActionListener(e -> {
            dialog.dispose();
            requestAdviceButton.setEnabled(true); // 다시 요청 가능하도록
        });

        dialog.add(label, BorderLayout.NORTH);
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // 관전자 모드 설정
    public void setSpectatorMode(boolean isSpectator) {
        this.isSpectator = isSpectator;

        if (isSpectator) {
            appendMessage("[시스템] 관전자 모드입니다.");
            requestAdviceButton.setVisible(false);
            offerAdviceButton.setVisible(true);
        } else {
            requestAdviceButton.setVisible(true);
            requestAdviceButton.setEnabled(true);
            offerAdviceButton.setVisible(false);
        }
    }

    // 훈수 요청 받음 (관전자용)
    public void onAdviceRequested() {
        offerAdviceButton.setEnabled(true);
    }

    // 훈수 제공자 목록 업데이트 받음 (플레이어용)
    public void onAdvisorsListReceived(List<String> advisors) {
        if (!advisors.isEmpty()) {
            SwingUtilities.invokeLater(() -> showAdvisorSelectionDialog(advisors));
        }
    }

    // 색상 훈수 표시
    public void showSuggestion(int x, int y, String spectatorId, int colorCode) {
        Point p = new Point(x, y);
        suggestions.add(p);

        Color color = new Color(colorCode);
        suggestionColors.put(p, color);

        omokBoard.addSuggestion(x, y, color);
        appendMessage("[훈수] " + spectatorId + "님이 (" + x + ", " + y + ")를 제안했습니다.");
    }

    private void clearSuggestions() {
        suggestions.clear();
        suggestionColors.clear();
        omokBoard.clearSuggestions();
    }

    public void resetGamePanel() {
        omokBoard.reset();
        omokBoard.setEnabled(true);
        messageArea.setText("[시스템] 게임 화면에 입장했습니다.\n");
        turnLabel.setText("게임 대기 중...");
        player1Label.setText("● 흑돌: -");
        player2Label.setText("○ 백돌: -");
        suggestions.clear();
        suggestionColors.clear();
        availableAdvisors.clear();
        reviewButton.setEnabled(false);
        prevButton.setEnabled(false);
        nextButton.setEnabled(false);

        if (!isSpectator) {
            requestAdviceButton.setVisible(true);
            requestAdviceButton.setEnabled(false);
            offerAdviceButton.setVisible(false);
        } else {
            requestAdviceButton.setVisible(false);
            offerAdviceButton.setVisible(true);
            offerAdviceButton.setEnabled(false);
        }

        revalidate();
        repaint();
    }

    private void handleBoardClick(MouseEvent e) {
        int margin = omokBoard.getMargin();
        int cellSize = omokBoardView.getCellSize();
        int x = (e.getX() - margin + 8 + cellSize / 2) / cellSize;
        int y = (e.getY() - margin + cellSize / 2) / cellSize;

        if (x >= 0 && x < 15 && y >= 0 && y < 15) {
            if (omokBoard.isEmpty(x, y)) {
                if (isSpectator) {
                    client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_SUGGEST_MOVE, x, y, 0));
                    appendMessage("[시스템] 훈수를 보냈습니다: (" + x + ", " + y + ")");
                } else {
                    int color = 1;
                    client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_PLACE_STONE, x, y, color));
                }
            } else {
                appendMessage("[경고] 이미 돌이 놓인 위치입니다.");
            }
        }
    }

    public void placeStone(int x, int y, int color) {
        omokBoard.placeStone(x, y, color);
        clearSuggestions();
        requestAdviceButton.setEnabled(true); // 돌을 놓은 후 다음 턴에 다시 요청 가능
    }

    public void updateTurn(String message) {
        turnLabel.setText(message);
        appendMessage("[턴] " + message);

        // 현재 차례인 플레이어만 훈수 요청 버튼 활성화
        if (!isSpectator) {
            if (message.contains(client.getUid() + "님")) {
                requestAdviceButton.setEnabled(true);
            } else {
                requestAdviceButton.setEnabled(false);
            }
        }
    }

    public void gameOver(String message) {
        appendMessage("[게임 종료] " + message);
        turnLabel.setText("게임 종료");
        omokBoard.setEnabled(false);
        requestAdviceButton.setEnabled(false);
        offerAdviceButton.setEnabled(false);
    }

    public void appendMessage(String message) {
        messageArea.append(message + "\n");
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
    }

    public void updatePlayerNames(String player1Name, String player2Name) {
        player1Label.setText("● 흑돌: " + player1Name);
        player2Label.setText("○ 백돌: " + player2Name);
    }

    public void updateSpectatorAvailability(boolean hasSpectators) {
        if (!isSpectator) {
            requestAdviceButton.setEnabled(hasSpectators);
            if (!hasSpectators) {
                appendMessage("[시스템] 현재 관전자가 없어 훈수를 요청할 수 없습니다.");
            }
        }
    }

    public JButton getReviewButton() { return reviewButton; }

    // 복기 관련 메서드
    public void reviewPlaceStone(int x, int y, int color) {
        omokBoard.placeStone(x, y, color);
    }
    public void reviewRemoveStone(int x, int y) {
        omokBoard.removeStone(x, y);
    }
    public void reviewShowSuggestion(int x, int y, int spectatorColor) {
        omokBoard.addSuggestion(x, y, spectatorColor);
    }
}