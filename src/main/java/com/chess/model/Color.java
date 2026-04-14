package com.chess.model;

public enum Color {
    RED,
    BLACK;

    public Color opposite() {
        return this == RED ? BLACK : RED;
    }
}
