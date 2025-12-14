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

    // Getters
    public OmokServer.ClientHandler getClientHandler() {
        return client;
    }
    public int getIndex() {
        return index;
    }
    public int getColor() {
        return color;
    }
    public Role getRole() {
        return role;
    }
    // Setters
    public void setColor(int color) {
        this.color = color;
    }
    public void setIndex(int index) {
        this.index = index;
    }
    public void setRole(Role role) {
        this.role = role;
    }
}
