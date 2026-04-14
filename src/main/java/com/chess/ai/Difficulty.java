package com.chess.ai;

public enum Difficulty {
    EASY(2),
    MEDIUM(4),
    HARD(6);

    public final int depth;

    Difficulty(int depth) {
        this.depth = depth;
    }
}
