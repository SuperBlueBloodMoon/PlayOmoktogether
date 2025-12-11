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
    public static final int MODE_RESULT_COUNT = 16;
    public static final int MODE_REPLAY_PREV = 17;
    public static final int MODE_REPLAY_NEXT = 18;
    public static final int MODE_CURRENT_COUNT = 19;
    public static final int MODE_PLACE_STONE = 20;
    public static final int MODE_STONE_PLACED = 21;
    public static final int MODE_SUGGEST_MOVE = 22;
    public static final int MODE_SUGGESTION_RECEIVED = 23;
    public static final int MODE_GAME_OVER = 24;
    public static final int MODE_TURN_CHANGED = 27;

    // 훈수 시스템 모드
    public static final int MODE_REQUEST_ADVICE = 30;      // 플레이어가 훈수 요청
    public static final int MODE_ADVICE_REQUEST_BROADCAST = 31; // 관전자들에게 훈수 요청 알림
    public static final int MODE_OFFER_ADVICE = 32;        // 관전자가 훈수 제공 의사 표시
    public static final int MODE_ADVICE_OFFERS_LIST = 33;  // 훈수 제공 의사 표시한 관전자 목록
    public static final int MODE_SELECT_ADVISOR = 34;      // 플레이어가 관전자 선택
    public static final int MODE_ADVICE_SELECTED = 35;     // 선택 완료 알림
    public static final int MODE_ADVICE_LIMIT_EXCEEDED = 36; // 훈수 횟수 초과
    public static final int MODE_GAME_CHAT = 37;           // 게임 중 채팅 (새로 추가)

    private String userID;
    private int mode;
    private String message;
    private byte[] image;
    private String currentIndex;
    private String endIndex;
    private int x;
    private int y;
    private int color;

    // 훈수 시스템용
    private String advisorId;      // 선택된 관전자 ID
    private int adviceColor;       // 훈수 색상 (관전자별로 다름)

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
    public OmokMsg(String userID, int mode, int x, int y) {
        this.userID = userID;
        this.mode = mode;
        this.x = x;
        this.y = y;
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