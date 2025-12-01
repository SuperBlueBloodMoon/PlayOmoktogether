package omok_server;

import omok_shared.OmokMsg;
import java.util.*;

public class GameRoom {
    private String roomId;
    private String title;
    private Player owner;
    private Vector<Player> players;
    private Vector<Player> spectators;
    private OmokServer server;
    private boolean gameStarted;
    private int currentTurn;
    private int[][] board;
    private GameRecord gameRecord;

    // 훈수 시스템 필드
    private Map<String, Integer> adviceRequestCount;  // 플레이어별 훈수 요청 횟수
    private static final int MAX_ADVICE_REQUESTS = 3; // 게임당 최대 훈수 요청 횟수
    private String currentAdviceRequester;             // 현재 훈수 요청한 플레이어
    private Set<String> adviceOffers;                  // 훈수 제공 의사 표시한 관전자들
    private Map<String, Integer> spectatorColors;      // 관전자별 훈수 색상
    private String selectedAdvisor;                    // 선택된 관전자

    // 관전자 훈수 색상 팔레트 (RGB)
    private static final int[][] COLOR = {
            {255, 0, 0},      // 빨강
            {0, 0, 255},      // 파랑
            {0, 200, 0},      // 초록
            {255, 165, 0},    // 주황
            {138, 43, 226},   // 보라
            {255, 20, 147},   // 핑크
            {0, 255, 255},    // 시안
            {255, 255, 0}     // 노랑
    };

    private static final int BOARD_SIZE = 15;
    private static final int BLACK = 1;
    private static final int WHITE = 2;

    public GameRoom(String title, Player owner, OmokServer server) {
        this.roomId = UUID.randomUUID().toString();
        this.title = title;
        this.owner = owner;
        this.server = server;
        this.players = new Vector<>();
        this.spectators = new Vector<>();
        this.players.add(owner);
        this.gameStarted = false;
        this.currentTurn = 0;
        this.board = new int[BOARD_SIZE][BOARD_SIZE];

        // 훈수 시스템 초기화
        this.adviceRequestCount = new HashMap<>();
        this.adviceOffers = new HashSet<>();
        this.spectatorColors = new HashMap<>();
    }

    public synchronized void enterPlayer(Player player) {
        if (gameStarted) {
            spectators.add(player);
            assignSpectatorColor(player.getClientHandler().getUid());
            player.getClientHandler().send(
                    new OmokMsg("SERVER", OmokMsg.MODE_WAITING_STRING,
                            "게임이 진행 중입니다. 관전자로 입장하였습니다.")
            );
        } else if (players.size() < 2) {
            players.add(player);
        } else {
            spectators.add(player);
            assignSpectatorColor(player.getClientHandler().getUid());
            player.getClientHandler().send(
                    new OmokMsg("SERVER", OmokMsg.MODE_WAITING_STRING,
                            "관전자로 입장하였습니다.")
            );
        }
    }

    // 관전자에게 고유 색상 할당
    private void assignSpectatorColor(String spectatorId) {
        if (!spectatorColors.containsKey(spectatorId)) {
            int colorIndex = spectatorColors.size() % COLOR.length;
            int colorCode = (COLOR[colorIndex][0] << 16) |
                    (COLOR[colorIndex][1] << 8) |
                    COLOR[colorIndex][2];
            spectatorColors.put(spectatorId, colorCode);
        }
    }

    public synchronized void exitPlayer(Player player) {
        players.remove(player);
        spectators.remove(player);
        spectatorColors.remove(player.getClientHandler().getUid());

        if (players.isEmpty()) {
            server.removeRoom(this);
        } else if (player.equals(owner)) {
            owner = players.get(0);
        }
    }

    public synchronized void startGame() {
        if (players.size() < 2) {
            owner.getClientHandler().send(
                    new OmokMsg("SERVER", OmokMsg.MODE_START,
                            "FAILED:플레이어가 2명이 되어야 시작할 수 있습니다.")
            );
            return;
        }

        gameStarted = true;
        currentTurn = 0;
        board = new int[BOARD_SIZE][BOARD_SIZE];

        // 훈수 카운터 초기화
        adviceRequestCount.clear();
        String player1Id = players.get(0).getClientHandler().getUid();
        String player2Id = players.get(1).getClientHandler().getUid();
        adviceRequestCount.put(player1Id, 0);
        adviceRequestCount.put(player2Id, 0);

        gameRecord = new GameRecord(player1Id, player2Id);

        // 관전자들에게 색상 할당 및 관전자 모드 알림
        for (Player spectator : spectators) {
            String spectatorId = spectator.getClientHandler().getUid();
            assignSpectatorColor(spectatorId);
            OmokMsg spectatorMsg = new OmokMsg("SERVER", OmokMsg.MODE_WAITING_STRING, "SPECTATOR");
            spectator.getClientHandler().send(spectatorMsg);
        }

        broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_START, "SUCCESS"));
        broadcastTurn();
    }

    // 훈수 요청 처리
    public synchronized void requestAdvice(String playerId) {
        if (!gameStarted) return;

        // 현재 턴 플레이어인지 확인
        Player currentPlayer = players.get(currentTurn);
        if (!currentPlayer.getClientHandler().getUid().equals(playerId)) {
            currentPlayer.getClientHandler().send(
                    new OmokMsg("SERVER", OmokMsg.MODE_GAME_CHAT,
                            "자신의 차례일 때만 훈수를 요청할 수 있습니다.")
            );
            return;
        }

        // 훈수 요청 횟수 확인
        int requestCount = adviceRequestCount.getOrDefault(playerId, 0);
        if (requestCount >= MAX_ADVICE_REQUESTS) {
            currentPlayer.getClientHandler().send(
                    new OmokMsg("SERVER", OmokMsg.MODE_ADVICE_LIMIT_EXCEEDED,
                            "훈수 요청 횟수를 모두 사용했습니다. (최대 " + MAX_ADVICE_REQUESTS + "회)")
            );
            return;
        }

        // 관전자가 있는지 확인
        if (spectators.isEmpty()) {
            currentPlayer.getClientHandler().send(
                    new OmokMsg("SERVER", OmokMsg.MODE_GAME_CHAT,
                            "현재 관전자가 없습니다.")
            );
            return;
        }

        // 훈수 요청 상태 저장
        currentAdviceRequester = playerId;
        adviceOffers.clear();
        selectedAdvisor = null;

        // 관전자들에게 훈수 요청 브로드캐스트
        OmokMsg adviceRequest = new OmokMsg("SERVER", OmokMsg.MODE_ADVICE_REQUEST_BROADCAST,
                playerId + "님이 훈수를 요청했습니다. 훈수를 제공하시겠습니까?");
        for (Player spectator : spectators) {
            spectator.getClientHandler().send(adviceRequest);
        }

        // 요청자에게 대기 메시지
        currentPlayer.getClientHandler().send(
                new OmokMsg("SERVER", OmokMsg.MODE_GAME_CHAT,
                        "관전자들의 응답을 기다리는 중입니다... (남은 횟수: " +
                                (MAX_ADVICE_REQUESTS - requestCount - 1) + "회)")
        );
    }

    // 관전자의 훈수 제공 의사 표시
    public synchronized void offerAdvice(String spectatorId) {
        if (currentAdviceRequester == null) return;

        // 관전자인지 확인
        boolean isSpectator = false;
        for (Player spec : spectators) {
            if (spec.getClientHandler().getUid().equals(spectatorId)) {
                isSpectator = true;
                break;
            }
        }
        if (!isSpectator) return;

        adviceOffers.add(spectatorId);

        // 요청자에게 업데이트된 목록 전송
        sendAdviceOffersList();
    }

    // 훈수 제공자 목록 전송
    private void sendAdviceOffersList() {
        if (currentAdviceRequester == null) return;

        StringBuilder offersList = new StringBuilder();
        for (String advisorId : adviceOffers) {
            if (offersList.length() > 0) offersList.append(",");
            offersList.append(advisorId);
        }

        Player requester = findPlayerByUid(currentAdviceRequester);
        if (requester != null) {
            requester.getClientHandler().send(
                    new OmokMsg("SERVER", OmokMsg.MODE_ADVICE_OFFERS_LIST, offersList.toString())
            );
        }
    }

    // 플레이어가 관전자 선택
    public synchronized void selectAdvisor(String playerId, String advisorId) {
        if (!playerId.equals(currentAdviceRequester)) return;
        if (!adviceOffers.contains(advisorId)) return;

        selectedAdvisor = advisorId;

        // 훈수 요청 횟수 증가
        adviceRequestCount.put(playerId, adviceRequestCount.get(playerId) + 1);

        // 선택된 관전자에게 알림
        Player advisor = findSpectatorByUid(advisorId);
        if (advisor != null) {
            OmokMsg selectedMsg = new OmokMsg("SERVER", OmokMsg.MODE_ADVICE_SELECTED,
                    playerId + "님이 당신의 훈수를 선택했습니다. 이제 훈수를 둘 수 있습니다.");
            selectedMsg.setAdvisorId(advisorId);
            advisor.getClientHandler().send(selectedMsg);
        }

        // 요청자에게 알림
        Player requester = findPlayerByUid(playerId);
        if (requester != null) {
            OmokMsg requesterMsg = new OmokMsg("SERVER", OmokMsg.MODE_ADVICE_SELECTED,
                    advisorId + "님의 훈수를 받습니다.");
            requesterMsg.setAdvisorId(advisorId);
            requester.getClientHandler().send(requesterMsg);
        }

        // 선택되지 않은 관전자들에게 알림
        for (String offerId : adviceOffers) {
            if (!offerId.equals(advisorId)) {
                Player otherAdvisor = findSpectatorByUid(offerId);
                if (otherAdvisor != null) {
                    otherAdvisor.getClientHandler().send(
                            new OmokMsg("SERVER", OmokMsg.MODE_WAITING_STRING,
                                    "다른 관전자가 선택되었습니다.")
                    );
                }
            }
        }
    }

    // 훈수 처리
    public synchronized void handleSuggestion(String spectatorId, int x, int y) {
        if (!gameStarted) {
            return;
        }

        // 선택된 관전자만 훈수 가능
        if (selectedAdvisor == null) {
            Player spec = findSpectatorByUid(spectatorId);
            if (spec != null) {
                spec.getClientHandler().send(
                        new OmokMsg("SERVER", OmokMsg.MODE_GAME_CHAT,
                                "현재 훈수가 선택되지 않았습니다.")
                );
            }
            return;
        }

        if (!selectedAdvisor.equals(spectatorId)) {
            Player spec = findSpectatorByUid(spectatorId);
            if (spec != null) {
                spec.getClientHandler().send(
                        new OmokMsg("SERVER", OmokMsg.MODE_GAME_CHAT,
                                "당신이 선택되지 않았습니다. 선택된 관전자: " + selectedAdvisor)
                );
            }
            return;
        }

        gameRecord.addSpectatorSuggestion(spectatorId, x, y);

        Player currentPlayer = players.get(currentTurn);
        int adviceColor = spectatorColors.getOrDefault(spectatorId, 0xFF0000); // 기본 빨강

        OmokMsg suggestionMsg = new OmokMsg(spectatorId, OmokMsg.MODE_SUGGESTION_RECEIVED, x, y, 3);
        suggestionMsg.setAdviceColor(adviceColor);
        currentPlayer.getClientHandler().send(suggestionMsg);
    }

    public synchronized boolean placeStone(String playerId, int x, int y) {
        if (!gameStarted) return false;

        Player currentPlayer = players.get(currentTurn);
        if (!currentPlayer.getClientHandler().getUid().equals(playerId)) {
            return false;
        }

        if (x < 0 || x >= BOARD_SIZE || y < 0 || y >= BOARD_SIZE || board[y][x] != 0) {
            return false;
        }

        int color = (currentTurn == 0) ? BLACK : WHITE;
        board[y][x] = color;
        gameRecord.addPlayerMove(playerId, x, y);

        // 돌을 놓으면 훈수 세션 종료
        currentAdviceRequester = null;
        selectedAdvisor = null;
        adviceOffers.clear();

        OmokMsg stonePlacedMsg = new OmokMsg(playerId, OmokMsg.MODE_STONE_PLACED, x, y, color);
        broadcastGameRoom(stonePlacedMsg);

        if (checkWin(x, y, color)) {
            gameRecord.endGame(playerId);
            broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_GAME_OVER,
                    playerId + "님이 승리하였습니다!"));
            int count = gameRecord.getMoveCount();
            broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_RESULT_COUNT, String.valueOf(count)));
            gameStarted = false;
            return true;
        }

        currentTurn = (currentTurn + 1) % 2;
        broadcastTurn();
        return true;
    }

    private Player findPlayerByUid(String uid) {
        for (Player p : players) {
            if (p.getClientHandler().getUid().equals(uid)) return p;
        }
        return null;
    }

    private Player findSpectatorByUid(String uid) {
        for (Player s : spectators) {
            if (s.getClientHandler().getUid().equals(uid)) return s;
        }
        return null;
    }

    private boolean checkWin(int x, int y, int color) {
        int[][] directions = {{1,0}, {0,1}, {1,1}, {1,-1}};
        for (int[] dir : directions) {
            int count = 1;
            count += countStones(x, y, dir[0], dir[1], color);
            count += countStones(x, y, -dir[0], -dir[1], color);
            if (count >= 5) return true;
        }
        return false;
    }

    private int countStones(int x, int y, int dx, int dy, int color) {
        int count = 0;
        int nx = x + dx;
        int ny = y + dy;
        while (nx >= 0 && nx < BOARD_SIZE && ny >= 0 && ny < BOARD_SIZE &&
                board[ny][nx] == color) {
            count++;
            nx += dx;
            ny += dy;
        }
        return count;
    }

    private void broadcastTurn() {
        String currentPlayerId = players.get(currentTurn).getClientHandler().getUid();
        String colorStr = (currentTurn == 0) ? "흑돌" : "백돌";
        String turnMsg = currentPlayerId + "님(" + colorStr + ")의 차례입니다.";
        broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_TURN_CHANGED, turnMsg));
    }

    public void broadcastGameRoom(OmokMsg msg) {
        for (Player player : players) {
            player.getClientHandler().send(msg);
        }
        for (Player spectator : spectators) {
            spectator.getClientHandler().send(msg);
        }
    }

    public String getPlayersForClient() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(players.get(i).getClientHandler().getUid());
        }
        return sb.toString();
    }

    public String getRoomStatus() {
        return gameStarted ? "게임중" : "대기중";
    }

    public String getRoomId() { return roomId; }
    public String getTitle() { return title; }
    public Player getOwner() { return owner; }
    public int getPlayerCount() { return players.size(); }
    public boolean isGameStarted() { return gameStarted; }
    public GameRecord getGameRecord() { return gameRecord; }
}