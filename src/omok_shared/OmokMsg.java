package omok_shared;

import javax.swing.*;
import java.io.Serializable;

public class OmokMsg implements Serializable {
    public final static int MODE_LOGIN = 0x1;
    public final static int MODE_LOGOUT = 0x2;
    public final static int MODE_LOBBY_STRING = 0x10;
    public final static int MODE_LOBBY_FILE = 0x11;
    public final static int MODE_LOBBY_IMAGE = 0x12;
    public final static int MODE_MAKE_ROOM = 0x30;
    public final static int MODE_TX_FILES = 0x80;

    String userID;
    int mode;
    String message;
    ImageIcon image;
    long size;
    public OmokMsg(String userID, int code, String message, ImageIcon image, long size) {
        this.userID = userID;
        this.mode = code;
        this.message = message;
        this.image = image;
        this.size = size;
    }

    public OmokMsg(String userID, int code, String message, ImageIcon image) {
        this(userID, code, message, image, 0);
    }
    public OmokMsg(String userID, int code) {
        this(userID, code, null, null);
    }
    public OmokMsg(String userID, int code, String message) {
        this(userID, code, message, null);
    }
    public OmokMsg(String userID, int code, ImageIcon image) {
        this(userID, code, null, image);
    }

    public String getUserID() {
        return userID;
    }

    public int getMode() {
        return mode;
    }

    public String getMessage() {
        return message;
    }

    public ImageIcon getImage() {
        return image;
    }

    public long getSize() {
        return size;
    }
}
