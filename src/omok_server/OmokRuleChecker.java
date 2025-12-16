package omok_server;
// 외부 참조 코드
public class OmokRuleChecker {

    private static final int BLACK = 1;
    private static final int WALL = 3; // 보드 밖

    // 4방향: 가로, 세로, 우하향 대각선, 좌하향 대각선
    private static final int[] DX = {1, 0, 1, 1};
    private static final int[] DY = {0, 1, 1, -1};

    public boolean isForbiddenMove(int[][] board, int x, int y) {
        // 주의: GameRoom에서 이미 board[y][x]에 흑돌(1)을 놓고 호출했다고 가정.

        // 1. 장목(Overline) 체크 (6목 이상)
        if (checkOverline(board, x, y)) {
            return true;
        }

        // 2. 4-4 (Double Four) & 3-3 (Double Three) 체크
        int fourCount = 0;
        int threeCount = 0;

        for (int i = 0; i < 4; i++) {
            // 각 방향별로 4목이 만들어지는지, 3목이 만들어지는지 확인
            if (checkFour(board, x, y, i)) {
                fourCount++;
            }
            // 4목이 아닐 때만 3목 검사
            else if (checkOpenThree(board, x, y, i)) {
                threeCount++;
            }
        }

        // 사사(4-4) 금수: 4목이 2개 이상
        if (fourCount >= 2) return true;

        // 삼삼(3-3) 금수: 열린 3목이 2개 이상
        if (threeCount >= 2) return true;

        return false;
    }

    // --- [1] 장목(Overline) 검사 ---
    private boolean checkOverline(int[][] board, int x, int y) {
        // 이미 GameRoom에서 돌을 뒀으므로 임시 착수/해제 코드 삭제함
        boolean isOverline = false;

        for (int i = 0; i < 4; i++) {
            // 현재 위치(x,y)는 board에 1로 박혀있음.
            // 양쪽 방향으로 연속된 1의 개수를 셉니다.
            // (자기 자신 1개 + 방향1 + 방향2)
            int count = 1;
            count += countConsecutive(board, x, y, DX[i], DY[i]);
            count += countConsecutive(board, x, y, -DX[i], -DY[i]);

            if (count >= 6) {
                isOverline = true;
                break;
            }
        }
        return isOverline;
    }

    // --- [2] 4목(Four) 검사 ---
    private boolean checkFour(int[][] board, int x, int y, int dirIndex) {
        int[] line = getLineInfo(board, x, y, DX[dirIndex], DY[dirIndex], 5);
        String s = lineToString(line);

        if (s.contains("11111")) return false; // 5목은 승리이므로 금수 아님

        // 4목 패턴 목록
        String[] patterns = { "1111", "11101", "10111", "11011", "11101" };

        for (String p : patterns) {
            if (s.contains(p)) {
                // 패턴이 발견되면, 정말 5목이 될 수 있는지(빈칸이 있는지) 확인
                return canBeFive(line);
            }
        }
        return false;
    }

    // --- [3] 열린 3목(Open Three) 검사 ---
    private boolean checkOpenThree(int[][] board, int x, int y, int dirIndex) {
        int[] line = getLineInfo(board, x, y, DX[dirIndex], DY[dirIndex], 5);
        String s = lineToString(line);

        if (s.contains("1111") || s.contains("11101") || s.contains("10111") || s.contains("11011")) return false;

        // 열린 3목 패턴 (양쪽 0 필수)
        if (s.contains("01110")) return true;
        if (s.contains("010110")) return true;
        if (s.contains("011010")) return true;

        return false;
    }

    // ★★★ [수정됨] 4목이 진짜 5목이 될 수 있는지 검사 ★★★
    private boolean canBeFive(int[] line) {
        String s = lineToString(line);

        // 죽은 4(양쪽이 막힘)를 걸러내기 위한 로직
        // "0"이 포함된 5목 가능 형태가 하나라도 있어야 함

        // 1. 한쪽 뚫린 4
        if (s.contains("01111") || s.contains("11110")) return true;

        // 2. 중간 뚫린 4 (이미 checkFour에서 패턴은 확인했으므로 빈칸 유무만 재확인)
        if (s.contains("10111") || s.contains("11011") || s.contains("11101")) return true;

        return false; // 꽉 막힌 4 (예: 211112)
    }

    // 보드에서 라인 정보 추출
    private int[] getLineInfo(int[][] board, int x, int y, int dx, int dy, int radius) {
        int size = radius * 2 + 1;
        int[] line = new int[size];

        for (int i = 0; i < size; i++) {
            int dist = i - radius;
            int nx = x + (dx * dist);
            int ny = y + (dy * dist);

            if (nx < 0 || nx >= 15 || ny < 0 || ny >= 15) {
                line[i] = WALL;
            } else {
                line[i] = board[ny][nx];
            }
        }
        return line;
    }

    private String lineToString(int[] line) {
        StringBuilder sb = new StringBuilder();
        for (int val : line) {
            sb.append(val);
        }
        return sb.toString();
    }

    // 연속된 돌 개수 세기 (자신은 제외하고 방향 탐색)
    private int countConsecutive(int[][] board, int x, int y, int dx, int dy) {
        int count = 0;
        int nx = x + dx;
        int ny = y + dy;

        while (nx >= 0 && nx < 15 && ny >= 0 && ny < 15 && board[ny][nx] == BLACK) {
            count++;
            nx += dx;
            ny += dy;
        }
        return count;
    }
}