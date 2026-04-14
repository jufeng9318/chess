package com.chess.controller;

import com.chess.ai.*;
import com.chess.model.*;
import com.chess.view.BoardCanvas;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import java.util.*;

public class GameController {
    public enum State {
        PLAYER_TURN,
        AI_THINKING,
        GAME_OVER
    }

    private Board    board;
    private BoardCanvas canvas;
    private ChessAI  ai;
    private State    state;
    private Color    currentTurn;  // 红方先行
    private Piece    selectedPiece;
    private List<Move> validMoves;
    private Color    playerColor;  // 玩家执红

    public GameController(BoardCanvas canvas, Difficulty difficulty) {
        this.canvas      = canvas;
        this.board       = canvas.getBoard();
        this.ai          = new ChessAI(difficulty);
        this.state       = State.PLAYER_TURN;
        this.currentTurn = Color.RED;
        this.playerColor = Color.RED;
        this.validMoves  = new ArrayList<>();
    }

    public void onCellClick(int row, int col) {
        if (state != State.PLAYER_TURN || currentTurn != playerColor) return;

        Piece clicked = board.get(row, col);

        if (selectedPiece == null) {
            // 选子
            if (clicked != null && clicked.color == playerColor) {
                selectPiece(clicked);
            }
        } else {
            // 已有选中，尝试落子
            if (isValidMove(row, col)) {
                executeMove(selectedPiece, row, col);
            } else if (clicked != null && clicked.color == playerColor) {
                selectPiece(clicked);
            } else {
                clearSelection();
            }
        }
    }

    private void selectPiece(Piece piece) {
        selectedPiece = piece;
        validMoves = MoveValidator.getLegalMoves(board, piece);
        validMoves = MoveValidator.filterMovesCausingOwnCheck(board, validMoves);
        canvas.setSelected(piece);
        canvas.setValidMoves(validMoves);
    }

    private void clearSelection() {
        selectedPiece = null;
        validMoves.clear();
        canvas.clearSelection();
    }

    private boolean isValidMove(int row, int col) {
        for (Move m : validMoves) {
            if (m.toRow == row && m.toCol == col) return true;
        }
        return false;
    }

    private Move findMove(Piece piece, int toRow, int toCol) {
        for (Move m : validMoves) {
            if (m.piece == piece && m.toRow == toRow && m.toCol == toCol) return m;
        }
        return null;
    }

    private void executeMove(Piece piece, int toRow, int toCol) {
        Move move = findMove(piece, toRow, toCol);
        if (move == null) return;

        move.execute(board);
        clearSelection();

        if (checkGameOver()) return;

        currentTurn = currentTurn.opposite();
        canvas.draw();

        if (currentTurn == aiColor()) {
            startAIThinking();
        }
    }

    private Color aiColor() {
        return playerColor == Color.RED ? Color.BLACK : Color.RED;
    }

    private void startAIThinking() {
        state = State.AI_THINKING;

        Thread aiThread = new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException e) {}

            Move aiMove = ai.getBestMove(board, aiColor());

            Platform.runLater(() -> {
                if (aiMove != null) {
                    aiMove.execute(board);
                    canvas.draw();

                    if (!checkGameOver()) {
                        currentTurn = currentTurn.opposite();
                        state = State.PLAYER_TURN;
                    }
                } else {
                    state = State.GAME_OVER;
                    onGameOver(playerColor);
                }
            });
        });
        aiThread.start();
    }

    private boolean checkGameOver() {
        Color enemy = currentTurn.opposite();
        List<Move> enemyMoves = MoveValidator.getAllLegalMoves(board, enemy);

        if (enemyMoves.isEmpty()) {
            state = State.GAME_OVER;
            if (MoveValidator.isCheck(board, enemy)) {
                onGameOver(currentTurn);
            } else {
                onDraw();
            }
            return true;
        }
        return false;
    }

    private void onGameOver(Color winner) {
        String msg = (winner == playerColor)
            ? "恭喜！你赢了！" : "AI 获胜！再来一局？";
        showEndDialog(msg);
    }

    private void onDraw() {
        showEndDialog("和棋！");
    }

    private void showEndDialog(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("游戏结束");
        alert.setHeaderText(message);
        alert.showAndWait();
    }

    public void restart(Difficulty difficulty) {
        this.board  = new Board();
        this.board.init();
        this.ai     = new ChessAI(difficulty);
        this.state  = State.PLAYER_TURN;
        this.currentTurn = Color.RED;
        this.selectedPiece = null;
        this.validMoves.clear();
        canvas.setBoard(board);
        canvas.draw();
    }

    public State getState()       { return state; }
    public Color getCurrentTurn()  { return currentTurn; }
}
