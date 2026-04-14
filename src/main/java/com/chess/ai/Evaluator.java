package com.chess.ai;

import com.chess.model.*;

public class Evaluator {

    private static final int KING_VALUE     = 10000;
    private static final int CHARIOT_VALUE  = 1000;
    private static final int CANNON_VALUE   = 450;
    private static final int HORSE_VALUE    = 450;
    private static final int ADVISOR_VALUE  = 200;
    private static final int ELEPHANT_VALUE = 200;
    private static final int PAWN_VALUE     = 100;
    private static final int RIVER_BONUS    = 100;

    // 兵的位置加成表（红方视角：row=9 是红方底线）
    // 黑方棋子使用 9-row 转换
    private static final int[][] PAWN_TABLE = {
        {  0,  0,  0,  0,  0,  0,  0,  0,  0},
        {  0,  0,  0,  0,  0,  0,  0,  0,  0},
        {  0,  0,  0,  0,  0,  0,  0,  0,  0},
        { 10, 20, 30, 45, 55, 45, 30, 20, 10},
        { 10, 20, 30, 45, 55, 45, 30, 20, 10},
        {  5, 10, 20, 30, 35, 30, 20, 10,  5},
        {  0,  0,  0,  0,  0,  0,  0,  0,  0},
        {  0,  0,  0,  0,  0,  0,  0,  0,  0},
        {  0,  0,  0,  0,  0,  0,  0,  0,  0},
        {  0,  0,  0,  0,  0,  0,  0,  0,  0},
    };

    private static final int[][] CHARIOT_TABLE = {
        {14, 14, 12, 18, 16, 18, 12, 14, 14},
        {16, 20, 18, 24, 26, 24, 18, 20, 16},
        {12, 12, 12, 18, 18, 18, 12, 12, 12},
        {12, 18, 16, 22, 22, 22, 16, 18, 12},
        {12, 16, 14, 18, 18, 18, 14, 16, 12},
        {12, 18, 16, 22, 22, 22, 16, 18, 12},
        { 6, 12, 12, 18, 18, 18, 12, 12,  6},
        { 4,  8,  6, 14, 14, 14,  6,  8,  4},
        { 8,  4,  8, 14, 12, 14,  8,  4,  8},
        {-2,  4,  2, 10,  8, 10,  2,  4, -2},
    };

    private static final int[][] CANNON_TABLE = {
        { 0,  0,  0,  0,  0,  0,  0,  0,  0},
        { 0,  0,  0,  0,  0,  0,  0,  0,  0},
        { 0,  0,  0,  0,  0,  0,  0,  0,  0},
        { 0,  0,  0,  0,  0,  0,  0,  0,  0},
        { 0,  0,  0,  0,  0,  0,  0,  0,  0},
        {12, 12, 12, 15, 15, 15, 12, 12, 12},
        {14, 16, 18, 22, 22, 22, 18, 16, 14},
        {14, 18, 20, 24, 26, 24, 20, 18, 14},
        {14, 18, 20, 24, 26, 24, 20, 18, 14},
        {13, 16, 18, 22, 22, 22, 18, 16, 13},
    };

    private static final int[][] HORSE_TABLE = {
        {-20,-10,-10, -5, -5,-10,-10,-20},
        {-10,  0,  5,  5,  5,  5,  0,-10},
        {-10,  5, 10, 10, 10, 10,  5,-10},
        {-10,  5, 10, 15, 15, 10,  5,-10},
        {-10,  5, 10, 15, 15, 10,  5,-10},
        {-10,  5, 10, 10, 10, 10,  5,-10},
        {-10,  0,  5,  5,  5,  5,  0,-10},
        {-20,-10,-10, -5, -5,-10,-10,-20},
        {  0,  0,  0,  0,  0,  0,  0,  0},
        {  0,  0,  0,  0,  0,  0,  0,  0},
    };

    private int getBaseValue(PieceType type) {
        return switch (type) {
            case KING      -> KING_VALUE;
            case CHARIOT   -> CHARIOT_VALUE;
            case CANNON    -> CANNON_VALUE;
            case HORSE     -> HORSE_VALUE;
            case ADVISOR   -> ADVISOR_VALUE;
            case ELEPHANT  -> ELEPHANT_VALUE;
            case PAWN      -> PAWN_VALUE;
        };
    }

    private int getPositionBonus(Piece p) {
        int r = p.row;
        int c = p.col;
        int redRow = 9 - r; // 黑方翻转视角

        return switch (p.type) {
            case PAWN -> {
                int river = PAWN_TABLE[redRow][c];
                boolean crossed = (p.color == Color.RED && r <= 4) || (p.color == Color.BLACK && r >= 5);
                yield river + (crossed ? RIVER_BONUS : 0);
            }
            case CHARIOT -> CHARIOT_TABLE[redRow][c];
            case CANNON  -> CANNON_TABLE[redRow][c];
            case HORSE   -> HORSE_TABLE[redRow][c];
            default      -> 0;
        };
    }

    public int evaluate(Board board) {
        int score = 0;

        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.get(r, c);
                if (p == null) continue;

                int value = getBaseValue(p.type) + getPositionBonus(p);
                if (p.color == Color.RED) score += value;
                else                       score -= value;
            }
        }

        // 将军惩罚
        if (MoveValidator.isCheck(board, Color.RED))   score -= 50;
        if (MoveValidator.isCheck(board, Color.BLACK))  score += 50;

        return score;
    }
}
