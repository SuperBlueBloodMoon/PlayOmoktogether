package omok_client;

import omok_client.view.omokBoardView;
import omok_shared.OmokMsg;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.Socket;

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

        // 시작 화면
        showView(LOGIN_VIEW);
    }

    public void showView(String viewName) {
        cardLayout.show(mainPanel, viewName);
        // 프레임 크기 설정
        if (viewName.equals(GAME_VIEW)) {
            setSize(800, 600);
        } else if(viewName.equals(LOGIN_VIEW)) {
            setSize(400, 100);
        }
        else {
            setSize(400, 500); // 로비/대기실
        }
        setLocationRelativeTo(null); // 크기 변경 후 중앙 재배치
    }

    public void connectToServer(String userID) {
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            sendUserID(userID);
            showView(LOBBY_VIEW);
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
                            case OmokMsg.MODE_LOBBY_STRING :
                                lobbyDisplay(msg.getUserID() + ": " + msg.getMessage());
                                break;
                            case  OmokMsg.MODE_LOBBY_IMAGE :
                                lobbyDisplay(msg.getUserID() + ": " + msg.getMessage());
                                //printDisplay(msg.getImage());
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

        int len = lobbyPanel.getChatArea().getDocument().getLength(); // 모든 글자수, 개행 문자 포함

        try {
            lobbyPanel.getDocuments().insertString(len, message + "\n", null); // 가장 끝에 메세지 삽입
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
        lobbyPanel.getChatArea().setCaretPosition(len); // 커서의 위치를 글자를 추가하기 시작한 위치로 이동
    }
    public String getUid() {
        return uid;
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

    public LobbyPanel(OmokClient client) {
        this.client = client;
        setLayout(new BorderLayout(10, 10)); // 전체 레이아웃

        // --- 상단 (정보 및 버튼) ---
        JPanel infoPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        // 사용자 목록
        JList<String> userList = new JList<>(new String[]{"강동명", "사용자12", "강강동동명명"});
        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setBorder(new TitledBorder("접속자"));

        // 방 목록
        JList<String> roomList = new JList<>(new String[]{"방", "방바방방", "방방비방방"});
        JScrollPane roomScrollPane = new JScrollPane(roomList);
        roomScrollPane.setBorder(new TitledBorder("방 목록"));

        infoPanel.add(userScrollPane);
        infoPanel.add(roomScrollPane);

        // --- 하단 (채팅 및 버튼) ---
        JPanel chatAndControlPanel = new JPanel(new BorderLayout());

        // 채팅 영역
        document = new DefaultStyledDocument();
        chatArea = new JTextPane(document);
        chatArea.setEditable(true);
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

        // 입장하기를 누르면 대기실로 이동 (임시)
        joinButton.addActionListener(e -> client.showView(OmokClient.WAITING_VIEW));
    }
    public JTextPane getChatArea() {
        return chatArea;
    }
    public DefaultStyledDocument getDocuments() {
        return document;
    }
}

/*
 2. 방 대기 화면 (WaitingRoomPanel)
 - 방 정보, 참가자 목록, 채팅, 게임 시작/나가기 버튼
*/

// 여기서 Player 생성
class WaitingRoomPanel extends JPanel {
    private OmokClient client;

    public WaitingRoomPanel(OmokClient client) {
        this.client = client;
        setLayout(new BorderLayout(10, 10));

        // --- 상단 (방 정보 및 참가자) ---
        JPanel topPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        // 방 정보/게임 설정
        JTextArea roomInfo = new JTextArea("방 제목: Test Room\n방장: 강동명\n규칙: 룰룰룰");
        roomInfo.setEditable(false);
        JScrollPane roomInfoPane = new JScrollPane(roomInfo);
        roomInfoPane.setBorder(new TitledBorder("방 정보 / 설정"));

        // 참가자 목록
        JList<String> playerList = new JList<>(new String[]{"강동명 (방장/흑)", "강강동동명명 (백)"});
        JScrollPane playerScrollPane = new JScrollPane(playerList);
        playerScrollPane.setBorder(new TitledBorder("참가자"));

        topPanel.add(roomInfoPane);
        topPanel.add(playerScrollPane);

        // --- 중간 (채팅) ---
        JTextArea chatArea = new JTextArea(10, 20);
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
        startButton.addActionListener(e -> {
            // 시작버튼을 누가 누를지, 같은 처리 필요-> 방장만 시작가능 같은거
            client.showView(OmokClient.GAME_VIEW);
        });

        // 나가기 버튼 (임시)
        exitButton.addActionListener(e -> client.showView(OmokClient.LOBBY_VIEW));
    }
}

/*
  3. 오목 게임 화면 (GamePanel)
  - 오목판, 게임 정보, 채팅/대화
*/
class GamePanel extends JPanel {
    private OmokClient client;

    public GamePanel(OmokClient client) {
        this.client = client;
        setLayout(new BorderLayout()); // 메인 레이아웃

        // --- 오목판 영역 (중앙) ---
        omokBoardView omokBoard = new omokBoardView();
        omokBoard.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int x = e.getX() / omokBoardView.getCellSize();
                int y = e.getY() / omokBoardView.getCellSize();
                // 객체 스트림으로 x, y, index, color 값 넘기기
                // 서버에서 컨트롤(어느 Player가 돌을 둘지)
                //
            }
        });

        // --- 사이드 패널 (동쪽) ---
        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new BoxLayout(sidePanel, BoxLayout.Y_AXIS));
        sidePanel.setPreferredSize(new Dimension(250, 0));

        // 플레이어 정보
        JPanel playerInfoPanel = new JPanel(new GridLayout(1, 2));
        playerInfoPanel.add(new JLabel(" (Player 1)"));
        playerInfoPanel.add(new JLabel(" (Player 2)"));
        playerInfoPanel.setBorder(new TitledBorder("현재 차례/정보"));

        // 게임 메시지/대화
        JTextArea messageArea = new JTextArea(10, 20);
        messageArea.setEditable(false);
        messageArea.setText("[알림] 게임 시작!\n[알림] 흑돌의 차례입니다.");
        JScrollPane messageScrollPane = new JScrollPane(messageArea);
        messageScrollPane.setBorder(new TitledBorder("메시지 / 대화"));

        // 제어 버튼
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton exitButton = new JButton("나가기");
        JButton surrenderButton = new JButton("기권");
        controlPanel.add(exitButton);
        controlPanel.add(surrenderButton);

        sidePanel.add(playerInfoPanel);
        sidePanel.add(messageScrollPane);
        sidePanel.add(Box.createVerticalStrut(10));
        sidePanel.add(controlPanel);

        // --- 최종 조립 ---
        add(omokBoard, BorderLayout.CENTER);
        add(sidePanel, BorderLayout.EAST);

        // 나가기 버튼 (임시)
        exitButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "게임을 종료하고 로비로 돌아가시겠습니까?", "게임 종료", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                client.showView(OmokClient.LOBBY_VIEW);
            }
        });
    }
}