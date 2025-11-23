package omok_shared;

import java.io.Serializable;

public class OmokMsg implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int MODE_LOGIN = 1;
    public static final int MODE_LOGOUT = 2;
    public static final int MODE_LOBBY_STRING = 3;
    public static final int MODE_LOBBY_IMAGE = 4;
    public static final int MODE_REFRESH_USER_LIST = 5;
    public static final int MODE_MAKE_ROOM = 6;
    public static final int MODE_JOIN_ROOM = 7;
    public static final int MODE_REFRESH_ROOM_LIST = 8;
    public static final int MODE_ROOM_ENTERED = 9;
    public static final int MODE_ROOM_INFO = 10;
    public static final int MODE_REFRESH_GAME_USER_LIST = 11;
    public static final int MODE_EXIT_ROOM = 12;
    public static final int MODE_WAITING_STRING = 13;
    public static final int MODE_GAME_START = 14;
    public static final int MODE_START = 15;

    // 게임 플레이용 모드
    public static final int MODE_PLACE_STONE = 20;        // 돌 놓기
    public static final int MODE_STONE_PLACED = 21;       // 돌이 놓여졌음
    public static final int MODE_SUGGEST_MOVE = 22;       // 관전자 훈수
    public static final int MODE_SUGGESTION_RECEIVED = 23; // 훈수 수신
    public static final int MODE_GAME_OVER = 24;          // 게임 종료
    public static final int MODE_TURN_CHANGED = 27;       // 턴 변경

    private String userID;
    private int mode;
    private String message;
    private byte[] image;

    // 게임 플레이용
    private int x;
    private int y;
    private int color;  // 1: 흑돌, 2: 백돌

    public OmokMsg(String userID, int mode) {
        this.userID = userID;
        this.mode = mode;
    }

    public OmokMsg(String userID, int mode, String message) {
        this.userID = userID;
        this.mode = mode;
        this.message = message;
    }

    public OmokMsg(String userID, int mode, int x, int y, int color) {
        this.userID = userID;
        this.mode = mode;
        this.x = x;
        this.y = y;
        this.color = color;
    }

    public String getUserID() { return userID; }
    public int getMode() { return mode; }
    public String getMessage() { return message; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getColor() { return color; }
}