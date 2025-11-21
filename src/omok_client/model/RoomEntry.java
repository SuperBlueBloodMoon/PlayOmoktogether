package omok_client.model;

public class RoomEntry {
    private final String roomId; // 서버에서 부여한 고유 ID (숨김 정보)
    private final String title;
    public RoomEntry(String roomId, String title) {
        this.roomId = roomId;
        this.title = title;
    }
    public String getRoomId() {
        return roomId;
    }
    public String getTitle() {
        return title;
    }
    @Override
    public String toString() {
        return title;
    }

}