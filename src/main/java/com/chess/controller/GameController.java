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
    private ChessEngine engine;
    private State    state;
    private Color    currentTurn;  // 红方先行
    private Piece    selectedPiece;
    private List<Move> validMoves;
    private Color    playerColor;  // 玩家执红
    private final Stack<Move> moveHistory = new Stack<>();  // 走子历史
    private boolean  canUndo = false;  // 是否可以悔棋

    /** 状态变化监听器 */
    public interface StatusListener {
        void onStatusChange(String status);
    }
    private StatusListener statusListener;
    public void setStatusListener(StatusListener listener) {
        this.statusListener = listener;
    }
    private void notifyStatus(String status) {
        if (statusListener != null) statusListener.onStatusChange(status);
    }

    public GameController(BoardCanvas canvas, Difficulty difficulty) {
        this.canvas      = canvas;
        this.board       = canvas.getBoard();
        this.engine      = new OurEngine(difficulty);
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

    /** 根据坐标找到board上的合法Move（用于AI线程返回后映射） */
    private Move findMoveByCoords(int fromRow, int fromCol, int toRow, int toCol) {
        Piece piece = board.get(fromRow, fromCol);
        if (piece == null) return null;
        List<Move> moves = MoveValidator.getLegalMoves(board, piece);
        moves = MoveValidator.filterMovesCausingOwnCheck(board, moves);
        for (Move m : moves) {
            if (m.toRow == toRow && m.toCol == toCol) return m;
        }
        return null;
    }

    private void executeMove(Piece piece, int toRow, int toCol) {
        Move move = findMove(piece, toRow, toCol);
        if (move == null) return;

        move.execute(board);
        moveHistory.push(move);
        canUndo = true;
        clearSelection();

        if (checkGameOver()) return;

        currentTurn = currentTurn.opposite();
        canvas.draw();

        if (currentTurn == aiColor()) {
            notifyStatus("AI思考中...");
            startAIThinking();
        }
    }

    private Color aiColor() {
        return playerColor == Color.RED ? Color.BLACK : Color.RED;
    }

    private void startAIThinking() {
        state = State.AI_THINKING;
        Board boardCopy = board.copy();

        Thread aiThread = new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException e) {}

            Move resultMove = engine.getBestMove(boardCopy, aiColor());

            Platform.runLater(() -> {
                Move actualMove = findMoveByCoords(
                    resultMove.fromRow, resultMove.fromCol,
                    resultMove.toRow, resultMove.toCol
                );
                if (actualMove != null) {
                    actualMove.execute(board);
                    moveHistory.push(actualMove);
                    canvas.draw();

                    if (!checkGameOver()) {
                        currentTurn = currentTurn.opposite();
                        state = State.PLAYER_TURN;
                        notifyStatus("红方回合");
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
            notifyStatus("游戏结束");
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
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setTitle("游戏结束");
        alert.showAndWait();
    }

    public void restart(Difficulty difficulty) {
        engine.shutdown();
        this.board  = new Board();
        this.board.init();
        this.engine  = new OurEngine(difficulty);
        this.state = State.PLAYER_TURN;
        this.currentTurn = Color.RED;
        notifyStatus("红方回合");
        this.selectedPiece = null;
        this.validMoves.clear();
        this.moveHistory.clear();
        this.canUndo = false;
        canvas.setBoard(board);
        canvas.draw();
    }

    /** 悔棋：撤销AI的步和玩家的步，回到玩家走子前 */
    public void undoMove() {
        if (moveHistory.isEmpty() || !canUndo) return;
        if (state == State.AI_THINKING || state == State.GAME_OVER) return;

        // 如果AI已经走了（当前轮到玩家且history >= 2），先撤销AI的步
        if (currentTurn == playerColor && moveHistory.size() >= 2) {
            Move aiMove = moveHistory.pop();
            aiMove.undo(board);
        }

        // 撤销玩家的步
        if (!moveHistory.isEmpty()) {
            Move playerMove = moveHistory.pop();
            playerMove.undo(board);
        }

        // 更新状态
        if (moveHistory.isEmpty()) {
            canUndo = false;
        }
        state = State.PLAYER_TURN;
        currentTurn = playerColor;
        notifyStatus("红方回合");
        selectedPiece = null;
        validMoves.clear();
        canvas.clearSelection();
        canvas.draw();
    }

    public boolean canUndo() {
        return canUndo && !moveHistory.isEmpty() && state != State.AI_THINKING && state != State.GAME_OVER;
    }

    public State getState()       { return state; }
    public Color getCurrentTurn() { return currentTurn; }
}
