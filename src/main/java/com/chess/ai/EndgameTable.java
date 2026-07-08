package com.chess.ai;

import com.chess.model.*;
import java.util.List;

/**
 * 残局库与残局判断
 * 识别简单残局局面并给出额外评估或特殊处理
 */
public class EndgameTable {
    
    /** 判断是否为残局（子力较少） */
    public static boolean isEndgame(Board board) {
        int pieceCount = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.get(r, c);
                if (p != null) pieceCount++;
            }
        }
        // 残局定义：双方总子力少于一定数量
        return pieceCount <= 12; // 少于12个子
    }
    
    /** 残局特殊评估调整（红方视角） */
    public static int endgameEval(Board board) {
        int score = 0;
        
        // 1. 兵卒过河价值提升
        score += pawnEndgameBonus(board, Color.RED);
        score -= pawnEndgameBonus(board, Color.BLACK);
        
        // 2. 车兵残局：车方优势
        score += chariotEndgame(board, Color.RED);
        score -= chariotEndgame(board, Color.BLACK);
        
        // 3. 简单杀法判断
        score += simpleMatingPatterns(board, Color.RED);
        score -= simpleMatingPatterns(board, Color.BLACK);
        
        return score;
    }
    
    /** 残局中兵卒的额外加分（已过河的兵） */
    private static int pawnEndgameBonus(Board board, Color color) {
        int bonus = 0;
        for (Piece p : board.getAllPieces(color)) {
            if (p.type != PieceType.PAWN) continue;
            
            // 过河兵在残局价值更高
            boolean crossed = (color == Color.RED) ? (p.row <= 4) : (p.row >= 5);
            if (crossed) {
                // 逼近九宫的兵价值更高
                int distToKingRow = (color == Color.RED) ? p.row : (9 - p.row);
                bonus += (10 - distToKingRow) * 15;
                
                // 底线兵价值极高
                if ((color == Color.RED && p.row <= 1) || (color == Color.BLACK && p.row >= 8)) {
                    bonus += 50;
                }
            }
        }
        return bonus;
    }
    
    /** 车兵残局评估（车方多兵时优势） */
    private static int chariotEndgame(Board board, Color color) {
        int chariots = 0, pawns = 0;
        for (Piece p : board.getAllPieces(color)) {
            if (p.type == PieceType.CHARIOT) chariots++;
            else if (p.type == PieceType.PAWN) pawns++;
        }
        
        // 有车无车残局优势极大
        int enemyChariots = 0;
        for (Piece p : board.getAllPieces(color.opposite())) {
            if (p.type == PieceType.CHARIOT) enemyChariots++;
        }
        
        int score = 0;
        if (chariots > enemyChariots) {
            score += 100 * (chariots - enemyChariots); // 有车对无车，大优
        }
        
        // 车方有兵配合加分
        if (chariots > 0 && pawns > 0) {
            score += pawns * 20;
        }
        
        return score;
    }
    
    /** 简单杀法模式识别（如双车杀、车兵杀等） */
    private static int simpleMatingPatterns(Board board, Color color) {
        int score = 0;
        
        // 获取将帅位置
        Piece king = null;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.get(r, c);
                if (p != null && p.type == PieceType.KING && p.color != color) {
                    king = p;
                }
            }
        }
        if (king == null) return 0;
        
        // 车将军将死威胁
        for (Piece p : board.getAllPieces(color)) {
            if (p.type == PieceType.CHARIOT) {
                // 车与将帅同线
                if (p.row == king.row || p.col == king.col) {
                    score += 30;
                }
            }
        }
        
        // 将军状态加分
        if (MoveValidator.isCheck(board, color.opposite())) {
            score += 40;
        }
        
        return score;
    }
    
    /** 检查是否为必胜残局（如单车胜单士等） */
    public static boolean isWinningEndgame(Board board, Color color) {
        // 简化判断：子力领先很多
        int myMaterial = 0, enemyMaterial = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.get(r, c);
                if (p == null) continue;
                int val = Evaluator.V_KING;
                switch (p.type) {
                    case CHARIOT -> val = Evaluator.V_CHARIOT;
                    case CANNON -> val = Evaluator.V_CANNON;
                    case HORSE -> val = Evaluator.V_HORSE;
                    case ADVISOR -> val = Evaluator.V_ADVISOR;
                    case ELEPHANT -> val = Evaluator.V_ELEPHANT;
                    case PAWN -> val = Evaluator.V_PAWN;
                }
                if (p.color == color) myMaterial += val;
                else enemyMaterial += val;
            }
        }
        
        // 子力领先超过车+马
        return myMaterial - enemyMaterial > Evaluator.V_CHARIOT + Evaluator.V_HORSE;
    }
}
