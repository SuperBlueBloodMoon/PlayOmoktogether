package omok_server;

import omok_shared.MoveRecord;
import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;
import java.util.List;

// 플레이어의 수와 관전자의 훈수를 기록. 큐에 넣었으니, 넣은대로 리스트에 넣고 빼내면 1번수부터 나올 것
public class GameRecord {
    private Queue<MoveRecord> moveQueue;     // 순서대로 기록된 모든 수
    private String player1Id;                 // 흑돌 플레이어
    private String player2Id;                 // 백돌 플레이어
    private String winner;                    // 승자
    private int currentMoveNumber;            // 현재 수 번호

    public GameRecord(String player1Id, String player2Id) {
        this.moveQueue = new LinkedList<>();
        this.player1Id = player1Id;
        this.player2Id = player2Id;
        this.currentMoveNumber = 0;
    }

    //플레이어의 수 기록
    public void addPlayerMove(String playerId, int x, int y) {
        currentMoveNumber++;
        MoveRecord move = new MoveRecord(playerId, x, y, false);
        moveQueue.offer(move);
    }

    //관전자의 훈수 기록
    public void addSpectatorSuggestion(String spectatorId, int x, int y) {
        MoveRecord suggestion = new MoveRecord(spectatorId, x, y, true);
        moveQueue.offer(suggestion);
    }

    //게임 종료
    public void endGame(String winner) {
        this.winner = winner;
    }

    //전체 기록 반환 (복기용) - 첫번째부터 꺼내면서 전달하기. 마지막 요소가 false면 플레이어 수, true면 훈수.
    public List<MoveRecord> getAllMoves() {
        return new ArrayList<>(moveQueue);
    }

    //잡다한 getters
    public String getPlayer1Id() { return player1Id; }
    public String getPlayer2Id() { return player2Id; }
    public String getWinner() { return winner; }
    public int getTotalMoves() { return currentMoveNumber; }
    public int getMoveCount() { return moveQueue.size(); }
}