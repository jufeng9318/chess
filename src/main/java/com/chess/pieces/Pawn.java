package com.chess.pieces;

import com.chess.model.*;

public class Pawn extends Piece {
    public Pawn(Color color, int row, int col) {
        super(PieceType.PAWN, color, row, col);
    }

    @Override
    public String getChar() {
        return color == Color.RED ? "兵" : "卒";
    }
}
