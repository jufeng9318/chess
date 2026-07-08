package com.chess.ai;

import com.chess.model.*;
import com.chess.pieces.*;

import java.util.*;

/**
 * 中国象棋开局库 — 扩充版
 * 内置常见开局变化，覆盖初始局面及若干后续变化
 */
public class OpeningBook {

    // 局面哈希 -> 候选着法列表（每个着法带评分，分数高者优先）
    private final Map<Long, List<BookMove>> book = new HashMap<>();

    public OpeningBook() {
        initBook();
    }

    /** 查询开局库，返回最佳库着法，无则返回null */
    public Move findBookMove(Board board, Color color) {
        long hash = computeHash(board, color);
        List<BookMove> moves = book.get(hash);
        if (moves == null || moves.isEmpty()) return null;

        // 按评分排序，优先返回高分变化
        moves.sort((a, b) -> Integer.compare(b.score, a.score));

        // 从前1/3的高分变化中随机选择（增加多样性）
        int topCount = Math.max(1, moves.size() / 3);
        BookMove bm = moves.get(new Random().nextInt(topCount));
        Piece piece = board.get(bm.fromRow, bm.fromCol);
        if (piece == null || piece.color != color) return null;

        return new Move(piece, bm.toRow, bm.toCol);
    }

    // ===================== 局面哈希 =====================

    private long computeHash(Board board, Color side) {
        long h = 0;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.get(r, c);
                if (p != null) {
                    h ^= (long) (p.type.ordinal() + 1) * (r * 9 + c + 1) * (p.color == Color.RED ? 1 : 31);
                }
            }
        }
        h ^= (side == Color.RED ? 0x12345678L : 0x9ABCDEF0L);
        return h;
    }

    // ===================== 初始化开局库 =====================

    private void initBook() {
        // 初始局面
        Board initial = new Board();
        initial.init();

        // ===== 红方开局（先行方）=====
        addMoves(initial, Color.RED,
                // 中炮（炮二平五 / 炮八平五）
                move(7, 1, 7, 4, 100),   // 中炮（炮二平五）
                move(7, 7, 7, 4, 100),   // 中炮（炮八平五）

                // 仙人指路
                move(6, 6, 5, 6, 80),    // 兵七进一
                move(6, 2, 5, 2, 80),    // 兵三进一

                // 飞相
                move(9, 6, 7, 4, 70),    // 相七进五
                move(9, 2, 7, 0, 70),    // 相三进五

                // 屏风马
                move(9, 7, 7, 6, 85),    // 马八进七
                move(9, 1, 7, 2, 85),    // 马二进三

                // 横车
                move(9, 0, 8, 0, 60),    // 车九进一
                move(9, 8, 8, 8, 60),    // 车一进一

                // 进兵
                move(6, 4, 5, 4, 50),    // 兵五进一
                move(6, 0, 5, 0, 40),    // 兵一进一
                move(6, 8, 5, 8, 40),    // 兵九进一
                move(6, 2, 5, 2, 45),    // 兵三进一（重复仙人指路，增加权重）
                move(6, 6, 5, 6, 45),    // 兵七进一（重复）

                // 进炮
                move(7, 1, 5, 1, 55),    // 炮二进四
                move(7, 7, 5, 7, 55),    // 炮八进四

                // 马进边
                move(9, 1, 7, 0, 40),    // 马二进一（边马，不太常见）
                move(9, 7, 7, 8, 40),    // 马八进九（边马）

                // 仕角
                move(9, 3, 8, 4, 35),    // 仕四进五
                move(9, 5, 8, 4, 35)     // 仕六进五
        );

        // ===== 黑方开局（后行方）=====
        addMoves(initial, Color.BLACK,
                // 屏风马（应对中炮）
                move(0, 7, 2, 6, 100),   // 马8进7
                move(0, 1, 2, 2, 100),   // 马2进3

                // 反架中炮（列手炮）
                move(2, 1, 2, 4, 90),    // 炮2平5
                move(2, 7, 2, 4, 90),    // 炮8平5

                // 飞象
                move(0, 6, 2, 4, 75),    // 象七进五
                move(0, 2, 2, 0, 75),    // 象三进五

                // 横车
                move(0, 0, 1, 0, 65),    // 车9进1
                move(0, 8, 1, 8, 65),    // 车1进1

                // 进卒
                move(3, 4, 4, 4, 60),    //卒5进一
                move(3, 0, 4, 0, 50),    //卒1进一
                move(3, 8, 4, 8, 50),    //卒9进一
                move(3, 2, 4, 2, 55),    //卒3进一
                move(3, 6, 4, 6, 55),    //卒7进一

                // 进炮
                move(2, 1, 4, 1, 55),    // 炮2进四
                move(2, 7, 4, 7, 55),    // 炮8进四

                // 马进边
                move(0, 1, 2, 0, 40),    // 马2进1
                move(0, 7, 2, 8, 40),    // 马8进9

                // 仕角
                move(0, 3, 1, 4, 35),    // 士4进5
                move(0, 5, 1, 4, 35)     // 士6进5
        );

        // ===== 后续变化（通过模拟走子生成局面哈希）=====
        initFollowUps(initial);
    }

    /** 添加后续变化：模拟常见开局序列 */
    private void initFollowUps(Board initial) {
        // 红方中炮 -> 黑方屏风马 -> 红方马八进七
        addSequence(initial, Color.RED,
                move(7, 1, 7, 4, 100),   // 红炮二平五
                move(0, 7, 2, 6, 100),   // 黑马8进7
                // 红方可选：
                move(9, 7, 7, 6, 95),    // 红马八进七（屏风马正
                move(9, 1, 7, 2, 90),    // 红马二进三
                move(9, 0, 8, 0, 85),    // 红车九进一（横车
                move(6, 6, 5, 6, 80)     // 红兵七进一
        );

        // 红方中炮 -> 黑方屏风马 -> 红方马八进七 -> 黑方马二进三
        addSequence(initial, Color.BLACK,
                move(7, 1, 7, 4, 100),   // 红炮二平五
                move(0, 7, 2, 6, 100),   // 黑马8进7
                move(9, 7, 7, 6, 95),    // 红马八进七
                move(0, 1, 2, 2, 100),   // 黑马2进3
                // 红方可选：
                move(9, 0, 8, 0, 90),    // 红车九进一
                move(6, 6, 5, 6, 85),    // 红兵七进一
                move(9, 1, 7, 2, 80)     // 红马二进三
        );

        // 仙人指路 -> 卒底炮
        addSequence(initial, Color.BLACK,
                move(6, 6, 5, 6, 80),    // 红兵七进一
                move(2, 1, 4, 1, 95),    // 黑炮2进四（卒底炮）
                // 红方可选：
                move(7, 1, 5, 1, 90),    // 红炮二进四
                move(9, 7, 7, 6, 85)     // 红马八进七
        );

        // 飞相 -> 飞象
        addSequence(initial, Color.BLACK,
                move(9, 6, 7, 4, 70),    // 红相七进五
                move(0, 6, 2, 4, 75),    // 黑象七进五
                // 红方可选：
                move(9, 7, 7, 6, 80),    // 红马八进七
                move(9, 1, 7, 2, 75),    // 红马二进三
                move(6, 6, 5, 6, 70)     // 红兵七进一
        );

        // 中炮 -> 屏风马 -> 横车 -> 平车
        addSequence(initial, Color.BLACK,
                move(7, 1, 7, 4, 100),   // 红炮二平五
                move(0, 7, 2, 6, 100),   // 黑马8进7
                move(9, 0, 8, 0, 85),    // 红车九进一
                // 黑方可选：
                move(0, 0, 1, 0, 90),    // 黑车9进1
                move(0, 8, 1, 8, 85),    // 黑车1进1
                move(2, 1, 2, 4, 80)     // 黑炮2平5
        );

        // 中炮对屏风马：红方五七炮
        addSequence(initial, Color.BLACK,
                move(7, 1, 7, 4, 100),   // 红炮二平五
                move(0, 7, 2, 6, 100),   // 黑马8进7
                move(9, 7, 7, 6, 95),    // 红马八进七
                move(0, 1, 2, 2, 95),    // 黑马2进3
                move(7, 7, 5, 7, 90),    // 红炮八进四（五七炮）
                // 黑方可选：
                move(3, 4, 4, 4, 85),    // 卒5进一
                move(3, 6, 4, 6, 80)     // 卒7进一
        );

        // 仙人指路 -> 卒底炮 -> 马二进三
        addSequence(initial, Color.BLACK,
                move(6, 6, 5, 6, 80),    // 红兵七进一
                move(2, 1, 4, 1, 95),    // 黑炮2进四
                move(9, 1, 7, 2, 90),    // 红马二进三
                // 黑方可选：
                move(0, 1, 2, 2, 85),    // 黑马2进3
                move(3, 6, 4, 6, 80)     // 卒7进一
        );

        // 飞相局 -> 反宫马
        addSequence(initial, Color.BLACK,
                move(9, 6, 7, 4, 70),    // 红相七进五
                move(0, 1, 2, 2, 90),    // 黑马2进3（反宫马）
                // 红方可选：
                move(9, 7, 7, 6, 85),    // 红马八进七
                move(9, 1, 7, 2, 80),    // 红马二进三
                move(6, 6, 5, 6, 75)     // 红兵七进一
        );

        // 中炮 -> 屏风马 -> 马八进七 -> 马二进三 -> 五八炮
        addSequence(initial, Color.BLACK,
                move(7, 1, 7, 4, 100),   // 红炮二平五
                move(0, 7, 2, 6, 100),   // 黑马8进7
                move(9, 7, 7, 6, 95),    // 红马八进七
                move(0, 1, 2, 2, 95),    // 黑马2进3
                move(7, 1, 5, 1, 90),    // 红炮二进四（五八炮）
                // 黑方可选：
                move(3, 4, 4, 4, 85),    // 卒5进一
                move(3, 2, 4, 2, 80)     // 卒3进一
        );

        // 仙人指路 -> 卒底炮 -> 马八进七
        addSequence(initial, Color.BLACK,
                move(6, 6, 5, 6, 80),    // 红兵七进一
                move(2, 1, 4, 1, 95),    // 黑炮2进四
                move(9, 7, 7, 6, 90),    // 红马八进七
                // 黑方可选：
                move(0, 7, 2, 6, 85),    // 黑马8进7
                move(0, 1, 2, 2, 80)     // 黑马2进3
        );

        // 仙人指路 -> 卒底炮 -> 马二进三 -> 兵三进一
        addSequence(initial, Color.BLACK,
                move(6, 2, 5, 2, 80),    // 红兵三进一
                move(2, 7, 4, 7, 95),    // 黑炮8进四
                move(9, 1, 7, 2, 90),    // 红马二进三
                // 黑方可选：
                move(0, 1, 2, 2, 85),    // 黑马2进3
                move(0, 7, 2, 6, 80)     // 黑马8进7
        );

        // 中炮 -> 屏风马 -> 马八进七 -> 马二进三 -> 横车
        addSequence(initial, Color.BLACK,
                move(7, 1, 7, 4, 100),   // 红炮二平五
                move(0, 7, 2, 6, 100),   // 黑马8进7
                move(9, 7, 7, 6, 95),    // 红马八进七
                move(0, 1, 2, 2, 95),    // 黑马2进3
                move(9, 0, 8, 0, 90),    // 红车九进一
                // 黑方可选：
                move(0, 0, 1, 0, 85),    // 黑车9进1
                move(2, 1, 2, 4, 80)     // 黑炮2平5
        );

        // 中炮 -> 屏风马 -> 马八进七 -> 马二进三 -> 直车
        addSequence(initial, Color.BLACK,
                move(7, 1, 7, 4, 100),   // 红炮二平五
                move(0, 7, 2, 6, 100),   // 黑马8进7
                move(9, 7, 7, 6, 95),    // 红马八进七
                move(0, 1, 2, 2, 95),    // 黑马2进3
                move(9, 8, 8, 8, 90),    // 红车一进一
                // 黑方可选：
                move(0, 8, 1, 8, 85),    // 黑车1进1
                move(2, 7, 2, 4, 80)     // 黑炮8平5
        );

        // 中炮 -> 屏风马 -> 马八进七 -> 马二进三 -> 兵三进一
        addSequence(initial, Color.BLACK,
                 move(7, 1, 7, 4, 100),   // 红炮二平五
                move(0, 7, 2, 6, 100),   // 黑马8进7
                move(9, 7, 7, 6, 95),    // 红马八进七
                move(0, 1, 2, 2, 95),    // 黑马2进3
                move(6, 2, 5, 2, 90),    // 红兵三进一
                // 黑方可选：
                move(3, 6, 4, 6, 85),    // 卒7进一
                move(3, 2, 4, 2, 80)     // 卒3进一
        );

        // 中炮 -> 屏风马 -> 马八进七 -> 马二进三 -> 兵七进一
        addSequence(initial, Color.BLACK,
                move(7, 1, 7, 4, 100),   // 红炮二平五
                move(0, 7, 2, 6, 100),   // 黑马8进7
                move(9, 7, 7, 6, 95),    // 红马八进七
                move(0, 1, 2, 2, 95),    // 黑马2进3
                move(6, 6, 5, 6, 90),    // 红兵七进一
                // 黑方可选：
                move(3, 6, 4, 6, 85),    // 卒7进一
                move(3, 4, 4, 4, 80)     // 卒5进一
        );

        // 飞相 -> 飞象 -> 马八进七 -> 马二进三
        addSequence(initial, Color.BLACK,
                move(9, 6, 7, 4, 70),    // 红相七进五
                move(0, 6, 2, 4, 75),    // 黑象七进五
                move(9, 7, 7, 6, 85),    // 红马八进七
                move(0, 1, 2, 2, 85),    // 黑马2进3
                // 红方可选：
                move(9, 1, 7, 2, 80),    // 红马二进三
                move(6, 6, 5, 6, 75)     // 红兵七进一
        );

        // 飞相 -> 飞象 -> 马八进七 -> 马二进三 -> 兵三进一
        addSequence(initial, Color.BLACK,
                move(9, 6, 7, 4, 70),    // 红相七进五
                move(0, 6, 2, 4, 75),    // 黑象七进五
                move(9, 7, 7, 6, 85),    // 红马八进七
                move(0, 1, 2, 2, 85),    // 黑马2进3
                move(9, 1, 7, 2, 80),    // 红马二进三
                // 黑方可选：
                move(0, 7, 2, 6, 75),    // 黑马8进7
                move(3, 2, 4, 2, 70)     // 卒3进一
        );

        // 仙人指路 -> 卒底炮 -> 马八进七 -> 马二进三
        addSequence(initial, Color.BLACK,
                move(6, 6, 5, 6, 80),    // 红兵七进一
                move(2, 1, 4, 1, 95),    // 黑炮2进四
                move(9, 7, 7, 6, 90),    // 红马八进七
                move(0, 1, 2, 2, 85),    // 黑马2进3
                // 红方可选：
                move(9, 1, 7, 2, 80),    // 红马二进三
                move(7, 1, 5, 1, 75)     // 红炮二进四
        );

        // 仙人指路 -> 卒底炮 -> 马二进三 -> 兵七进一
        addSequence(initial, Color.BLACK,
                move(6, 2, 5, 2, 80),    // 红兵三进一
                move(2, 7, 4, 7, 95),    // 黑炮8进四
                move(9, 1, 7, 2, 90),    // 红马二进三
                move(0, 7, 2, 6, 85),    // 黑马8进7
                // 红方可选：
                move(6, 6, 5, 6, 80),    // 红兵七进一
                move(9, 7, 7, 6, 75)     // 红马八进七
        );

        // 中炮 -> 屏风马 -> 马八进七 -> 马二进三 -> 炮二平七
        addSequence(initial, Color.BLACK,
                move(7, 1, 7, 4, 100),   // 红炮二平五
                move(0, 7, 2, 6, 100),   // 黑马8进7
                move(9, 7, 7, 6, 95),    // 红马八进七
                move(0, 1, 2, 2, 95),    // 黑马2进3
                move(7, 1, 7, 7, 90),    // 红炮二平八
                // 黑方可选：
                move(3, 6, 4, 6, 85),    // 卒7进一
                move(3, 4, 4, 4, 80)     // 卒5进一
        );

        // 中炮 -> 屏风马 -> 马八进七 -> 马二进三 -> 炮二平六（过宫炮）
        addSequence(initial, Color.BLACK,
                move(7, 1, 7, 4, 100),   // 红炮二平五
                move(0, 7, 2, 6, 100),   // 黑马8进7
                move(9, 7, 7, 6, 95),    // 红马八进七
                move(0, 1, 2, 2, 95),    // 黑马2进3
                move(7, 1, 7, 6, 90),    // 红炮二平六（过宫炮）
                // 黑方可选：
                move(3, 6, 4, 6, 85),    // 卒7进一
                move(0, 0, 1, 0, 80)     // 黑车9进1
        );

        // 中炮 -> 屏风马 -> 马八进七 -> 马二进三 -> 车九平四
        addSequence(initial, Color.BLACK,
                move(7, 1, 7, 4, 100),   // 红炮二平五
                move(0, 7, 2, 6, 100),   // 黑马8进7
                move(9, 7, 7, 6, 95),    // 红马八进七
                move(0, 1, 2, 2, 95),    // 黑马2进3
                move(9, 0, 8, 0, 90),    // 红车九进一
                move(8, 0, 8, 4, 85),    // 红车九平五（车九平四不对，是平到五路）
                // 黑方可选：
                move(0, 0, 1, 0, 80),    // 黑车9进1
                move(2, 1, 2, 4, 75)     // 黑炮2平5
        );
    }

    // ===================== 辅助方法 =====================

    private void addMoves(Board board, Color side, BookMove... moves) {
        long hash = computeHash(board, side);
        for (BookMove m : moves) {
            book.computeIfAbsent(hash, k -> new ArrayList<>()).add(m);
        }
    }

    /** 模拟开局序列，为每个局面添加后续着法 */
    private void addSequence(Board start, Color firstToMove, BookMove... moves) {
        Board board = start.copy();
        Color side = firstToMove;

        for (BookMove m : moves) {
            long hash = computeHash(board, side);
            book.computeIfAbsent(hash, k -> new ArrayList<>()).add(m);

            // 模拟走子
            Piece piece = board.get(m.fromRow, m.fromCol);
            if (piece != null) {
                board.set(m.toRow, m.toCol, piece);
                board.set(m.fromRow, m.fromCol, null);
            }
            side = (side == Color.RED) ? Color.BLACK : Color.RED;
        }
    }

    private static BookMove move(int fromRow, int fromCol, int toRow, int toCol, int score) {
        return new BookMove(fromRow, fromCol, toRow, toCol, score);
    }

    private void addBookMove(long hash, int fromRow, int fromCol, int toRow, int toCol) {
        book.computeIfAbsent(hash, k -> new ArrayList<>()).add(new BookMove(fromRow, fromCol, toRow, toCol, 50));
    }

    private static class BookMove {
        final int fromRow, fromCol, toRow, toCol;
        final int score;

        BookMove(int fromRow, int fromCol, int toRow, int toCol, int score) {
            this.fromRow = fromRow;
            this.fromCol = fromCol;
            this.toRow = toRow;
            this.toCol = toCol;
            this.score = score;
        }
    }
}
