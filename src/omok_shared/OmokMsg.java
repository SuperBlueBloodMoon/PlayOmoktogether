package omok_shared;

import java.io.Serializable;

public class OmokMsg implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int MODE_LOGIN = 1;
    public static final int MODE_LOGOUT = 2;
    public static final int MODE_LOBBY_STRING = 3;
    public static final int MODE_REFRESH_USER_LIST = 4;
    public static final int MODE_MAKE_ROOM = 5;
    public static final int MODE_JOIN_ROOM = 6;
    public static final int MODE_REFRESH_ROOM_LIST = 7;
    public static final int MODE_ROOM_ENTERED = 8;
    public static final int MODE_ROOM_INFO = 9;
    public static final int MODE_REFRESH_GAME_USER_LIST = 10;
    public static final int MODE_EXIT_ROOM = 11;
    public static final int MODE_WAITING_STRING = 12;
    public static final int MODE_GAME_START = 13;
    public static final int MODE_START = 14;
    public static final int MODE_RESULT_COUNT = 15;
    public static final int MODE_REPLAY_PREV = 16;
    public static final int MODE_REPLAY_NEXT = 17;
    public static final int MODE_CURRENT_COUNT = 18;
    public static final int MODE_PLACE_STONE = 19;
    public static final int MODE_STONE_PLACED = 20;
    public static final int MODE_SUGGEST_MOVE = 21;
    public static final int MODE_SUGGESTION_RECEIVED = 22;
    public static final int MODE_GAME_OVER = 23;
    public static final int MODE_TURN_CHANGED = 24;

    // 훈수 시스템 모드
    public static final int MODE_REQUEST_ADVICE = 30;
    public static final int MODE_ADVICE_REQUEST_BROADCAST = 31;
    public static final int MODE_OFFER_ADVICE = 32;
    public static final int MODE_ADVICE_OFFERS_LIST = 33;
    public static final int MODE_SELECT_ADVISOR = 34;
    public static final int MODE_ADVICE_SELECTED = 35;
    public static final int MODE_ADVICE_LIMIT_EXCEEDED = 36;
    public static final int MODE_GAME_CHAT = 37;

    public static final int MODE_PLAYER_INFO = 38;        // 플레이어 정보
    public static final int MODE_SURRENDER = 39;          // 기권
    public static final int MODE_SPECTATOR_COUNT = 40;    // 관전자 수
    public static final int MODE_USER_STATS = 41;         // 사용자 전적

    private String userID;
    private int mode;
    private String message;
    private String currentIndex;
    private String endIndex;
    private int x;
    private int y;
    private int color;

    private String advisorId;
    private int adviceColor;

    public OmokMsg(String userID, int mode) {
        this.userID = userID;
        this.mode = mode;
    }

    public OmokMsg(String userID, int mode, String message) {
        this.userID = userID;
        this.mode = mode;
        this.message = message;
    }

    public OmokMsg(String userID, int mode, String currentIndex, String endIndex) {
        this.userID = userID;
        this.mode = mode;
        this.currentIndex = currentIndex;
        this.endIndex = endIndex;
    }

    public OmokMsg(String userID, int mode, int x, int y, int color) {
        this.userID = userID;
        this.mode = mode;
        this.x = x;
        this.y = y;
        this.color = color;
    }

    public OmokMsg(String userID, int mode, int x, int y, int color, int adviceColor) {
        this.userID = userID;
        this.mode = mode;
        this.x = x;
        this.y = y;
        this.color = color;
        this.adviceColor = adviceColor;
    }

    public String getUserID() { return userID; }
    public int getMode() { return mode; }
    public String getMessage() { return message; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getColor() { return color; }
    public String getCurrentIndex() { return currentIndex; }
    public String getEndIndex() { return endIndex; }
    public int getAdviceColor() { return adviceColor; }

    public void setAdvisorId(String advisorId) { this.advisorId = advisorId; }
    public void setAdviceColor(int adviceColor) { this.adviceColor = adviceColor; }
}