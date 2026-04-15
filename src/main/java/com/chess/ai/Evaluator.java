package com.chess.ai;

import com.chess.model.*;
import java.util.*;

/**
 * 中国象棋局面评估 — 专家版
 *
 * 评估原则：
 * 1. 子力第一（80%权重）：多大子少子是胜负核心
 * 2. 过河兵（战略关键）：过河兵价值翻倍，逼近九宫再翻倍
 * 3. 大子活跃度：开放线、被蹩腿等
 * 4. 王安全：被将军必须应，士象护王加分
 * 5. 兑换判断：少子时避免换子，多子时主动换
 */
public class Evaluator {

    // ===================== 基础子力价值 =====================
    public static final int V_KING       = 100000;
    public static final int V_CHARIOT    = 1000;
    public static final int V_CANNON     = 450;
    public static final int V_HORSE     = 420;
    public static final int V_ADVISOR   = 200;
    public static final int V_ELEPHANT  = 200;
    public static final int V_PAWN      = 100;
    // 过河兵增值
    public static final int V_PAWN_CROSS = 120;
    public static final int V_PAWN_ASSAULT = 60;

    public int getBaseValue(PieceType type) {
        return switch (type) {
            case KING -> V_KING;
            case CHARIOT -> V_CHARIOT;
            case CANNON  -> V_CANNON;
            case HORSE   -> V_HORSE;
            case ADVISOR -> V_ADVISOR;
            case ELEPHANT -> V_ELEPHANT;
            case PAWN    -> V_PAWN;
        };
    }

    // ===================== 红方视角位置加成表（0=黑底线，9=红底线）=====================

    // 车：喜欢中心线、开放线，高位车更有威慑力
    private static final int[][] CHARIOT_PST = {
        //   c0   c1   c2   c3   c4   c5   c6   c7   c8
        {  14,  16,  12,  18,  16,  18,  12,  16,  14 },  // row 0 黑底线
        {  16,  20,  18,  24,  26,  24,  18,  20,  16 },
        {  12,  12,  12,  18,  18,  18,  12,  12,  12 },
        {  18,  20,  18,  22,  22,  22,  18,  20,  18 },
        {  16,  18,  16,  20,  20,  20,  16,  18,  16 },
        {  18,  20,  18,  22,  22,  22,  18,  20,  18 },
        {  14,  16,  14,  18,  18,  18,  14,  16,  14 },
        {  12,  14,  12,  16,  16,  16,  12,  14,  12 },
        {  10,  12,  10,  14,  14,  14,  10,  12,  10 },
        {   0,   4,   2,  10,   8,  10,   2,   4,   0 },  // row 9 红底线
    };

    // 马：河口（row 4）、纵深位置（row 2-3）价值高，边角被憋
    private static final int[][] HORSE_PST = {
        {  0,  0,  0,  0,  0,  0,  0,  0,  0 },
        {  2,  4,  6,  8,  8,  8,  6,  4,  2 },
        {  4,  6,  8, 10, 12, 10,  8,  6,  4 },  // row 2 纵深
        {  6,  8, 10, 12, 14, 12, 10,  8,  6 },
        {  8, 10, 12, 14, 16, 14, 12, 10,  8 },  // row 4 河口
        {  6,  8, 10, 12, 14, 12, 10,  8,  6 },
        {  4,  6,  8, 10, 12, 10,  8,  6,  4 },
        {  2,  4,  6,  8,  8,  8,  6,  4,  2 },
        {  0,  2,  4,  6,  6,  6,  4,  2,  0 },
        {  0,  0,  0,  0,  0,  0,  0,  0,  0 },
    };

    // 炮：高位炮更有威慑，中心线炮更强
    private static final int[][] CANNON_PST = {
        {  0,  0,  0,  0,  0,  0,  0,  0,  0 },
        {  0,  0,  0,  0,  0,  0,  0,  0,  0 },
        {  0,  0,  0,  0,  0,  0,  0,  0,  0 },
        {  0,  0,  0,  0,  0,  0,  0,  0,  0 },
        {  4,  4,  4,  6,  6,  6,  4,  4,  4 },
        {  8,  8, 10, 12, 14, 12, 10,  8,  8 },
        { 12, 14, 16, 20, 22, 20, 16, 14, 12 },
        { 14, 16, 18, 22, 24, 22, 18, 16, 14 },
        { 14, 16, 18, 22, 24, 22, 18, 16, 14 },
        { 12, 14, 16, 20, 22, 20, 16, 14, 12 },
    };

    // 兵：过河兵（row 0-4 红方视角）价值激增
    private static final int[][] PAWN_PST = {
        // row 0 = 黑底线（红方视角最前线）
        { 90, 100, 100, 110, 120, 110, 100, 100,  90 },  // 逼近九宫
        { 70,  80,  80,  90, 100,  90,  80,  80,  70 },
        { 50,  60,  60,  70,  80,  70,  60,  60,  50 },  // 过河
        { 30,  40,  45,  55,  60,  55,  45,  40,  30 },  // 河口
        // 未过河
        { 10,  15,  20,  25,  25,  25,  20,  15,  10 },
        {  5,  10,  15,  15,  15,  15,  15,  10,   5 },
        {  0,   0,   0,   0,   0,   0,   0,   0,   0 },
        {  0,   0,   0,   0,   0,   0,   0,   0,   0 },
        {  0,   0,   0,   0,   0,   0,   0,   0,   0 },
        {  0,   0,   0,   0,   0,   0,   0,   0,   0 },
    };

    // 士：留在九宫内
    private static final int[][] ADVISOR_PST = {
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
        { 0,  0, 20,  0,  0,  0, 20,  0,  0 },
        { 0, 20,  0,  0, 20,  0,  0, 20,  0 },
        { 0,  0, 20, 20, 25, 20, 20,  0,  0 },
    };

    // 象：巡河（row 5）加分
    private static final int[][] ELEPHANT_PST = {
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
        { 0,  0, 20,  0,  0,  0, 20,  0,  0 },  // 巡河加分
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
        {20,  0,  0,  0,  0,  0,  0,  0, 20 },  // 巡河加分
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
        { 0,  0,  0,  0,  0,  0,  0,  0,  0 },
    };

    // ===================== 主评估函数 =====================
    // 返回红方视角分数：正数=红优，负数=黑优

    public int evaluate(Board board) {
        int redScore   = 0;
        int blackScore = 0;

        List<Piece> redPieces    = new ArrayList<>();
        List<Piece> blackPieces  = new ArrayList<>();
        Piece redKing   = null, blackKing = null;

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.get(r, c);
                if (p == null) continue;
                if (p.color == Color.RED) {
                    redPieces.add(p);
                    if (p.type == PieceType.KING) redKing = p;
                } else {
                    blackPieces.add(p);
                    if (p.type == PieceType.KING) blackKing = p;
                }
            }
        }

        if (redKing == null)  return -V_KING;
        if (blackKing == null) return  V_KING;

        // 1. 子力价值 + 位置加成（每个棋子单独计算）
        for (Piece p : redPieces) {
            redScore += pieceValue(board, p);
        }
        for (Piece p : blackPieces) {
            blackScore += pieceValue(board, p);
        }

        // 2. 王安全
        redScore   += kingSafety(board, redKing, blackPieces);
        blackScore += kingSafety(board, blackKing, redPieces);

        // 3. 大子兑换判断（少子方避免换子）
        int rMat = materialTotal(redPieces);
        int bMat = materialTotal(blackPieces);
        if (rMat < bMat - V_CHARIOT) redScore   -= 30; // 红少子，避免换子
        if (bMat < rMat - V_CHARIOT) blackScore -= 30; // 黑少子，避免换子

        // 4. 局势：将帅对面/将军
        if (MoveValidator.isKingsFacing(board)) {
            // 将帅对面：红方被将（黑方威胁更大）
            if (MoveValidator.isCheck(board, Color.RED))   redScore   -= 100;
            if (MoveValidator.isCheck(board, Color.BLACK)) blackScore -= 100;
        }
        if (MoveValidator.isCheck(board, Color.RED))   redScore   -= 60;
        if (MoveValidator.isCheck(board, Color.BLACK)) blackScore -= 60;

        return redScore - blackScore;
    }

    /**
     * 单个棋子的总价值：基础分 + 位置分
     */
    private int pieceValue(Board board, Piece p) {
        int base = getBaseValue(p.type);
        int bonus = 0;

        // 转换为红方视角行（0=黑底线，9=红底线）
        int redRow = 9 - p.row;
        int col = p.col;

        // 位置加成表
        bonus += switch (p.type) {
            case CHARIOT  -> CHARIOT_PST[redRow][col];
            case CANNON   -> CANNON_PST[redRow][col];
            case HORSE    -> HORSE_PST[redRow][col];
            case PAWN     -> PAWN_PST[redRow][col];
            case ADVISOR  -> ADVISOR_PST[redRow][col];
            case ELEPHANT -> ELEPHANT_PST[redRow][col];
            default       -> 0;
        };

        // 过河兵额外增值（过河后价值接近翻倍）
        if (p.type == PieceType.PAWN) {
            if (p.color == Color.RED && p.row <= 4) {
                bonus += V_PAWN_CROSS;
                if (p.row <= 2) bonus += V_PAWN_ASSAULT;
            }
            if (p.color == Color.BLACK && p.row >= 5) {
                bonus += V_PAWN_CROSS;
                if (p.row >= 7) bonus += V_PAWN_ASSAULT;  // 逼近红方九宫
            }
        }

        // 马蹩腿惩罚（阻碍进攻）
        if (p.type == PieceType.HORSE) {
            bonus -= horseBlockPenalty(board, p);
        }

        // 炮有炮架时加分（有威胁）
        if (p.type == PieceType.CANNON) {
            bonus += cannonPlatformBonus(board, p);
        }

        // 象/士在九宫加分
        if (p.type == PieceType.ADVISOR || p.type == PieceType.ELEPHANT) {
            if (MoveValidator.inPalace(p.color, p.row, p.col)) {
                bonus += 10;
            } else {
                bonus -= 15; // 象/士不在九宫 = 严重失误
            }
        }

        return base + bonus;
    }

    /** 马被蹩腿惩罚 */
    private int horseBlockPenalty(Board board, Piece horse) {
        int[][] dirs = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        int[][] blocks = {{0,-1},{-1,0},{-1,0},{0,1},{0,-1},{1,0},{1,0},{0,1}};
        int blocked = 0;
        for (int i = 0; i < 8; i++) {
            int br = horse.row + blocks[i][0];
            int bc = horse.col + blocks[i][1];
            if (br >= 0 && br < Board.ROWS && bc >= 0 && bc < Board.COLS) {
                if (!MoveValidator.isEmpty(board, br, bc)) blocked++;
            }
        }
        return blocked * 10;
    }

    /** 炮有炮架时加分（威胁能力） */
    private int cannonPlatformBonus(Board board, Piece cannon) {
        int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
        int platforms = 0;
        for (int[] d : dirs) {
            int r = cannon.row + d[0];
            int c = cannon.col + d[1];
            while (r >= 0 && r < Board.ROWS && c >= 0 && c < Board.COLS) {
                Piece p = board.get(r, c);
                if (p != null) {
                    if (p.color != cannon.color) platforms++;
                    break;
                }
                r += d[0];
                c += d[1];
            }
        }
        return platforms * 8;
    }

    /** 王安全评估 */
    private int kingSafety(Board board, Piece king, List<Piece> enemyPieces) {
        int safety = 0;

        // 九宫内己方棋子保护
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int r = king.row + dr;
                int c = king.col + dc;
                if (r < 0 || r >= Board.ROWS || c < 0 || c >= Board.COLS) continue;
                if (dr == 0 && dc == 0) continue;
                Piece p = board.get(r, c);
                if (p != null && p.color == king.color) {
                    if (p.type == PieceType.ADVISOR || p.type == PieceType.ELEPHANT) {
                        safety += 15; // 士象护王
                    } else {
                        safety += 5; // 其他棋子护王
                    }
                }
            }
        }

        // 对方大子逼近威胁
        for (Piece enemy : enemyPieces) {
            int dist = Math.max(
                Math.abs(enemy.col - king.col),
                Math.abs(enemy.row - king.row)
            );
            if (enemy.type == PieceType.CHARIOT) {
                // 车威胁最大：直线逼近
                if (enemy.col == king.col || enemy.row == king.row) {
                    safety -= 25;
                } else if (dist <= 3) {
                    safety -= 12;
                }
            } else if (enemy.type == PieceType.CANNON) {
                if (dist <= 3) safety -= 8;
            } else if (enemy.type == PieceType.HORSE) {
                if (dist <= 2) safety -= 6;
            }
        }

        return Math.max(safety, -60);
    }

    private int materialTotal(List<Piece> pieces) {
        int total = 0;
        for (Piece p : pieces) total += getBaseValue(p.type);
        return total;
    }
}
