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
import java.util.ArrayList;
import java.util.List;

public class OmokClient extends JFrame {
    private Socket socket;
    private String serverAddress;
    private int serverPort;
    private ObjectOutputStream out;
    private String uid;
    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);
    private  Thread receiveThread;

    // 각 화면 패널
    private LoginPanel loginPanel;
    private LobbyPanel lobbyPanel;
    private WaitingRoomPanel waitingRoomPanel;
    private GamePanel gamePanel;

    // 패널
    public static final String LOGIN_VIEW = "Login";
    public static final String LOBBY_VIEW = "Lobby";
    public static final String WAITING_VIEW = "WaitingRoom";
    public static final String GAME_VIEW = "Game";

    public OmokClient(String address, String port) {
        setTitle("같이 둬 1.0");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        serverAddress = address;
        serverPort = Integer.parseInt(port);

        // --- 패널 생성 ---
        loginPanel = new LoginPanel(this);
        lobbyPanel = new LobbyPanel(this);
        gamePanel = new GamePanel(this);
        waitingRoomPanel = new WaitingRoomPanel(this);

        // --- mainPanel에 패널 추가 ---
        mainPanel.add(loginPanel, LOGIN_VIEW);
        mainPanel.add(lobbyPanel, LOBBY_VIEW);
        mainPanel.add(waitingRoomPanel, WAITING_VIEW);
        mainPanel.add(gamePanel, GAME_VIEW);

        // --- 프레임에 mainPanel 추가 및 설정 ---
        add(mainPanel);
        setSize(800, 800);
        setLocationRelativeTo(null);
        setVisible(true);

        showView(LOGIN_VIEW);
    }

    public void showView(String viewName) {
        cardLayout.show(mainPanel, viewName);
        // 프레임 크기 설정
        if (viewName.equals(GAME_VIEW)) {
            setSize(900, 650); // 오목판 + 사이드 패널을 위해 크기 증가
        } else if(viewName.equals(LOGIN_VIEW)) {
            setSize(400, 100);
        } else {
            setSize(400, 500); // 로비/대기실
        }
        setLocationRelativeTo(null); // 크기 변경 후 중앙 재배치

        // 레이아웃 강제 갱신
        revalidate();
        repaint();
    }

    public void connectToServer(String userID) {
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            out.flush();
            sendUserID(userID);
            SwingUtilities.invokeLater(() -> {
                showView(LOBBY_VIEW);
            });
            startReceiveThread();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

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
                while (true) {
                    try {
                        OmokMsg msg = (OmokMsg) in.readObject();
                        if (msg == null) {
                            disconnect();
                            lobbyDisplay("서버 연결 끊김");
                            return;
                        }
                        switch (msg.getMode()) {
                            case OmokMsg.MODE_REFRESH_USER_LIST:
                                String allUser = msg.getMessage();
                                String[] userArray = allUser.split(",");
                                lobbyPanel.updateUserList(userArray);
                                break;
                            case OmokMsg.MODE_LOBBY_STRING:
                                lobbyDisplay(msg.getUserID() + ": " + msg.getMessage());
                                break;
                            case OmokMsg.MODE_LOBBY_IMAGE:
                                lobbyDisplay(msg.getUserID() + ": " + msg.getMessage());
                                //printDisplay(msg.getImage());
                                break;
                            case OmokMsg.MODE_ROOM_ENTERED:
                                // 알아보니까, SwingUtilities.invokeLater 쓰는 것이 더 안전하다고 함!
                                // 교착상태, 깜빡임, 크래시 같은 상황을 예방 가능
                                SwingUtilities.invokeLater(() -> showView(WAITING_VIEW));
                                break;
                            case OmokMsg.MODE_REFRESH_ROOM_LIST:
                                String allRoom = msg.getMessage();
                                String[] roomArray = (allRoom == null || allRoom.isEmpty())
                                        ? new String[0]
                                        : allRoom.split(",");
                                lobbyPanel.updateRoomList(roomArray);
                                break;
                            case OmokMsg.MODE_ROOM_INFO:
                                String roomInfoMessage = msg.getMessage();
                                String[] roomInfo = roomInfoMessage.split(",");
                                waitingRoomPanel.updateRoomInfo(roomInfo[0], roomInfo[1]);
                                break;
                            case OmokMsg.MODE_REFRESH_GAME_USER_LIST:
                                String allGameUser = msg.getMessage();
                                String[] gameUserArray = allGameUser.split(",");
                                waitingRoomPanel.updatePlayerInfo(gameUserArray);
                                break;
                            case OmokMsg.MODE_EXIT_ROOM:
                                String exitRoom = msg.getMessage();
                                if (exitRoom.equals("SUCCESS")) {
                                    SwingUtilities.invokeLater(() -> showView(LOBBY_VIEW));
                                }
                            case OmokMsg.MODE_WAITING_STRING:
                                waitingDisplay(msg.getUserID() + ": " + msg.getMessage());
                                if (msg.getMessage().equals("SPECTATOR")) {
                                    // 해당 유저 관전자 설정
                                    SwingUtilities.invokeLater(() -> {
                                        gamePanel.setSpectatorMode(true);
                                    });
                                } else {
                                    waitingDisplay(msg.getUserID() + ": " + msg.getMessage());
                                }
                                break;
                            case OmokMsg.MODE_START:
                                if (msg.getMessage().equals("SUCCESS")) {
                                    SwingUtilities.invokeLater(() -> showView(GAME_VIEW));
                                } else {
                                    SwingUtilities.invokeLater(() -> {
                                    showMessage(waitingRoomPanel, "방장만이 게임을 시작할 수 있습니다.", "알림", JOptionPane.WARNING_MESSAGE);
                                    });
                                }
                                break;
                            case OmokMsg.MODE_STONE_PLACED:
                                // 돌이 놓여졌음
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
                                // 관전자 훈수 수신 (현재 턴 플레이어에게만 보임)
                                int sugX = msg.getX();
                                int sugY = msg.getY();
                                SwingUtilities.invokeLater(() -> {
                                    gamePanel.showSuggestion(sugX, sugY, msg.getUserID());
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
                        }
                    } catch (IOException | ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
        receiveThread.start();
    }

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

    private void sendUserID(String userID) {
        uid = userID;
        send(new OmokMsg(uid, OmokMsg.MODE_LOGIN));
    }

    public void send(OmokMsg msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            System.err.println("클라이언트 일반 전송 오류> " + e.getMessage());
        }
    }

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

    public String getUid() {
        return uid;
    }

   public void showMessage(Component parent, String message, String title, int messageType) {
       SwingUtilities.invokeLater(() -> {
           JOptionPane.showMessageDialog(parent, message, title, messageType);
       });
   }

    public static void main(String[] args) {
        String[] config = loadServerConfig();
        new OmokClient(config[0],config[1]);
    }
}

class LoginPanel extends JPanel {
    private OmokClient client;
    private JTextField idField;
    private JButton connectButton;

    public LoginPanel(OmokClient client) {
        this.client = client;
        JPanel formPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        formPanel.add(new JLabel("User ID:"));
        idField = new JTextField(15);
        formPanel.add(idField);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        connectButton = new JButton("접 속 하 기");
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
    private void handleLogin() {
        String userID = idField.getText().trim();

        if (userID.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "ID를 입력해야 합니다.",
                    "입력 오류",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        client.connectToServer(userID);
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
            userListModel.addElement(user);
        }
    }
    public void updateRoomList(String[] rooms) {
        roomListModel.clear();
        if (rooms == null) return;
        for (String room : rooms) {
            String[] parts = room.split("\\|");
            String roomId = parts[0];
            String title = parts[1];
            RoomEntry newEntry = new RoomEntry(roomId, title);
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
    private boolean isSpectator;
    private List<Point> suggestions;

    public GamePanel(OmokClient client) {
        this.client = client;
        this.suggestions = new ArrayList<>();
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

        // 오목판을 감싸는 패널
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
        messageArea = new JTextArea(15, 20);
        messageArea.setEditable(false);
        messageArea.setText("[시스템] 게임 화면에 입장했습니다.\n");
        JScrollPane messageScrollPane = new JScrollPane(messageArea);
        messageScrollPane.setBorder(new TitledBorder("게임 메시지"));

        // 제어 버튼
        JPanel controlPanel = new JPanel(new GridLayout(3, 1, 5, 5));

        JButton surrenderButton = new JButton("기권");
        JButton exitButton = new JButton("나가기");

        controlPanel.add(surrenderButton);
        controlPanel.add(exitButton);
        controlPanel.setMaximumSize(new Dimension(250, 70));

        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(turnLabel);
        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(playerInfoPanel);
        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(messageScrollPane);
        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(controlPanel);
        sidePanel.add(Box.createVerticalGlue());

        // --- 최종 조립 ---
        add(boardPanel, BorderLayout.CENTER);
        add(sidePanel, BorderLayout.EAST);


        surrenderButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "기권하시겠습니까?",
                    "기권 확인",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                // 기권기능 넣기
            }
        });

        exitButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "게임을 종료하고 대기실로 돌아가시겠습니까?",
                    "게임 종료",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                client.showView(OmokClient.WAITING_VIEW);
            }
        });
    }

    // 오목판 클릭 처리
    private void handleBoardClick(MouseEvent e) {
        int margin = omokBoard.getMargin();
        int cellSize = omokBoardView.getCellSize();

        // 마진 고려 좌표 계산. 마우스는 픽셀 좌표로 값을 주고, 오목판에선 이 좌표가 아니라 다른 값을 사용
        int x = (e.getX() - margin + 8 + cellSize / 2) / cellSize;
        int y = (e.getY() - margin + cellSize / 2) / cellSize;

        // 유효한 범위 체크
        if (x >= 0 && x < 15 && y >= 0 && y < 15) {
            if (omokBoard.isEmpty(x, y)) {
                if (isSpectator) {
                    // 관전자는 훈수를 둠
                    client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_SUGGEST_MOVE, x, y, 0));
                    appendMessage("[훈수] 당신이 (" + x + ", " + y + ")를 제안했습니다.");
                } else {
                    // 플레이어는 실제로 돌을 둠
                    int color = 1;
                    client.send(new OmokMsg(client.getUid(), OmokMsg.MODE_PLACE_STONE, x, y, color));
                }
            } else {
                appendMessage("[경고] 이미 돌이 놓인 위치입니다.");
            }
        }
    }

    // 돌 놓기 (서버로부터 받은 정보)
    public void placeStone(int x, int y, int color) {
        omokBoard.placeStone(x, y, color);
        // 훈수 제거 (실제 수가 놓여졌으므로)
        clearSuggestions();
    }

    // 관전자 훈수 표시 (반투명으로)
    public void showSuggestion(int x, int y, String spectatorId) {
        suggestions.add(new Point(x, y));
        omokBoard.addSuggestion(x, y);
        appendMessage("[훈수] " + spectatorId + "님이 (" + x + ", " + y + ")를 제안했습니다.");
    }

    private void clearSuggestions() {
        suggestions.clear();
        omokBoard.clearSuggestions();
    }

    public void updateTurn(String message) {
        turnLabel.setText(message);
        appendMessage("[턴] " + message);
    }

    public void setSpectatorMode(boolean isSpectator) {
        this.isSpectator = isSpectator;
        if (isSpectator) {
            appendMessage("[시스템] 관전자 모드입니다. 클릭하면 훈수를 둘 수 있습니다.");
        }
    }

    public void gameOver(String message) {
        appendMessage("[게임 종료] " + message);
        turnLabel.setText("게임 종료");
        // 더 이상 클릭 못하도록
        omokBoard.setEnabled(false);
    }

    // 게임 진행 중 메시지 띄우기용
    public void appendMessage(String message) {
        messageArea.append(message + "\n");
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
    }
}