package omok_client;

import javax.swing.*;
import javax.swing.border.TitledBorder;

import java.awt.*;

public class OmokClient extends JFrame {

    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);

    // 각 화면 패널
    private LobbyPanel lobbyPanel;
    private WaitingRoomPanel waitingRoomPanel;
    private GamePanel gamePanel;

    // 패널
    public static final String LOBBY_VIEW = "Lobby";
    public static final String WAITING_VIEW = "WaitingRoom";
    public static final String GAME_VIEW = "Game";

    public OmokClient() {
        setTitle("같이 둬 1.0");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // --- 패널 생성 ---
        lobbyPanel = new LobbyPanel(this);
        waitingRoomPanel = new WaitingRoomPanel(this);
        gamePanel = new GamePanel(this);

        // --- mainPanel에 패널 추가 ---
        mainPanel.add(lobbyPanel, LOBBY_VIEW);
        mainPanel.add(waitingRoomPanel, WAITING_VIEW);
        mainPanel.add(gamePanel, GAME_VIEW);

        // --- 프레임에 mainPanel 추가 및 설정 ---
        add(mainPanel);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setVisible(true);

        // 시작 화면
        showView(LOBBY_VIEW);
    }

    public void showView(String viewName) {
        cardLayout.show(mainPanel, viewName);
        // 프레임 크기 설정
        if (viewName.equals(GAME_VIEW)) {
            setSize(800, 600);
        } else {
            setSize(400, 500); // 로비/대기실
        }
        setLocationRelativeTo(null); // 크기 변경 후 중앙 재배치
    }

    public static void main(String[] args) {
        new OmokClient();
    }
}

/*
 1. 방 생성/접속 화면 (LobbyPanel)
 - 사용자 목록, 방 목록, 방 만들기 버튼, 입장하기 버튼
*/
class LobbyPanel extends JPanel {
    private OmokClient client;

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
        JTextArea chatArea = new JTextArea(5, 20);
        chatArea.setEditable(false);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setBorder(new TitledBorder("채팅"));

        // 입력 및 버튼
        JPanel inputPanel = new JPanel(new BorderLayout());
        JTextField chatInput = new JTextField();
        JButton sendButton = new JButton("보내기");

        JPanel controlPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        JButton createButton = new JButton("방 만들기");
        JButton joinButton = new JButton("입장하기");
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

        // 방 만들기를 누르면 대기실로 이동 (임시)
        createButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "방 제목을 입력해 주세요.", "입력", JOptionPane.QUESTION_MESSAGE);
            client.showView(OmokClient.WAITING_VIEW);
        });

        // 입장하기를 누르면 대기실로 이동 (임시)
        joinButton.addActionListener(e -> client.showView(OmokClient.WAITING_VIEW));
    }
}

/*
 2. 방 대기 화면 (WaitingRoomPanel)
 - 방 정보, 참가자 목록, 채팅, 게임 시작/나가기 버튼
*/
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
        // 실제 오목판 그려야 함
        JPanel omokBoard = new JPanel();
        omokBoard.setPreferredSize(new Dimension(500, 500));
        omokBoard.setBackground(new Color(222, 184, 135));
        omokBoard.setLayout(new GridBagLayout());
        omokBoard.add(new JLabel("오목판 (15x15 Grid Drawing Area)"));

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