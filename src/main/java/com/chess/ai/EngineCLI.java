package com.chess.ai;

import com.chess.model.*;
import java.io.*;
import java.util.*;

/**
 * 引擎命令行包装器（供测试框架调用）
 *
 * 简单文本协议：
 *   ucci                          → ucciok
 *   setboard <FEN>                → ok
 *   setside <RED|BLACK>           → ok（设置下一步谁走）
 *   go <depth>                    → bestmove e2e4
 *   bench <depth>                 → 跑基准测试，输出 节点数/秒
 *   quit                          → 退出
 *
 * 运行方式：
 *   java -cp target/classes com.chess.ai.EngineCLI
 */
public class EngineCLI {
    private static Board board = new Board();
    private static ChessAI ai = new ChessAI(Difficulty.HARD);
    private static Color sideToMove = Color.RED;
    private static final Evaluator evaluator = new Evaluator();

    public static void main(String[] args) throws Exception {
        board.init();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out), true);

        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            try {
                if (line.equals("ucci")) {
                    out.println("ucciok");
                    out.println("id name OurChessAI");
                    out.println("id author GW");
                    out.println("option name Depth type spin default 8 min 2 max 12");
                    out.println("readyok");

                } else if (line.startsWith("setboard ")) {
                    String fen = line.substring("setboard ".length());
                    board = fenToBoard(fen);
                    sideToMove = Color.RED;
                    out.println("ok");

                } else if (line.startsWith("setside ")) {
                    String side = line.substring("setside ".length()).trim();
                    sideToMove = side.equalsIgnoreCase("BLACK") ? Color.BLACK : Color.RED;
                    out.println("ok");

                } else if (line.startsWith("go ")) {
                    String[] parts = line.split("\\s+");
                    int depth = 6;
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (parts[i].equals("depth")) {
                            depth = Math.max(2, Math.min(10, Integer.parseInt(parts[i + 1])));
                        }
                    }
                    long t0 = System.currentTimeMillis();
                    Move move = searchBest(board, sideToMove, depth);
                    long ms = System.currentTimeMillis() - t0;
                    if (move != null) {
                        out.println("bestmove " + toUci(move));
                        move.execute(board);
                        sideToMove = sideToMove.opposite();
                    } else {
                        out.println("nobestmove");
                    }

                } else if (line.equals("bench")) {
                    bench(out);

                } else if (line.equals("quit")) {
                    break;
                }
            } catch (Exception e) {
                out.println("error " + e.getMessage());
            }
        }
    }

    private static Move searchBest(Board b, Color color, int maxDepth) {
        List<Move> moves = MoveValidator.getAllLegalMoves(b, color);
        if (moves.isEmpty()) return null;

        // 按 MVV-LVA 排序
        moves.sort((a, b2) -> {
            int va = mvvScore(a);
            int vb = mvvScore(b2);
            return vb - va;
        });

        Move best = moves.get(0);
        int bestScore = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE + 1;
        int beta  = Integer.MAX_VALUE - 1;

        for (Move m : moves) {
            m.execute(b);
            int score = alphabeta(b, maxDepth - 1, alpha, beta, color.opposite(), 1);
            m.undo(b);
            if (score > bestScore) {
                bestScore = score;
                best = m;
            }
            alpha = Math.max(alpha, score);
        }
        return best;
    }

    private static int alphabeta(Board b, int depth, int alpha, int beta,
                                 Color color, int fold) {
        if (depth == 0) {
            int e = evaluator.evaluate(b);
            return color == Color.RED ? e : -e;
        }

        List<Move> moves = MoveValidator.getAllLegalMoves(b, color);
        if (moves.isEmpty()) {
            if (MoveValidator.isCheck(b, color)) {
                return color == Color.RED
                        ? -(90000 + fold * 50)
                        :  (90000 + fold * 50);
            }
            return 0;
        }

        // 吃子优先排序
        moves.sort((a, b2) -> {
            int va = mvvScore(a);
            int vb = mvvScore(b2);
            return vb - va;
        });

        if (color == Color.RED) {
            int best = Integer.MIN_VALUE;
            for (Move m : moves) {
                m.execute(b);
                int score = alphabeta(b, depth - 1, alpha, beta, Color.BLACK, fold + 1);
                m.undo(b);
                best = Math.max(best, score);
                alpha = Math.max(alpha, score);
                if (beta <= alpha) break;
            }
            return best;
        } else {
            int best = Integer.MAX_VALUE;
            for (Move m : moves) {
                m.execute(b);
                int score = alphabeta(b, depth - 1, alpha, beta, Color.RED, fold + 1);
                m.undo(b);
                best = Math.min(best, score);
                beta = Math.min(beta, score);
                if (beta <= alpha) break;
            }
            return best;
        }
    }

    private static int mvvScore(Move m) {
        if (m.captured == null) return 0;
        return evaluator.getBaseValue(m.captured.type) * 100
                - evaluator.getBaseValue(m.piece.type);
    }

    private static void bench(PrintWriter out) {
        Board b = new Board();
        b.init();
        long t0 = System.currentTimeMillis();
        int nodes = 0;

        for (int d = 1; d <= 4; d++) {
            long td = System.currentTimeMillis();
            List<Move> moves = MoveValidator.getAllLegalMoves(b, Color.RED);
            int best = Integer.MIN_VALUE;
            for (Move m : moves) {
                m.execute(b);
                int s = alphabeta(b, d - 1, Integer.MIN_VALUE + 1,
                        Integer.MAX_VALUE - 1, Color.BLACK, 1);
                m.undo(b);
                if (s > best) best = s;
            }
            long elapsed = System.currentTimeMillis() - td;
            nodes = countNodes(b, d, Color.RED);
            out.printf("depth=%d nodes=%d time=%dms nps=%d%n",
                    d, nodes, elapsed,
                    elapsed > 0 ? (nodes * 1000 / elapsed) : 0);
        }
        long total = System.currentTimeMillis() - t0;
        out.println("total: " + total + "ms");
    }

    private static int countNodes(Board b, int depth, Color color) {
        if (depth == 0) return 1;
        int count = 1;
        for (Move m : MoveValidator.getAllLegalMoves(b, color)) {
            m.execute(b);
            count += countNodes(b, depth - 1, color.opposite());
            m.undo(b);
        }
        return count;
    }

    // ===================== FEN 解析 =====================
    // FEN: rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1
    // 行从黑方底线(row0)到红方底线(row9)，每行右到左（col 8→0）
    private static Board fenToBoard(String fen) {
        Board b = new Board();
        for (int r = 0; r < Board.ROWS; r++)
            for (int c = 0; c < Board.COLS; c++)
                b.set(r, c, null);

        String[] parts = fen.trim().split("\\s+");
        String[] ranks = parts[0].split("/");

        for (int ri = 0; ri < ranks.length && ri < 10; ri++) {
            int r = ri; // row 0 = 黑底线
            int c = 8;  // 从右到左
            for (int i = 0; i < ranks[ri].length() && c >= 0; i++) {
                char ch = ranks[ri].charAt(i);
                if (Character.isDigit(ch)) {
                    c -= (ch - '0');
                } else {
                    Color color = Character.isUpperCase(ch) ? Color.RED : Color.BLACK;
                    PieceType type = fenCharToType(Character.toLowerCase(ch));
                    b.set(r, c, new Piece(type, color, r, c));
                    c--;
                }
            }
        }

        if (parts.length > 1 && parts[1].equals("b")) {
            sideToMove = Color.BLACK;
        } else {
            sideToMove = Color.RED;
        }
        return b;
    }

    private static PieceType fenCharToType(char ch) {
        return switch (ch) {
            case 'k' -> PieceType.KING;
            case 'r' -> PieceType.CHARIOT;
            case 'n' -> PieceType.HORSE;
            case 'b' -> PieceType.ELEPHANT;
            case 'a' -> PieceType.ADVISOR;
            case 'c' -> PieceType.CANNON;
            case 'p' -> PieceType.PAWN;
            default  -> PieceType.PAWN;
        };
    }

    // ICCS 坐标: a-i=col0-8, 0-9=row0-9
    private static String toUci(Move m) {
        return (char)('a' + m.fromCol) + "" + m.fromRow
                + (char)('a' + m.toCol)   + "" + m.toRow;
    }
}
