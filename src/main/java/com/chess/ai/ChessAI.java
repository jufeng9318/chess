package com.chess.ai;

import com.chess.model.*;
import java.util.*;

public class ChessAI {
    private final Difficulty difficulty;
    private final Evaluator  evaluator;
    private int nodesSearched = 0;

    // Killer moves: moves that caused beta cutoffs at specific depths
    private final Map<Integer, List<Move>> killerMoves = new HashMap<>();

    public ChessAI(Difficulty difficulty) {
        this.difficulty = difficulty;
        this.evaluator  = new Evaluator();
    }

    public Move getBestMove(Board board, Color color) {
        nodesSearched = 0;
        killerMoves.clear();

        List<Move> moves = MoveValidator.getAllLegalMoves(board, color);
        if (moves.isEmpty()) return null;

        // 启发式排序：吃子着法优先（MVV-LVA），其次 killer moves
        moves.sort((a, b) -> getMoveScore(b, color, difficulty.depth) - getMoveScore(a, color, difficulty.depth));

        Move bestMove  = moves.get(0);
        int  bestScore = Integer.MIN_VALUE;
        int  alpha     = Integer.MIN_VALUE;
        int  beta      = Integer.MAX_VALUE;

        for (Move move : moves) {
            move.execute(board);
            int score = -alphaBeta(board, difficulty.depth - 1, alpha, beta, color.opposite(), false, 1);
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

    private int alphaBeta(Board board, int depth, int alpha, int beta, Color color, boolean maximizing, int fold) {
        nodesSearched++;

        if (depth == 0) {
            return evaluator.evaluate(board);
        }

        List<Move> moves = MoveValidator.getAllLegalMoves(board, color);
        if (moves.isEmpty()) {
            if (MoveValidator.isCheck(board, color)) {
                // 将死
                return maximizing ? Integer.MIN_VALUE + 10000 * fold : Integer.MAX_VALUE - 10000 * fold;
            }
            return 0; // 和棋
        }

        // 深度越深，搜索的着法越多（叶节点可以多搜一些）
        int moveLimit = moves.size() > 12 ? 10 : (moves.size() > 6 ? 8 : moves.size());
        if (moves.size() > moveLimit) {
            moves.sort((a, b) -> getMoveScore(b, color, depth) - getMoveScore(a, color, depth));
            moves = new ArrayList<>(moves.subList(0, moveLimit));
        } else {
            moves.sort((a, b) -> getMoveScore(b, color, depth) - getMoveScore(a, color, depth));
        }

        if (maximizing) {
            int maxScore = Integer.MIN_VALUE;
            for (int i = 0; i < moves.size(); i++) {
                Move move = moves.get(i);
                move.execute(board);
                int score = alphaBeta(board, depth - 1, alpha, beta, color.opposite(), false, fold + 1);
                move.undo(board);
                maxScore = Math.max(maxScore, score);
                alpha = Math.max(alpha, score);
                if (beta <= alpha) {
                    // 记录 killer move（非吃子着法）
                    if (move.captured == null) {
                        killerMoves.computeIfAbsent(depth, k -> new ArrayList<>()).add(move);
                    }
                    break;
                }
            }
            return maxScore;
        } else {
            int minScore = Integer.MAX_VALUE;
            for (int i = 0; i < moves.size(); i++) {
                Move move = moves.get(i);
                move.execute(board);
                int score = alphaBeta(board, depth - 1, alpha, beta, color.opposite(), true, fold + 1);
                move.undo(board);
                minScore = Math.min(minScore, score);
                beta = Math.min(beta, score);
                if (beta <= alpha) {
                    if (move.captured == null) {
                        killerMoves.computeIfAbsent(depth, k -> new ArrayList<>()).add(move);
                    }
                    break;
                }
            }
            return minScore;
        }
    }

    /** MVV-LVA: Most Valuable Victim - Least Valuable Attacker */
    private int getMoveScore(Move move, Color color, int depth) {
        int score = 0;

        // 吃子着法优先：牺牲小子吃大子得分高
        if (move.captured != null) {
            int victimScore = getBaseValue(move.captured.type);
            int attackerScore = getBaseValue(move.piece.type);
            score = victimScore * 10 - attackerScore;
        }

        // Killer move 加成（导致剪枝的着法）
        List<Move> killers = killerMoves.get(depth);
        if (killers != null && killers.contains(move)) {
            score += 50;
        }

        return score;
    }

    private int getBaseValue(PieceType type) {
        return switch (type) {
            case KING      -> 10000;
            case CHARIOT   -> 1000;
            case CANNON    -> 450;
            case HORSE     -> 450;
            case ADVISOR   -> 200;
            case ELEPHANT  -> 200;
            case PAWN      -> 100;
        };
    }
}
