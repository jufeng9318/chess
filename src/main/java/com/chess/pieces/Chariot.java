package com.chess.pieces;

import com.chess.model.*;

public class Chariot extends Piece {
    public Chariot(Color color, int row, int col) {
        super(PieceType.CHARIOT, color, row, col);
    }

    @Override
    public String getChar() {
        return "車";
    }
}
