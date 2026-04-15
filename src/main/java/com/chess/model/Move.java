package com.chess.model;

import java.util.Objects;

public class Move {
    public final int fromRow, fromCol;
    public final int toRow, toCol;
    public final Piece piece;
    public Piece captured;

    public Move(Piece piece, int toRow, int toCol) {
        this.piece = piece;
        this.fromRow = piece.row;
        this.fromCol = piece.col;
        this.toRow = toRow;
        this.toCol = toCol;
    }

    public void execute(Board board) {
        this.captured = board.get(toRow, toCol);
        board.set(toRow, toCol, piece);
        board.set(fromRow, fromCol, null);
        piece.row = toRow;
        piece.col = toCol;
    }

    public void undo(Board board) {
        board.set(fromRow, fromCol, piece);
        board.set(toRow, toCol, captured);
        piece.row = fromRow;
        piece.col = fromCol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Move move = (Move) o;
        return fromRow == move.fromRow && fromCol == move.fromCol
                && toRow == move.toRow && toCol == move.toCol
                && piece.color == move.piece.color
                && piece.type == move.piece.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromRow, fromCol, toRow, toCol, piece.color, piece.type);
    }

    @Override
    public String toString() {
        return piece + " (" + fromCol + "," + fromRow + ") -> (" + toCol + "," + toRow + ")";
    }
}
