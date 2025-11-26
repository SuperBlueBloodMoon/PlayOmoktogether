package omok_shared;

import java.io.Serializable;

//서버에서 클라이언트한테 한 수 한 수를 보내야 클라이언트에서 이 객체를 받고 그려낼 수 있을 것.
public class MoveRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private String playerId;
    private int x;
    private int y;
    private boolean isSpectator;

    public MoveRecord(String playerId, int x, int y, boolean isSpectator) {
        this.playerId = playerId;
        this.x = x;
        this.y = y;
        this.isSpectator = isSpectator;
    }

    public String getPlayerId() { return playerId; }
    public int getX() { return x; }
    public int getY() { return y; }
    public boolean isSpectator() { return isSpectator; }
}