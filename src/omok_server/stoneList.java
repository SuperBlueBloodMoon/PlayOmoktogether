package omok_server;

import java.util.List;

public class stoneList {
    private List<Stone> stones;

    public stoneList(List<Stone> stones) {
        this.stones = stones;
    }

    // Getter
    public List<Stone> getStones() {
        return stones;
    }
    // Setter
    public void addStone(Stone stone) {
        stones.add(stone);
    }
}
