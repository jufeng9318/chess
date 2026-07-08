package com.chess.ai;

import com.chess.model.*;
import java.util.*;

/**
 * 中国象棋开局库
 * 内置常见开局变化，前10步优先使用库着法
 */
public class OpeningBook {
    
    // 局面哈希 -> 候选着法列表
    private final Map<Long, List<BookMove>> book = new HashMap<>();
    
    public OpeningBook() {
        initBook();
    }
    
    /** 查询开局库，返回最佳库着法，无则返回null */
    public Move findBookMove(Board board, Color color) {
        long hash = computeHash(board, color);
        List<BookMove> moves = book.get(hash);
        if (moves == null || moves.isEmpty()) return null;
        
        // 随机选择一个变化
        BookMove bm = moves.get(new Random().nextInt(moves.size()));
        Piece piece = board.get(bm.fromRow, bm.fromCol);
        if (piece == null || piece.color != color) return null;
        
        Move move = new Move(piece, bm.toRow, bm.toCol);
        return move;
    }
    
    /** 简单的局面哈希（基于棋子位置） */
    private long computeHash(Board board, Color side) {
        long h = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.get(r, c);
                if (p != null) {
                    h ^= (long)(p.type.ordinal() + 1) * (r * 9 + c + 1) * (p.color == Color.RED ? 1 : 31);
                }
            }
        }
        h ^= (side == Color.RED ? 0x12345678L : 0x9ABCDEF0L);
        return h;
    }
    
    /** 初始化开局库 */
    private void initBook() {
        // 使用简单的初始局面哈希来存储开局
        long initialHash = computeInitialHash();
        
        // 中炮开局：炮二平五（炮从(7,1)到(7,4)）
        addBookMove(initialHash, 7, 1, 7, 4);
        // 屏风马：马八进七（马从(9,7)到(7,6)）
        addBookMove(initialHash, 9, 7, 7, 6);
        // 屏风马：马二进三（马从(9,1)到(7,2)）
        addBookMove(initialHash, 9, 1, 7, 2);
    }
    
    /** 计算初始局面哈希 */
    private long computeInitialHash() {
        long h = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = getInitialPiece(r, c);
                if (p != null) {
                    h ^= (long)(p.type.ordinal() + 1) * (r * 9 + c + 1) * (p.color == Color.RED ? 1 : 31);
                }
            }
        }
        h ^= (Color.RED == Color.RED ? 0x12345678L : 0x9ABCDEF0L);
        return h;
    }
    
    /** 获取初始局面指定位置的棋子 */
    private Piece getInitialPiece(int r, int c) {
        // 黑方（上方）
        if (r == 0) {
            if (c == 0 || c == 8) return new com.chess.pieces.Chariot(Color.BLACK, r, c);
            if (c == 1 || c == 7) return new com.chess.pieces.Horse(Color.BLACK, r, c);
            if (c == 2 || c == 6) return new com.chess.pieces.Elephant(Color.BLACK, r, c);
            if (c == 3 || c == 5) return new com.chess.pieces.Advisor(Color.BLACK, r, c);
            if (c == 4) return new com.chess.pieces.King(Color.BLACK, r, c);
        }
        if (r == 2 && (c == 1 || c == 7)) return new com.chess.pieces.Cannon(Color.BLACK, r, c);
        if (r == 3 && c % 2 == 0) return new com.chess.pieces.Pawn(Color.BLACK, r, c);
        
        // 红方（下方）
        if (r == 9) {
            if (c == 0 || c == 8) return new com.chess.pieces.Chariot(Color.RED, r, c);
            if (c == 1 || c == 7) return new com.chess.pieces.Horse(Color.RED, r, c);
            if (c == 2 || c == 6) return new com.chess.pieces.Elephant(Color.RED, r, c);
            if (c == 3 || c == 5) return new com.chess.pieces.Advisor(Color.RED, r, c);
            if (c == 4) return new com.chess.pieces.King(Color.RED, r, c);
        }
        if (r == 7 && (c == 1 || c == 7)) return new com.chess.pieces.Cannon(Color.RED, r, c);
        if (r == 6 && c % 2 == 0) return new com.chess.pieces.Pawn(Color.RED, r, c);
        
        return null;
    }

    private void addBookMove(long hash, int fromRow, int fromCol, int toRow, int toCol) {
        book.computeIfAbsent(hash, k -> new ArrayList<>()).add(new BookMove(fromRow, fromCol, toRow, toCol));
    }

    private static class BookMove {
        final int fromRow, fromCol, toRow, toCol;
        BookMove(int fromRow, int fromCol, int toRow, int toCol) {
            this.fromRow = fromRow;
            this.fromCol = fromCol;
            this.toRow = toRow;
            this.toCol = toCol;
        }
    }
}
