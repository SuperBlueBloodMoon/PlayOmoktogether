package omok_server;

import omok_shared.OmokMsg;

import java.util.UUID;
import java.util.Vector;

public class GameRoom {
    private String roomId;
    private String title;
    private Player owner;
    private Vector<Player> players;
    private Vector<Player> spectators;
    private OmokServer server;
    private boolean gameStarted;
    private int currentTurn;  // 0: Player A (흑돌), 1: Player B (백돌)
    private int[][] board;

    //복기 기록용
    private GameRecord gameRecord;

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
    }

    // syncronized 쓴 이유는, 양쪽에서 동시에 변경할 수도 있어서. 아래 함수들도 동일.
    public synchronized void enterPlayer(Player player) {
        if (players.size() < 2) {
            players.add(player);
        } else {
            spectators.add(player);
            player.getClientHandler().send(
                    new OmokMsg("SERVER", OmokMsg.MODE_WAITING_STRING,
                            "관전자로 입장하였습니다. 클릭하면 현재 플레이어에게 훈수를 둘 수 있습니다.")
            );
        }
    }

    public synchronized void exitPlayer(Player player) {
        players.remove(player);
        spectators.remove(player);

        if (players.isEmpty()) {
            server.removeRoom(this);
        } else if (player.equals(owner)) {
            owner = players.get(0);
        }
    }

    public synchronized void startGame() {
        if (players.size() != 2) {
            owner.getClientHandler().send(
                    new OmokMsg("SERVER", OmokMsg.MODE_START,
                            "플레이어가 2명이 되어야 시작할 수 있습니다.")
            );
            return;
        }

        gameStarted = true;
        currentTurn = 0;
        board = new int[BOARD_SIZE][BOARD_SIZE];

        //게임레코드 초기화. 두판 연속으로 하거나 그러면 꼬일수도 있으니.
        String player1Id = players.get(0).getClientHandler().getUid();
        String player2Id = players.get(1).getClientHandler().getUid();
        gameRecord = new GameRecord(player1Id, player2Id);

        // 관전자들에게 알림
        for (Player spectator : spectators) {
            OmokMsg spectatorMsg = new OmokMsg("SERVER", OmokMsg.MODE_WAITING_STRING, "SPECTATOR");
            spectator.getClientHandler().send(spectatorMsg);
        }

        broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_START, "SUCCESS"));

        // 첫 턴 알림
        broadcastTurn();
    }

    // 돌 놓기 처리
    public synchronized boolean placeStone(String playerId, int x, int y) {
        if (!gameStarted) {
            return false;
        }

        // 차례 확인
        Player currentPlayer = players.get(currentTurn);
        if (!currentPlayer.getClientHandler().getUid().equals(playerId)) {
            return false;
        }

        // 빈 자리 확인
        if (x < 0 || x >= BOARD_SIZE || y < 0 || y >= BOARD_SIZE || board[y][x] != 0) {
            return false;
        }

        // 돌 놓기
        int color = (currentTurn == 0) ? BLACK : WHITE;
        board[y][x] = color;

        //여기서, playerId, x, y 로 큐에다 넣으면 될듯
        //각 수 기록
        gameRecord.addPlayerMove(playerId, x, y);

        // 모든 플레이어와 관전자에게 알림
        OmokMsg stonePlacedMsg = new OmokMsg(playerId, OmokMsg.MODE_STONE_PLACED, x, y, color);
        broadcastGameRoom(stonePlacedMsg);

        // 승리 체크
        if (checkWin(x, y, color)) {
            //게임종료 기록
            gameRecord.endGame(playerId);
            broadcastGameRoom(new OmokMsg("SERVER", OmokMsg.MODE_GAME_OVER,
                    playerId + "님이 승리하였습니다!"));
            gameStarted = false;
            return true;
        }

        // 턴 변경
        currentTurn = (currentTurn + 1) % 2;
        broadcastTurn();

        return true;
    }

    // 관전자 훈수(제안) 처리
    public synchronized void handleSuggestion(String spectatorId, int x, int y) {
        if (!gameStarted) {
            return;
        }

        // 관전자인지 확인
        boolean isSpectator = false;
        for (Player spec : spectators) {
            if (spec.getClientHandler().getUid().equals(spectatorId)) {
                isSpectator = true;
                break;
            }
        }

        if (!isSpectator) {
            return;
        }

        //훈수 기록
        gameRecord.addSpectatorSuggestion(spectatorId, x, y);

        // 현재 플레이어에게만 훈수 전송
        Player currentPlayer = players.get(currentTurn);
        int suggestionColor = 3;
        OmokMsg suggestionMsg = new OmokMsg(spectatorId, OmokMsg.MODE_SUGGESTION_RECEIVED, x, y, suggestionColor);
        currentPlayer.getClientHandler().send(suggestionMsg);
    }

    //승리 체크 (오목 판정)
    private boolean checkWin(int x, int y, int color) {
        // 8방향 체크: 가로, 세로, 대각선 2개
        int[][] directions = {{1,0}, {0,1}, {1,1}, {1,-1}};

        for (int[] dir : directions) {
            int count = 1;

            // 양방향 체크
            count += countStones(x, y, dir[0], dir[1], color);
            count += countStones(x, y, -dir[0], -dir[1], color);

            if (count >= 5) {
                return true;
            }
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

    public String getRoomId() { return roomId; }
    public String getTitle() { return title; }
    public Player getOwner() { return owner; }
    public int getPlayerCount() { return players.size(); }
    public boolean isGameStarted() { return gameStarted; }
}