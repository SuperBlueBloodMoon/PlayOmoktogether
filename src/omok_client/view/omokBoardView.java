package omok_client.view;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class omokBoardView extends JPanel {
    private static final int cellSize = 35;
    private final int MARGIN = 40;
    private final int BLACK = 1;
    private final int WHITE = 2;
    private int[][] board;
    private List<Point> suggestions;  // 관전자 훈수 위치

    public omokBoardView() {
        // board 배열 초기화
        board = new int[15][15];
        suggestions = new ArrayList<>();

        setPreferredSize(new Dimension(cellSize * 14 + MARGIN * 2, cellSize * 14 + MARGIN * 2));
        setBackground(new Color(222, 184, 135));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        drawBoard(g);
        drawStone(g);
        drawSuggestions(g);  // 훈수 표시
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

        // 화점 그리기 (선택사항)
        drawStarPoints(g);
    }

    // 화점(星) 표시 - 오목판의 주요 교차점
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
        if (board == null) return; // null 체크 추가

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

    // 특정 위치에 돌 놓기
    public void placeStone(int x, int y, int color) {
        if (x >= 0 && x < 15 && y >= 0 && y < 15) {
            board[y][x] = color;
            repaint();
        }
    }
    public void removeStone(int x, int y) {
        if (x >= 0 && x < 15 && y >= 0 && y < 15) {
            board[y][x] = 0;
            repaint();
        }
    }

    // 특정 위치가 비어있는지 확인
    public boolean isEmpty(int x, int y) {
        if (x >= 0 && x < 15 && y >= 0 && y < 15) {
            return board[y][x] == 0;
        }
        return false;
    }

    // 훈수 추가
    public void addSuggestion(int x, int y) {
        suggestions.add(new Point(x, y));
        repaint();
    }

    // 훈수 제거
    public void clearSuggestions() {
        suggestions.clear();
        repaint();
    }

    // 훈수 표시
    private void drawSuggestions(Graphics g) {
        // graphics인 g를 graphics2D로 캐스팅 -> 투명도 조절같은 기능 사용 가능!
        Graphics2D g2d = (Graphics2D) g;
        // SRC_OVER: 기존 이미지 위에 새 이미지를 겹쳐 그리기. 판 위에 돌 올리기 위해서
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)); // 반투명

        for (Point p : suggestions) {
            int px = MARGIN - 8 + p.x * cellSize - 10;
            int py = MARGIN + p.y * cellSize - 10;

            g2d.setColor(new Color(255, 0, 0)); // 빨간색 훈수
            g2d.fillOval(px, py, 20, 20);
            g2d.setColor(Color.BLACK);
            g2d.drawOval(px, py, 20, 20);
        }

        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); // 불투명으로 복원
    }

    public static int getCellSize() {
        return cellSize;
    }

    public int getMargin() {
        return MARGIN;
    }

    public void reset() {
        for (int y = 0; y < 15; y++) {
            for (int x = 0; x < 15; x++) {
                board[y][x] = 0;
            }
        }
        suggestions.clear();
        repaint();
    }
}