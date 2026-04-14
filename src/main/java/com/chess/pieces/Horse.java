package com.chess.pieces;

import com.chess.model.*;

public class Horse extends Piece {
    public Horse(Color color, int row, int col) {
        super(PieceType.HORSE, color, row, col);
    }

    @Override
    public String getChar() {
        return "馬";
    }
}
