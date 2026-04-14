package com.chess.pieces;

import com.chess.model.*;

public class Cannon extends Piece {
    public Cannon(Color color, int row, int col) {
        super(PieceType.CANNON, color, row, col);
    }

    @Override
    public String getChar() {
        return color == Color.RED ? "砲" : "炮";
    }
}
