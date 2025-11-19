package omok_client.view;

import omok_server.omokMethod;

import javax.swing.*;
import java.awt.*;

public class omokBoardView extends JPanel {
    private static final int cellSize = 35;
    private final int MARGIN = 40;
    private final int BLACK = 1;
    private final int WHITE = 2;
    private int[][] board;

    public omokBoardView() {
        setPreferredSize(new Dimension(cellSize * 14 + MARGIN * 2, cellSize * 14 + MARGIN * 2)); // Dimension -> 너비(width)와 높이(height)를 함께 저장하는 객체, setPreferredSize() -> 컴포넌트가 갖기를 원하는 권장 크기를 지정
        setBackground(new Color(222, 184, 135));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        drawBoard(g);
        drawStone(g);

    }
    public void drawBoard(Graphics g){
        for (int i = 0; i < 15; i++) {
            g.drawLine(MARGIN - 8, MARGIN + i * cellSize, MARGIN - 8 + 14 * cellSize, MARGIN + i * cellSize); // 가로
            g.drawLine(MARGIN - 8 + i * cellSize, MARGIN, MARGIN - 8 + i * cellSize, MARGIN + 14 * cellSize); // 세로
        }
    }

    public void drawStone(Graphics g) {
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                if (board[y][x] == BLACK) {
                    g.setColor(Color.BLACK);
                    g.fillOval(x * cellSize + 5, y * cellSize + 5, 20, 20);
                } else if (board[y][x] == WHITE) {
                    g.setColor(Color.WHITE);
                    g.fillOval(x * cellSize + 5, y * cellSize + 5, 20, 20);
                    g.setColor(Color.BLACK);
                    g.drawOval(x * cellSize + 5, y * cellSize + 5, 20, 20);
                }
            }
        }
    }

    public void setBoard(int[][] board) {
        this.board = board;
        repaint(); // Swing 이벤트 큐에 “다시 그려라” 요청
    }

    public static int getCellSize() {
        return cellSize;
    }
}
