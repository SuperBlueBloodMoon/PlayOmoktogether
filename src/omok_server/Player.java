package omok_server;

import omok_server.data.Role;

public class Player {
    private Role role;
    private int index;
    private int color;
    private OmokServer.ClientHandler client;

    public Player(OmokServer.ClientHandler client) {
        this.client = client;
    }

    public OmokServer.ClientHandler getClientHandler() {
        return client;
    }
    public void setIndex(int index) {
        this.index = index;
    }
    public int getIndex() {
        return index;
    }
    public void setColor(int color) {
        this.color = color;
    }
    public int getColor() {
        return color;
    }
    public void setRole(Role role) {
        this.role = role;
    }
    public Role getRole() {
        return role;
    }
}
