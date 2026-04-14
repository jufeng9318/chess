package com.chess.pieces;

import com.chess.model.*;

public class King extends Piece {
    public King(Color color, int row, int col) {
        super(PieceType.KING, color, row, col);
    }

    @Override
    public String getChar() {
        return color == Color.RED ? "帥" : "將";
    }
}
