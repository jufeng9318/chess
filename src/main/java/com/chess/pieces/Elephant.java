package com.chess.pieces;

import com.chess.model.*;

public class Elephant extends Piece {
    public Elephant(Color color, int row, int col) {
        super(PieceType.ELEPHANT, color, row, col);
    }

    @Override
    public String getChar() {
        return color == Color.RED ? "相" : "象";
    }
}
