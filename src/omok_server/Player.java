package omok_server;

import omok_server.data.Role;

public class Player {
    private Role role;
    private int index;
    private int color;

    public Player(int index, int color,  Role role) {
        this.index = index;
        this.color = color;
        this.role = role;
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
}
