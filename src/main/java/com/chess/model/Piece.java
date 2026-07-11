package com.chess.model;

public class Piece {
    public final PieceType type;
    public final Color color;
    public int row;
    public int col;

    public Piece(PieceType type, Color color, int row, int col) {
        this.type = type;
        this.color = color;
        this.row = row;
        this.col = col;
    }

    public String getChar() {
        return switch (type) {
            case KING -> "帥";
            case ADVISOR -> "仕";
            case ELEPHANT -> "相";
            case HORSE -> "馬";
            case CHARIOT -> "車";
            case CANNON -> "砲";
            case PAWN -> "兵";
        };
    }

    /** 深拷贝 */
    public Piece copy() {
        return new Piece(type, color, row, col);
    }

    @Override
    public String toString() {
        return color + " " + type;
    }
}
