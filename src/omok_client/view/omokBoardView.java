package omok_client.view;

import javax.swing.*;
import java.awt.*;

public class omokBoardView extends JPanel {
    private static final int cellSize = 35;
    private final int MARGIN = 40;
    private final int BLACK = 1;
    private final int WHITE = 2;
    private int[][] board;

    public omokBoardView() {
        // board 배열 초기화 - 여기가 문제였어요
        board = new int[15][15];

        setPreferredSize(new Dimension(cellSize * 14 + MARGIN * 2, cellSize * 14 + MARGIN * 2));
        setBackground(new Color(222, 184, 135));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        drawBoard(g);
        drawStone(g);
    }

    public void drawBoard(Graphics g) {
        g.setColor(Color.BLACK);
        for (int i = 0; i < 15; i++) {
            // 가로선
            g.drawLine(MARGIN - 8, MARGIN + i * cellSize,
                    MARGIN - 8 + 14 * cellSize, MARGIN + i * cellSize);
            // 세로선
            g.drawLine(MARGIN - 8 + i * cellSize, MARGIN,
                    MARGIN - 8 + i * cellSize, MARGIN + 14 * cellSize);
        }

        // 화점 그리기
        drawStarPoints(g);
    }

    // 화점 그리기
    private void drawStarPoints(Graphics g) {
        g.setColor(Color.BLACK);
        int[] starPositions = {3, 7, 11}; // 4번째, 8번째, 12번째 선

        for (int y : starPositions) {
            for (int x : starPositions) {
                int px = MARGIN - 8 + x * cellSize;
                int py = MARGIN + y * cellSize;
                g.fillOval(px - 3, py - 3, 6, 6);
            }
        }
    }

    public void drawStone(Graphics g) {
        if (board == null) return;

        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                if (board[y][x] == BLACK) {
                    g.setColor(Color.BLACK);
                    // 돌의 위치 조정 (교차점에 정확히 배치)
                    int px = MARGIN - 8 + x * cellSize - 10;
                    int py = MARGIN + y * cellSize - 10;
                    g.fillOval(px, py, 20, 20);
                } else if (board[y][x] == WHITE) {
                    g.setColor(Color.WHITE);
                    int px = MARGIN - 8 + x * cellSize - 10;
                    int py = MARGIN + y * cellSize - 10;
                    g.fillOval(px, py, 20, 20);
                    g.setColor(Color.BLACK);
                    g.drawOval(px, py, 20, 20);
                }
            }
        }
    }

    public void setBoard(int[][] board) {
        this.board = board;
        repaint(); // Swing 이벤트에 "다시 그려라" 요청
    }

    // 특정 위치에 돌 놓기
    public void placeStone(int x, int y, int color) {
        if (x >= 0 && x < 15 && y >= 0 && y < 15) {
            board[y][x] = color;
            repaint();
        }
    }

    // 보드 초기화
    public void clearBoard() {
        board = new int[15][15];
        repaint();
    }

    // 특정 위치가 비어있는지 확인
    public boolean isEmpty(int x, int y) {
        if (x >= 0 && x < 15 && y >= 0 && y < 15) {
            return board[y][x] == 0;
        }
        return false;
    }

    public static int getCellSize() {
        return cellSize;
    }

    public int getMargin() {
        return MARGIN;
    }

    public int[][] getBoard() {
        return board;
    }
}