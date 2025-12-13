package omok_server;

import omok_shared.OmokMsg;
import java.util.*;

public class GameRoom {
    private String roomId;
    private String title;
    private Player owner;
    private Vector<Player> players;        // 게임 참여 플레이어 (최대 2명)
    private Vector<Player> spectators;     // 관전자 목록
    private OmokServer server;
    private boolean gameStarted;           // 게임 시작 여부
    private int currentTurn;               // 현재 턴 (0: 흑돌, 1: 백돌)
    private int[][] board;                 // 15x15 오목판 상태
    private GameRecord gameRecord;         // 게임 기록 (복기용)
    private OmokRuleChecker ruleChecker; // 금수 확인

    // 훈수 시스템 관련
    private Map<String, Integer> adviceRequestCount;  // 플레이어별 훈수 요청 횟수
    private static final int MAX_ADVICE_REQUESTS = 5; // 게임당 최대 훈수 요청 횟수
    private String currentAdviceRequester;            // 현재 훈수 요청한 플레이어 ID
    private Set<String> adviceOffers;                 // 훈수 제공 의사를 밝힌 관전자들
    private Map<String, Integer> spectatorColors;     // 관전자별 고유 색상 (훈수 표시용)
    private String selectedAdvisor;                   // 선택된 관전자 ID

    // 관전자 훈수 표시용 색상
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

    // 게임 방 생성자
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
        this.ruleChecker = new OmokRuleChecker();

        // 훈수 시스템 초기화
        this.adviceRequestCount = new HashMap<>();
        this.adviceOffers = new HashSet<>();
        this.spectatorColors = new HashMap<>();
    }

    // 플레이어가 방에 입장
    public synchronized boolean enterPlayer(Player player) {
        // 게임이 시작된 경우 입장 불가
        if (gameStarted) {
            return false;
        }

        // 플레이어가 2명 미만이면 플레이어로 추가
        if (players.size() < 2) {
            players.add(player);
        } else {
            // 2명 이상이면 관전자로 추가
            spectators.add(player);
            assignSpectatorColor(player.getClientHandler().getUid());
            player.getClientHandler().send(
                    new OmokMsg("SERVER", OmokMsg.MODE_WAITING_STRING,
                            "관전자로 입장하였습니다.")
            );
        }
        return true;
    }

    // 관전자에게 고유 색상 할당
    private void assignSpectatorColor(String spectatorId) {
        if (!spectatorColors.containsKey(spectatorId)) {
            // 관전자 수에 따라 차례대로 색상 할당
            int colorIndex = spectatorColors.size() % COLOR.length;
            // RGB를 정수로
            int colorCode = (COLOR[colorIndex][0] << 16) |
                    (COLOR[colorIndex][1] << 8) |
                    COLOR[colorIndex][2];
            spectatorColors.put(spectatorId, colorCode);
        }
    }

    // 플레이어가 방에서 퇴장 (게임 진행 중 퇴장 시 자동 기권)
    public synchronized void exitPlayer(Player player) {
        String playerId = player.getClientHandler().getUid();
        boolean wasPlayer = false;

        // 리스트를 돌면서 ID가 같은지 직접 확인
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (p.getClientHandler().getUid().equals(playerId)) {
                players.remove(i); // 리스트에서 제거
                wasPlayer = true;  // 플레이어가 찾았으니 체크
                break; // 찾았으니 루프 종료
            }
        }

        players.remove(player);
        spectators.remove(player);
        spectatorColors.remove(playerId);

        // 게임 진행 중에 플레이어가 나가면 자동 기권 처리
        if (gameStarted && wasPlayer) {
            // 남은 플레이어를 승자로 설정
            String winnerId = null;
            for (Player p : players) {
                if (!p.getClientHandler().getUid().equals(playerId)) {
                    winnerId = p.getClientHandler().getUid();
                    break;
                }
            }

            if (winnerId != null) {
                gameRecord.endGame(winnerId);
                String message = playerId + "님이 연결이 끊어졌습니다. " + winnerId + "님이 승리했습니다!";
                broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_GAME_OVER, message));

                // 전적 업데이트
                server.updateUserStats(winnerId, true);
                server.updateUserStats(playerId, false);

                // 복기
                int count = gameRecord.getMoveCount();
                broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_RESULT_COUNT, String.valueOf(count)));
                gameStarted = false;
            }
        }

        // 방에 아무도 없으면 방 삭제
        if (players.isEmpty()) {
            server.removeRoom(this);
        } else if (player.equals(owner)) {
            // 방장이 나가면 첫 번째 플레이어를 방장으로 변경
            owner = players.get(0);
        }

        // 관전자 수 변경 알림
        notifySpectatorCount();
    }

    // 게임 시작 처리
    public synchronized void startGame() {
        // 플레이어 2명 미만이면 시작 불가
        if (players.size() < 2) {
            owner.getClientHandler().send(
                    new OmokMsg("SERVER", OmokMsg.MODE_START,
                            "FAILED:플레이어가 2명이 되어야 시작할 수 있습니다.")
            );
            return;
        }

        // 게임 상태 초기화
        gameStarted = true;
        currentTurn = 0;
        board = new int[BOARD_SIZE][BOARD_SIZE];

        // 훈수 요청 횟수 초기화
        adviceRequestCount.clear();
        String player1Id = players.get(0).getClientHandler().getUid();
        String player2Id = players.get(1).getClientHandler().getUid();
        adviceRequestCount.put(player1Id, 0);
        adviceRequestCount.put(player2Id, 0);

        // 게임 기록 시작
        gameRecord = new GameRecord(player1Id, player2Id);

        // 관전자들 설정
        for (Player spectator : spectators) {
            String spectatorId = spectator.getClientHandler().getUid();
            assignSpectatorColor(spectatorId);
            OmokMsg spectatorMsg = new OmokMsg("SERVER", OmokMsg.MODE_WAITING_STRING, "SPECTATOR");
            spectator.getClientHandler().send(spectatorMsg);
        }

        // 모든 참가자에게 게임 시작 알림
        broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_START, "SUCCESS"));

        // 플레이어 정보와 전적 전송
        String player1Stats = server.getUserStatsString(player1Id);
        String player2Stats = server.getUserStatsString(player2Id);
        String playerInfo = player1Id + player1Stats + "|" + player2Id + player2Stats;
        broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_PLAYER_INFO, playerInfo));

        // 관전자 수 알림
        notifySpectatorCount();

        // 첫 턴 알림
        broadcastTurn();

        // 로비에 방 목록 갱신 (게임중 상태 표시)
        server.broadcastRoomListToAll();
    }

    // 플레이어가 훈수 요청
    public synchronized void requestAdvice(String playerId) {
        if (!gameStarted) return;

        // 현재 턴 플레이어인지 확인
        Player currentPlayer = players.get(currentTurn);
        if (!currentPlayer.getClientHandler().getUid().equals(playerId)) {
            // 요청한 플레이어에게 거절 메시지 전송
            Player requester = findPlayerByUid(playerId);
            if (requester != null) {
                requester.getClientHandler().send(
                        new OmokMsg("SERVER", OmokMsg.MODE_GAME_CHAT,
                                "자신의 차례일 때만 훈수를 요청할 수 있습니다.")
                );
            }
            return;
        }

        // 훈수 요청 횟수 초과 확인
        int requestCount = adviceRequestCount.getOrDefault(playerId, 0);
        if (requestCount >= MAX_ADVICE_REQUESTS) {
            currentPlayer.getClientHandler().send(
                    new OmokMsg("SERVER", OmokMsg.MODE_ADVICE_LIMIT_EXCEEDED,
                            "훈수 요청 횟수를 모두 사용했습니다. (최대 " + MAX_ADVICE_REQUESTS + "회)")
            );
            return;
        }

        // 관전자 존재 여부 확인
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

        // 모든 관전자에게 훈수 요청 알림
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

    // 관전자가 훈수 제공 의사 표시
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

        // 제공 의사 목록에 추가
        adviceOffers.add(spectatorId);

        // 요청자에게 업데이트된 목록 전송
        sendAdviceOffersList();
    }

    // 훈수 제공 의사를 밝힌 관전자 목록을 요청자에게 전송
    private void sendAdviceOffersList() {
        if (currentAdviceRequester == null) return;

        // 관전자 ID를 쉼표로 구분하여 문자열 생성
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

    // 플레이어가 훈수해줄 관전자 선택
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

    // 관전자의 훈수 처리  (선택된 관전자만 훈수 가능)
    public synchronized void handleSuggestion(String spectatorId, int x, int y) {
        if (!gameStarted) return;

        // 선택된 관전자인지 확인
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

        // 현재 턴 플레이어에게 훈수 전송
        Player currentPlayer = players.get(currentTurn);
        int adviceColor = spectatorColors.getOrDefault(spectatorId, 0xFF0000);
        gameRecord.addSpectatorSuggestion(spectatorId, x, y, adviceColor);

        OmokMsg suggestionMsg = new OmokMsg(spectatorId, OmokMsg.MODE_SUGGESTION_RECEIVED, x, y, 3);
        suggestionMsg.setAdviceColor(adviceColor);
        currentPlayer.getClientHandler().send(suggestionMsg);
    }

    // 플레이어가 돌을 놓음  return으로 성공 여부
    public synchronized boolean placeStone(String playerId, int x, int y) {
        if (!gameStarted) return false;

        // 현재 턴 플레이어인지 확인
        Player currentPlayer = players.get(currentTurn);
        if (!currentPlayer.getClientHandler().getUid().equals(playerId)) {
            return false;
        }

        // 유효한 위치인지 확인
        if (x < 0 || x >= BOARD_SIZE || y < 0 || y >= BOARD_SIZE || board[y][x] != 0) {
            return false;
        }

        // 돌 놓기
        int color = (currentTurn == 0) ? BLACK : WHITE;
        board[y][x] = color;
        if (color == BLACK) {
            if (ruleChecker.isForbiddenMove(board, x, y)) {
                // 금수 발견
                OmokMsg forbiddenStoneMsg = new OmokMsg(playerId, OmokMsg.MODE_STONE_PLACED, x, y, color);
                broadcastGameRoom(forbiddenStoneMsg);

                gameRecord.addPlayerMove(playerId, x, y);

                // 상대방 승리 처리
                String loserId = playerId;
                String winnerId = players.get((currentTurn + 1) % 2).getClientHandler().getUid();

                gameRecord.endGame(winnerId);
                broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_GAME_OVER,
                        loserId + "님이 금수(반칙)를 두어 패배했습니다. " + winnerId + "님 승리!"));

                server.updateUserStats(winnerId, true);
                server.updateUserStats(loserId, false);

                int count = gameRecord.getMoveCount();
                broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_RESULT_COUNT, String.valueOf(count)));
                gameStarted = false;

                return true; // 게임이 종료되었으므로 true 반환
            }
        }
        gameRecord.addPlayerMove(playerId, x, y);

        // 훈수 절차 종료
        currentAdviceRequester = null;
        selectedAdvisor = null;
        adviceOffers.clear();

        // 모든 참가자에게 돌이 놓인 위치 전송
        OmokMsg stonePlacedMsg = new OmokMsg(playerId, OmokMsg.MODE_STONE_PLACED, x, y, color);
        broadcastGameRoom(stonePlacedMsg);

        // 승리 조건 확인
        if (checkWin(x, y, color)) {
            gameRecord.endGame(playerId);
            broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_GAME_OVER,
                    playerId + "님이 승리하였습니다!"));

            // 전적 업데이트
            String loserId = players.get((currentTurn + 1) % 2).getClientHandler().getUid();
            server.updateUserStats(playerId, true);
            server.updateUserStats(loserId, false);

            // 복기를 위한 전송
            int count = gameRecord.getMoveCount();
            broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_RESULT_COUNT, String.valueOf(count)));
            gameStarted = false;
            return true;
        }

        // 턴 변경
        currentTurn = (currentTurn + 1) % 2;
        broadcastTurn();
        return true;
    }

    // 플레이어 기권 처리
    public synchronized void handleSurrender(String playerId) {
        if (!gameStarted) return;

        Player surrenderer = findPlayerByUid(playerId);
        if (surrenderer == null) return;

        // 상대방을 승자로 설정
        String winnerId = null;
        for (Player p : players) {
            if (!p.getClientHandler().getUid().equals(playerId)) {
                winnerId = p.getClientHandler().getUid();
                break;
            }
        }

        if (winnerId != null) {
            gameRecord.endGame(winnerId);
            String message = playerId + "님이 기권했습니다. " + winnerId + "님이 승리했습니다!";
            broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_GAME_OVER, message));

            // 전적 업데이트
            server.updateUserStats(winnerId, true);
            server.updateUserStats(playerId, false);

            // 복기를 위한 전송
            int count = gameRecord.getMoveCount();
            broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_RESULT_COUNT, String.valueOf(count)));
            gameStarted = false;
        }
    }

    // UID로 플레이어 찾기
    private Player findPlayerByUid(String uid) {
        for (Player p : players) {
            if (p.getClientHandler().getUid().equals(uid)) return p;
        }
        return null;
    }

    // UID로 관전자 찾기
    private Player findSpectatorByUid(String uid) {
        for (Player s : spectators) {
            if (s.getClientHandler().getUid().equals(uid)) return s;
        }
        return null;
    }

    // 승리 조건 확인 (5개 연속)
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

    // 현재 턴 정보를 모든 참가자에게 전송
    private void broadcastTurn() {
        String currentPlayerId = players.get(currentTurn).getClientHandler().getUid();
        String colorStr = (currentTurn == 0) ? "흑돌" : "백돌";
        String turnMsg = currentPlayerId + "님(" + colorStr + ")의 차례입니다.";
        broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_TURN_CHANGED, turnMsg));
    }

    // 방의 모든 참가자(플레이어 + 관전자)에게 메시지 전송
    public void broadcastGameRoom(OmokMsg msg) {
        for (Player player : players) {
            player.getClientHandler().send(msg);
        }
        for (Player spectator : spectators) {
            spectator.getClientHandler().send(msg);
        }
    }

    // 클라이언트에 표시할 참가자 목록 문자열 생성

    public String getPlayersForClient() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(players.get(i).getClientHandler().getUid());
            sb.append(" [플레이어]");
        }
        if (!spectators.isEmpty()) {
            sb.append(",");
            for (int i = 0; i < spectators.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(spectators.get(i).getClientHandler().getUid());
                sb.append(" [관전자]");
            }
        }
        return sb.toString();
    }

    // 플레이어들에게 관전자 수 알림. 관전자가 있어야 훈수 요청 가능

    private void notifySpectatorCount() {
        if (gameStarted) {
            OmokMsg countMsg = new OmokMsg("SERVER", OmokMsg.MODE_SPECTATOR_COUNT,
                    String.valueOf(spectators.size()));
            for (Player player : players) {
                player.getClientHandler().send(countMsg);
            }
        }
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