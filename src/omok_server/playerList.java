package omok_server;

import java.util.List;

public class playerList {
    private List<Player> Players;
    private int color;
    public playerList(List<Player> Players, int color) {
        this.Players = Players;
        this.color = color;
    }
    public List<Player> getPlayers() {
        return Players;
    }
    public void addPlayer(Player player) {
        Players.add(player);
    }
}
