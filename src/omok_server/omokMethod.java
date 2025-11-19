package omok_server;

public class omokMethod {
    private final int maxSize = 15;
    private final int[][] map = new int[maxSize][maxSize];

    public void init() {
        for (int i = 0; i < maxSize; i++) {
            for (int j = 0; j < maxSize; j++) {
                map[i][j] = 0;
            }
        }
    }
    public void inputStone(Stone stone) {
        int x = stone.getX();
        int y = stone.getY();
        int color = stone.getColor();
        map[y][x] = color;
    }

    public boolean checkWin(Stone stone) {
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] direction : directions) {
            int count = 1;
            int x = stone.getX();
            int y = stone.getY();
            count += countDirection(x, y, direction[0], direction[1], stone.getColor());
            count += countDirection(x, y, -direction[0], -direction[1], stone.getColor());
            if (count >= 5) {
                return true;
            }
        }
        return false;
    }

    public int countDirection(int x, int y, int dx, int dy, int player) {
        int count = 0;
        int nx = x + dx, ny = y + dy;
        while (nx >= 0 && ny >= 0 && ny < map.length && nx < map.length && map[ny][nx] == player) {
            count++;
            nx += dx;
            ny += dy;
        }
        return count;
    }

    public int[][] getMap() {
        return map;
    }
}
