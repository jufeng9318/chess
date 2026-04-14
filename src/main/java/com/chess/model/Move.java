package com.chess.model;

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
    public String toString() {
        return piece + " (" + fromCol + "," + fromRow + ") -> (" + toCol + "," + toRow + ")";
    }
}
