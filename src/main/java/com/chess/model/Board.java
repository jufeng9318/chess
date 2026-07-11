package com.chess.model;

import com.chess.pieces.*;

import java.util.ArrayList;
import java.util.List;

public class Board {
    public static final int ROWS = 10;
    public static final int COLS = 9;

    private final Piece[][] grid;

    public Board() {
        grid = new Piece[ROWS][COLS];
    }

    public void init() {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                grid[r][c] = null;

        // 黑方（上方，row 0-4）
        grid[0][0] = new Chariot(Color.BLACK, 0, 0);
        grid[0][1] = new Horse(Color.BLACK, 0, 1);
        grid[0][2] = new Elephant(Color.BLACK, 0, 2);
        grid[0][3] = new Advisor(Color.BLACK, 0, 3);
        grid[0][4] = new King(Color.BLACK, 0, 4);
        grid[0][5] = new Advisor(Color.BLACK, 0, 5);
        grid[0][6] = new Elephant(Color.BLACK, 0, 6);
        grid[0][7] = new Horse(Color.BLACK, 0, 7);
        grid[0][8] = new Chariot(Color.BLACK, 0, 8);

        grid[2][1] = new Cannon(Color.BLACK, 2, 1);
        grid[2][7] = new Cannon(Color.BLACK, 2, 7);

        grid[3][0] = new Pawn(Color.BLACK, 3, 0);
        grid[3][2] = new Pawn(Color.BLACK, 3, 2);
        grid[3][4] = new Pawn(Color.BLACK, 3, 4);
        grid[3][6] = new Pawn(Color.BLACK, 3, 6);
        grid[3][8] = new Pawn(Color.BLACK, 3, 8);

        // 红方（下方，row 5-9）
        grid[9][0] = new Chariot(Color.RED, 9, 0);
        grid[9][1] = new Horse(Color.RED, 9, 1);
        grid[9][2] = new Elephant(Color.RED, 9, 2);
        grid[9][3] = new Advisor(Color.RED, 9, 3);
        grid[9][4] = new King(Color.RED, 9, 4);
        grid[9][5] = new Advisor(Color.RED, 9, 5);
        grid[9][6] = new Elephant(Color.RED, 9, 6);
        grid[9][7] = new Horse(Color.RED, 9, 7);
        grid[9][8] = new Chariot(Color.RED, 9, 8);

        grid[7][1] = new Cannon(Color.RED, 7, 1);
        grid[7][7] = new Cannon(Color.RED, 7, 7);

        grid[6][0] = new Pawn(Color.RED, 6, 0);
        grid[6][2] = new Pawn(Color.RED, 6, 2);
        grid[6][4] = new Pawn(Color.RED, 6, 4);
        grid[6][6] = new Pawn(Color.RED, 6, 6);
        grid[6][8] = new Pawn(Color.RED, 6, 8);
    }

    public Piece get(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return null;
        return grid[row][col];
    }

    public void set(int row, int col, Piece piece) {
        grid[row][col] = piece;
        if (piece != null) {
            piece.row = row;
            piece.col = col;
        }
    }

    public Board copy() {
        Board b = new Board();
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                b.grid[r][c] = (this.grid[r][c] != null) ? this.grid[r][c].copy() : null;
        return b;
    }

    public List<Piece> getAllPieces(Color color) {
        List<Piece> list = new ArrayList<>();
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                Piece p = grid[r][c];
                if (p != null && p.color == color) list.add(p);
            }
        return list;
    }
}
