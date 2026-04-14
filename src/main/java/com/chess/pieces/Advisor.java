package com.chess.pieces;

import com.chess.model.*;

public class Advisor extends Piece {
    public Advisor(Color color, int row, int col) {
        super(PieceType.ADVISOR, color, row, col);
    }

    @Override
    public String getChar() {
        return color == Color.RED ? "仕" : "士";
    }
}
