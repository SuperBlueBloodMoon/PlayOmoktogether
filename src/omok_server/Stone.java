package omok_server;

public class Stone {
    private int x;
    private int y;
    private int color;
    private int index;

    public Stone(int x, int y, int color, int index) {
        this.x = x;
        this.y = y;
        this.color = color;
        this.index = index;
    }
    // Getters
    public int getIndex() {
        return index;
    }

    public int getColor() {
        return color;
    }

    public int getY() {
        return y;
    }

    public int getX() {
        return x;
    }
}
