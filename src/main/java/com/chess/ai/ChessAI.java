package com.chess.ai;

import com.chess.model.*;
import java.util.*;

public class ChessAI {
    private final Difficulty difficulty;
    private final Evaluator  evaluator;
    private int nodesSearched = 0;

    public ChessAI(Difficulty difficulty) {
        this.difficulty = difficulty;
        this.evaluator  = new Evaluator();
    }

    public Move getBestMove(Board board, Color color) {
        nodesSearched = 0;
        List<Move> moves = MoveValidator.getAllLegalMoves(board, color);
        if (moves.isEmpty()) return null;

        // 启发式排序：吃子着法优先
        moves.sort((a, b) -> getMoveValue(b) - getMoveValue(a));

        Move bestMove  = moves.get(0);
        int  bestScore = Integer.MIN_VALUE;
        int  alpha     = Integer.MIN_VALUE;
        int  beta      = Integer.MAX_VALUE;

        for (Move move : moves) {
            move.execute(board);
            int score = -alphaBeta(board, difficulty.depth - 1, alpha, beta, color.opposite(), false);
            move.undo(board);

            if (score > bestScore) {
                bestScore = score;
                bestMove  = move;
            }
            alpha = Math.max(alpha, score);
        }

        System.out.println("[AI] 搜索节点数: " + nodesSearched + "  最优得分: " + bestScore);
        return bestMove;
    }

    private int alphaBeta(Board board, int depth, int alpha, int beta, Color color, boolean maximizing) {
        nodesSearched++;

        if (depth == 0) {
            return evaluator.evaluate(board);
        }

        List<Move> moves = MoveValidator.getAllLegalMoves(board, color);
        if (moves.isEmpty()) {
            if (MoveValidator.isCheck(board, color)) {
                // 将死
                return maximizing ? Integer.MIN_VALUE + 10000 : Integer.MAX_VALUE - 10000;
            }
            return 0; // 和棋
        }

        // 限制每层最多搜索的着法数（避免组合爆炸）
        if (moves.size() > 6) {
            moves.sort((a, b) -> getMoveValue(b) - getMoveValue(a));
            moves = new ArrayList<>(moves.subList(0, 6));
        }

        if (maximizing) {
            int maxScore = Integer.MIN_VALUE;
            for (Move move : moves) {
                move.execute(board);
                int score = alphaBeta(board, depth - 1, alpha, beta, color.opposite(), false);
                move.undo(board);
                maxScore = Math.max(maxScore, score);
                alpha = Math.max(alpha, score);
                if (beta <= alpha) break; // Alpha 剪枝
            }
            return maxScore;
        } else {
            int minScore = Integer.MAX_VALUE;
            for (Move move : moves) {
                move.execute(board);
                int score = alphaBeta(board, depth - 1, alpha, beta, color.opposite(), true);
                move.undo(board);
                minScore = Math.min(minScore, score);
                beta = Math.min(beta, score);
                if (beta <= alpha) break; // Beta 剪枝
            }
            return minScore;
        }
    }

    private int getMoveValue(Move move) {
        if (move.captured == null) return 0;
        return switch (move.captured.type) {
            case KING      -> 100000;
            case CHARIOT   -> 90;
            case CANNON, HORSE -> 40;
            case ADVISOR, ELEPHANT -> 20;
            case PAWN      -> 10;
        };
    }
}
