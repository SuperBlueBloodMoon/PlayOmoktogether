package omok_server;

import java.util.List;

public class stoneList {
    private List<Stone> stones;

    public stoneList(List<Stone> stones) {
        this.stones = stones;
    }

    public List<Stone> getStones() {
        return stones;
    }

    public void addStone(Stone stone) {
        stones.add(stone);
    }
}
