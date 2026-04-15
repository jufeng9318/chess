package com.chess.ai;

import com.chess.model.*;
import java.util.*;

/**
 * 中国象棋 AI — 专家增强版
 *
 * 核心改进：
 * 1. 修复整数溢出：best 初始化为 MIN_VALUE + 1（避免 -MIN_VALUE 溢出）
 * 2. Killer 深度计算正确（统一用 ply）
 * 3. 置换表键值包含轮走方（消除红黑同形混淆）
 * 4. 置换表支持 ALPHA/BETA/EXACT 三种类型
 * 5. PVS（主要变例搜索）+ LMR（延迟缩减）
 * 6. History Heuristic（历史启发）
 * 7. 改进的 Quiescence Search（静态搜索）
 * 8. 根搜索从 depth=3 开始（depth=2 只是评估，没有真正博弈）
 * 9. Aspiration Window（期望窗口）加速搜索
 */
public class ChessAI {
    private final int maxDepth;
    private final Evaluator evaluator;

    private int nodesSearched = 0;
    private long searchStartMs = 0;
    private static final long TIME_LIMIT_MS = 6000;

    // Zobrist 哈希
    private static final long[][] PIECE_KEYS = new long[7][2];
    private static final long SIDE_KEY;
    static {
        Random rng = new Random(42);
        for (int t = 0; t < 7; t++)
            for (int c = 0; c < 2; c++)
                PIECE_KEYS[t][c] = rng.nextLong();
        SIDE_KEY = rng.nextLong();
    }

    // 置换表（定长数组，比 HashMap 更快）
    private static final long TT_EMPTY = Long.MAX_VALUE;
    private final long[]  ttHash  = new long[65536];
    private final int[]   ttScore = new int[65536];
    private final byte[]  ttDepth = new byte[65536];
    private final byte[]  ttType  = new byte[65536];
    private static final int TT_ALPHA = 0, TT_BETA = 1, TT_EXACT = 2;

    // History Heuristic（每个格子一个分数）
    private final int[] historyRed   = new int[90];
    private final int[] historyBlack = new int[90];

    // Killer moves [ply][2]
    private final Move[][] killers = new Move[64][2];

    public ChessAI(Difficulty difficulty) {
        this.maxDepth = switch (difficulty) {
            case EASY -> 4;
            case MEDIUM -> 6;
            case HARD -> 8;
        };
        this.evaluator = new Evaluator();
        Arrays.fill(ttHash, TT_EMPTY);
    }

    public Move getBestMove(Board board, Color color) {
        nodesSearched = 0;
        searchStartMs = System.currentTimeMillis();
        Arrays.fill(historyRed,   0);
        Arrays.fill(historyBlack, 0);

        List<Move> rootMoves = MoveValidator.getAllLegalMoves(board, color);
        if (rootMoves.isEmpty()) return null;

        // 按 MVV-LVA 预排序
        rootMoves.sort((a, b) -> mvvScore(b) - mvvScore(a));

        Move bestMove = rootMoves.get(0);
        int bestScore = Integer.MIN_VALUE + 1;

        // 根搜索从深度3开始（depth=2 只有评估，没有 alpha-beta 比较）
        for (int depth = 3; depth <= maxDepth; depth++) {
            if (timeOut()) break;

            sortMoves(rootMoves, color, 0);

            int localBest  = Integer.MIN_VALUE + 1;
            Move localBestMove = bestMove;

            // Aspiration Window：以上一层分数为中心开窗口
            int window = depth >= 4 ? 30 : 50;
            int alpha = bestScore == Integer.MIN_VALUE + 1
                    ? Integer.MIN_VALUE + 1 : Math.max(Integer.MIN_VALUE + 1, bestScore - window);
            int beta  = bestScore == Integer.MIN_VALUE + 1
                    ? Integer.MAX_VALUE - 1 : Math.min(Integer.MAX_VALUE - 1, bestScore + window);

            for (Move move : rootMoves) {
                if (timeOut()) break;

                move.execute(board);
                int score = -alphaBeta(board, depth - 1, -beta, -alpha,
                        color.opposite(), 1);
                move.undo(board);

                if (score > localBest) {
                    localBest = score;
                    localBestMove = move;
                }

                // Aspiration 失败时扩大窗口重搜
                if (score <= alpha || score >= beta) {
                    int newAlpha = Integer.MIN_VALUE + 1;
                    int newBeta  = Integer.MAX_VALUE - 1;
                    move.execute(board);
                    score = -alphaBeta(board, depth - 1, newAlpha, newBeta,
                            color.opposite(), 1);
                    move.undo(board);
                    if (score > localBest) {
                        localBest = score;
                        localBestMove = move;
                    }
                }
            }

            if (!timeOut() && localBest > Integer.MIN_VALUE + 1) {
                bestScore = localBest;
                bestMove  = localBestMove;
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

    // ==================== Zobrist Hash ====================
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

    // ==================== Transposition Table ====================
    private int ttIdx(long hash) { return (int) (hash & 0xFFFF); }

    private int ttProbe(long hash, int depth, int alpha, int beta) {
        int idx = ttIdx(hash);
        if (ttHash[idx] != hash) return Integer.MIN_VALUE;
        if (ttDepth[idx] < depth) return Integer.MIN_VALUE;

        int s = ttScore[idx], t = ttType[idx];
        if (t == TT_EXACT) return s;
        if (t == TT_ALPHA && s <= alpha) return alpha;
        if (t == TT_BETA  && s >= beta)  return beta;
        return Integer.MIN_VALUE;
    }

    private void ttRecord(long hash, int depth, int score, int alpha, int beta) {
        int idx = ttIdx(hash);
        int type = score >= beta ? TT_BETA : (score <= alpha ? TT_ALPHA : TT_EXACT);
        if (ttDepth[idx] <= depth || ttHash[idx] != hash) {
            ttHash[idx]  = hash;
            ttScore[idx] = score;
            ttDepth[idx] = (byte) depth;
            ttType[idx]  = (byte) type;
        }
    }

    // ==================== History ====================
    private int historyGet(Move m) {
        return m.piece.color == Color.RED
                ? historyRed[m.fromRow * 9 + m.fromCol]
                : historyBlack[m.fromRow * 9 + m.fromCol];
    }

    private void historyAdd(Move m, int bonus) {
        int i = m.fromRow * 9 + m.fromCol;
        int[] arr = m.piece.color == Color.RED ? historyRed : historyBlack;
        arr[i] += bonus;
        if (arr[i] > 30000) {
            for (int j = 0; j < arr.length; j++) arr[j] >>= 1;
        }
    }

    // ==================== Alpha-Beta Search ====================
    private int alphaBeta(Board board, int depth, int alpha, int beta,
                          Color color, int ply) {
        if (timeOut()) return 0;
        nodesSearched++;

        List<Move> moves = MoveValidator.getAllLegalMoves(board, color);
        if (moves.isEmpty()) {
            if (MoveValidator.isCheck(board, color)) {
                return color == Color.RED
                        ? -(90000 + ply * 50)
                        :  (90000 + ply * 50);
            }
            return 0;
        }

        // Quiescence 搜索
        if (depth <= 0) {
            return quiescence(board, alpha, beta, color, ply);
        }

        long hash = zobristHash(board, color);

        // 置换表裁剪
        int ttScore = ttProbe(hash, depth, alpha, beta);
        if (ttScore != Integer.MIN_VALUE) return ttScore;

        sortMoves(moves, color, ply);

        // 深度很深时限制搜索宽度（至少保留 10 个）
        int searchLimit = moves.size();
        if (depth >= 4 && moves.size() > 12) {
            searchLimit = 12;
        }

        boolean isRed = (color == Color.RED);
        int best = isRed ? Integer.MIN_VALUE + 1 : Integer.MAX_VALUE - 1;

        for (int i = 0; i < searchLimit && i < moves.size(); i++) {
            if (timeOut()) break;
            Move move = moves.get(i);

            int score;
            if (i == 0) {
                // 第一个着法：全窗口搜索
                move.execute(board);
                score = -alphaBeta(board, depth - 1, -beta, -alpha,
                        color.opposite(), ply + 1);
                move.undo(board);
            } else {
                // LMR（延迟缩减）：浅一层搜索
                int r = (depth >= 3 && i >= 4) ? 2 : 1;
                move.execute(board);
                score = -alphaBeta(board, depth - 1 - r,
                        -alpha - 1, -alpha, color.opposite(), ply + 1);
                move.undo(board);
                // 好于 alpha 则全窗口验证
                if (score > alpha && score < beta) {
                    move.execute(board);
                    score = -alphaBeta(board, depth - 1,
                            -beta, -alpha, color.opposite(), ply + 1);
                    move.undo(board);
                }
            }

            if (isRed) {
                if (score > best) best = score;
                if (score > alpha) alpha = score;
            } else {
                if (score < best) best = score;
                if (score < beta)  beta  = score;
            }

            if (beta <= alpha) {
                if (move.captured == null) recordKiller(move, ply);
                historyAdd(move, depth * depth);
                break;
            }
        }

        ttRecord(hash, depth, best, alpha, beta);
        return best;
    }

    // ==================== Quiescence Search ====================
    private int quiescence(Board board, int alpha, int beta, Color color, int ply) {
        if (timeOut()) return 0;
        nodesSearched++;
        if (ply > 20) return evaluator.evaluate(board);

        int standPat = evaluator.evaluate(board);
        if (color == Color.RED) {
            if (standPat >= beta) return beta;
            alpha = Math.max(alpha, standPat);
        } else {
            if (-standPat <= alpha) return alpha;
            beta = Math.min(beta, -standPat);
        }

        List<Move> caps = new ArrayList<>();
        for (Piece p : board.getAllPieces(color)) {
            for (Move m : MoveValidator.getLegalMoves(board, p)) {
                if (m.captured != null) caps.add(m);
            }
        }
        if (caps.isEmpty()) return standPat;

        caps.sort((a, b) -> mvvScore(b) - mvvScore(a));

        for (Move move : caps) {
            if (timeOut()) break;
            move.execute(board);
            int score = -quiescence(board, alpha, beta, color.opposite(), ply + 1);
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

    // ==================== Move Ordering ====================
    private void recordKiller(Move move, int ply) {
        if (move.captured != null) return;
        if (ply < killers.length) {
            if (killers[ply][0] != move) {
                killers[ply][1] = killers[ply][0];
                killers[ply][0] = move;
            }
        }
    }

    private void sortMoves(List<Move> moves, Color color, int ply) {
        Move[] k = ply < killers.length ? killers[ply] : null;

        moves.sort((a, b) -> {
            // 1. 吃子优先
            int va = mvvScore(a), vb = mvvScore(b);
            if (vb != va) return vb - va;

            // 2. Killer
            if (k != null) {
                boolean aK = (a == k[0] || a == k[1]);
                boolean bK = (b == k[0] || b == k[1]);
                if (aK && !bK) return -1;
                if (bK && !aK) return  1;
            }

            // 3. History
            int ha = historyGet(a), hb = historyGet(b);
            if (hb != ha) return hb - ha;

            return 0;
        });
    }

    private int mvvScore(Move m) {
        if (m.captured == null) return 0;
        return evaluator.getBaseValue(m.captured.type) * 100
                - evaluator.getBaseValue(m.piece.type);
    }
}
