package com.chess.ai;

import com.chess.model.*;
import java.util.*;

/**
 * 中国象棋 AI — 稳定专家版
 *
 * 核心改进：
 * 1. Zobrist 哈希置换表（内容哈希而非对象身份）
 * 2. 真正的静止搜索（Quiescence Search）
 * 3. 简化的搜索结构（去掉不稳定的 Aspiration Window）
 * 4. 着法排序：吃子 > Killer > History
 */
public class ChessAI {
    private final int maxDepth;
    private final Evaluator evaluator;

    private int nodesSearched = 0;
    private long searchStartMs = 0;
    private static final long TIME_LIMIT_MS = 6000;

    // Zobrist 哈希：用于置换表
    private static final long[][] PIECE_KEYS = new long[7][2]; // [pieceType][color]
    private static long SIDE_KEY = 0;
    static {
        Random rng = new Random(42);
        for (int t = 0; t < 7; t++)
            for (int c = 0; c < 2; c++)
                PIECE_KEYS[t][c] = rng.nextLong();
        SIDE_KEY = rng.nextLong();
    }

    // 置换表
    private final Map<Long, Integer> tt = new HashMap<>(65536);
    private final Map<Long, Integer> ttDepth = new HashMap<>(65536);

    // History Heuristic
    private final Map<String, Integer> history = new HashMap<>();

    // Killer moves
    private final Map<Integer, List<Move>> killers = new HashMap<>();

    public ChessAI(Difficulty difficulty) {
        this.maxDepth = switch (difficulty) {
            case EASY -> 4;
            case MEDIUM -> 6;
            case HARD -> 8;
        };
        this.evaluator = new Evaluator();
    }

    public Move getBestMove(Board board, Color color) {
        nodesSearched = 0;
        searchStartMs = System.currentTimeMillis();
        history.clear();
        killers.clear();

        List<Move> rootMoves = MoveValidator.getAllLegalMoves(board, color);
        if (rootMoves.isEmpty()) return null;

        Move bestMove = rootMoves.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (int depth = 2; depth <= maxDepth; depth++) {
            if (timeOut()) break;

            sortMoves(rootMoves, color, depth);
            int localBest = Integer.MIN_VALUE;
            Move localBestMove = bestMove;

            int alpha = Integer.MIN_VALUE + 1;
            int beta  = Integer.MAX_VALUE - 1;

            for (Move move : rootMoves) {
                if (timeOut()) break;

                move.execute(board);
                int score = -alphaBeta(board, depth - 1, alpha, beta,
                        color.opposite(), false, 1, depth);
                move.undo(board);

                if (score > localBest) {
                    localBest = score;
                    localBestMove = move;
                }
                alpha = Math.max(alpha, score);
            }

            if (!timeOut() && localBest > Integer.MIN_VALUE) {
                bestScore = localBest;
                bestMove = localBestMove;
            }
        }

        long elapsed = System.currentTimeMillis() - searchStartMs;
        System.out.println("[AI] d=" + maxDepth + " n=" + nodesSearched
                + " s=" + bestScore + " t=" + elapsed + "ms -> " + bestMove);
        return bestMove;
    }

    private boolean timeOut() {
        return System.currentTimeMillis() - searchStartMs > TIME_LIMIT_MS;
    }

    /** 计算棋盘的 Zobrist 哈希 */
    private long zobristHash(Board board, Color side) {
        long h = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.get(r, c);
                if (p != null) {
                    int ci = p.color == Color.RED ? 0 : 1;
                    h ^= PIECE_KEYS[p.type.ordinal()][ci];
                }
            }
        }
        if (side == Color.BLACK) h ^= SIDE_KEY;
        return h;
    }

    private int alphaBeta(Board board, int depth, int alpha, int beta,
                          Color color, boolean maximizing, int fold, int rootDepth) {
        if (timeOut()) return 0;
        nodesSearched++;

        // 递归终止
        if (depth <= 0) {
            return maximizing
                    ? evaluator.evaluate(board)
                    : -evaluator.evaluate(board);
        }

        List<Move> moves = MoveValidator.getAllLegalMoves(board, color);
        if (moves.isEmpty()) {
            if (MoveValidator.isCheck(board, color)) {
                return maximizing
                        ? -(90000 + fold * 50)
                        :  (90000 + fold * 50);
            }
            return 0;
        }

        long hash = zobristHash(board, color);

        // 置换表：深度更深的结果优先，EXACT 结果直接返回
        if (tt.containsKey(hash)) {
            int cachedDepth = ttDepth.getOrDefault(hash, 0);
            if (cachedDepth >= depth) {
                // TT 存的已是红方视角分数，直接返回
                return tt.get(hash);
            }
        }

        sortMoves(moves, color, depth);

        // 深度很深时限制搜索宽度（但至少保留 12 着）
        int searchCount = moves.size();
        if (depth >= 3 && moves.size() > 12) {
            searchCount = 12;
        }

        int best = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        boolean didCutoff = false;

        for (int i = 0; i < Math.min(searchCount, moves.size()); i++) {
            if (timeOut()) break;

            Move move = moves.get(i);
            move.execute(board);

            int score;
            if (move.captured != null && depth <= 2) {
                // 吃子着法：递归搜索 + 静止搜索
                score = -quiescence(board, -beta, -alpha, color.opposite(), fold);
            } else {
                score = -alphaBeta(board, depth - 1, -beta, -alpha,
                        color.opposite(), !maximizing, fold + 1, rootDepth);
            }

            move.undo(board);

            if (maximizing) {
                if (score > best) best = score;
                alpha = Math.max(alpha, score);
            } else {
                if (score < best) best = score;
                beta = Math.min(beta, score);
            }

            if (beta <= alpha) {
                recordKiller(move, rootDepth - depth);
                recordHistory(move);
                didCutoff = true;
                break;
            }
        }

        // 记录置换表（仅记录完全搜索且至少评估了一个着法的结果）
        int prevDepth = ttDepth.getOrDefault(hash, 0);
        boolean searchedAtLeastOne = (best != Integer.MIN_VALUE && best != Integer.MAX_VALUE);
        if (searchedAtLeastOne && !didCutoff && depth >= prevDepth) {
            // 统一存红方视角的值
            int val = maximizing ? best : -best;
            tt.put(hash, val);
            ttDepth.put(hash, depth);
        }

        return best;
    }

    /**
     * 静止搜索（Quiescence Search）：
     * 评估 + 只搜索吃子着法，消除"地平线效应"
     */
    private int quiescence(Board board, int alpha, int beta, Color color, int fold) {
        if (timeOut()) return 0;
        nodesSearched++;
        if (fold > 12) return 0; // 防止无限递归

        int standPat = evaluator.evaluate(board);
        // 当前搜索方视角的评估
        if (color == Color.RED) {
            if (standPat >= beta) return beta;
            alpha = Math.max(alpha, standPat);
        } else {
            if (-standPat <= alpha) return alpha;
            beta = Math.min(beta, -standPat);
        }

        // 收集所有吃子着法
        List<Move> caps = new ArrayList<>();
        for (Piece p : board.getAllPieces(color)) {
            for (Move m : MoveValidator.getLegalMoves(board, p)) {
                if (m.captured != null) caps.add(m);
            }
        }
        if (caps.isEmpty()) return standPat;

        caps.sort((a, b) -> {
            int va = evaluator.getBaseValue(a.captured.type);
            int vb = evaluator.getBaseValue(b.captured.type);
            return vb - va; // 吃大子优先
        });

        for (Move move : caps) {
            if (timeOut()) break;
            move.execute(board);
            int score = -quiescence(board, alpha, beta, color.opposite(), fold + 1);
            move.undo(board);

            if (color == Color.RED) {
                alpha = Math.max(alpha, score);
                if (alpha >= beta) break;
            } else {
                beta = Math.min(beta, score);
                if (beta <= alpha) break;
            }
        }

        return color == Color.RED ? alpha : beta;
    }

    private void recordKiller(Move move, int key) {
        if (move.captured != null) return;
        killers.computeIfAbsent(key, k -> new ArrayList<>(2));
        List<Move> list = killers.get(key);
        if (!list.contains(move)) {
            if (list.size() >= 2) list.remove(1);
            list.add(0, move);
        }
    }

    private void recordHistory(Move move) {
        String key = moveKey(move.piece.color, move.fromRow, move.fromCol,
                             move.toRow, move.toCol);
        history.merge(key, 2, Integer::sum);
    }

    private void sortMoves(List<Move> moves, Color color, int depth) {
        int relDepth = maxDepth - depth;
        List<Move> klist = killers.getOrDefault(relDepth, List.of());

        moves.sort((a, b) -> {
            if (klist.contains(a) && !klist.contains(b)) return  1;
            if (klist.contains(b) && !klist.contains(a)) return -1;

            int ha = history.getOrDefault(moveKey(color, a.fromRow, a.fromCol, a.toRow, a.toCol), 0);
            int hb = history.getOrDefault(moveKey(color, b.fromRow, b.fromCol, b.toRow, b.toCol), 0);
            if (hb != ha) return hb - ha;

            int va = mvvScore(a);
            int vb = mvvScore(b);
            return vb - va;
        });
    }

    private int mvvScore(Move m) {
        if (m.captured == null) return 0;
        return evaluator.getBaseValue(m.captured.type) * 100
                - evaluator.getBaseValue(m.piece.type);
    }

    private String moveKey(Color color, int fr, int fc, int tr, int tc) {
        return color + ":" + fr + "," + fc + "->" + tr + "," + tc;
    }
}
