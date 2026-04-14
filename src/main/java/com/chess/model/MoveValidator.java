package com.chess.model;

import java.util.ArrayList;
import java.util.List;

public class MoveValidator {

    // === 辅助方法 ===

    public static boolean inPalace(Color color, int row, int col) {
        if (color == Color.RED) {
            return row >= 7 && row <= 9 && col >= 3 && col <= 5;
        } else {
            return row >= 0 && row <= 2 && col >= 3 && col <= 5;
        }
    }

    public static boolean inRiver(Color color, int row) {
        // 红方在 rows 7-9（棋盘下方），黑方在 rows 0-2（棋盘上方）
        if (color == Color.RED) return row >= 5;
        return row <= 4;
    }

    public static boolean isEmpty(Board board, int row, int col) {
        return board.get(row, col) == null;
    }

    public static boolean isEnemy(Board board, int row, int col, Color color) {
        Piece p = board.get(row, col);
        return p != null && p.color != color;
    }

    // === 各棋子移动规则 ===

    public static List<Move> getKingMoves(Board board, Piece king) {
        List<Move> moves = new ArrayList<>();
        int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
        for (int[] d : dirs) {
            int nr = king.row + d[0];
            int nc = king.col + d[1];
            if (inPalace(king.color, nr, nc)) {
                if (isEmpty(board, nr, nc) || isEnemy(board, nr, nc, king.color)) {
                    moves.add(new Move(king, nr, nc));
                }
            }
        }
        return moves;
    }

    public static List<Move> getAdvisorMoves(Board board, Piece advisor) {
        List<Move> moves = new ArrayList<>();
        int[][] dirs = {{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int[] d : dirs) {
            int nr = advisor.row + d[0];
            int nc = advisor.col + d[1];
            if (inPalace(advisor.color, nr, nc)) {
                if (isEmpty(board, nr, nc) || isEnemy(board, nr, nc, advisor.color)) {
                    moves.add(new Move(advisor, nr, nc));
                }
            }
        }
        return moves;
    }

    public static List<Move> getElephantMoves(Board board, Piece elephant) {
        List<Move> moves = new ArrayList<>();
        int[][] dirs = {{2,2},{2,-2},{-2,2},{-2,-2}};
        for (int[] d : dirs) {
            int nr = elephant.row + d[0];
            int nc = elephant.col + d[1];
            if (nr < 0 || nr >= Board.ROWS || nc < 0 || nc >= Board.COLS) continue;
            if (!inRiver(elephant.color, nr)) continue; // 不过河
            int eyeRow = (elephant.row + nr) / 2;
            int eyeCol = (elephant.col + nc) / 2;
            if (isEmpty(board, eyeRow, eyeCol)) {
                if (isEmpty(board, nr, nc) || isEnemy(board, nr, nc, elephant.color)) {
                    moves.add(new Move(elephant, nr, nc));
                }
            }
        }
        return moves;
    }

    public static List<Move> getHorseMoves(Board board, Piece horse) {
        List<Move> moves = new ArrayList<>();
        int[][] dirs = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for (int[] d : dirs) {
            int nr = horse.row + d[0];
            int nc = horse.col + d[1];
            if (nr < 0 || nr >= Board.ROWS || nc < 0 || nc >= Board.COLS) continue;
            int blockRow = horse.row + d[0] / 2;
            int blockCol = horse.col + d[1] / 2;
            if (!isEmpty(board, blockRow, blockCol)) continue; // 蹩马腿
            if (isEmpty(board, nr, nc) || isEnemy(board, nr, nc, horse.color)) {
                moves.add(new Move(horse, nr, nc));
            }
        }
        return moves;
    }

    public static List<Move> getChariotMoves(Board board, Piece chariot) {
        List<Move> moves = new ArrayList<>();
        int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
        for (int[] d : dirs) {
            int nr = chariot.row + d[0];
            int nc = chariot.col + d[1];
            while (nr >= 0 && nr < Board.ROWS && nc >= 0 && nc < Board.COLS) {
                if (isEmpty(board, nr, nc)) {
                    moves.add(new Move(chariot, nr, nc));
                } else {
                    if (isEnemy(board, nr, nc, chariot.color)) {
                        moves.add(new Move(chariot, nr, nc));
                    }
                    break;
                }
                nr += d[0];
                nc += d[1];
            }
        }
        return moves;
    }

    public static List<Move> getCannonMoves(Board board, Piece cannon) {
        List<Move> moves = new ArrayList<>();
        int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
        for (int[] d : dirs) {
            int nr = cannon.row + d[0];
            int nc = cannon.col + d[1];
            boolean jumped = false;
            while (nr >= 0 && nr < Board.ROWS && nc >= 0 && nc < Board.COLS) {
                if (!jumped) {
                    if (isEmpty(board, nr, nc)) {
                        moves.add(new Move(cannon, nr, nc));
                    } else {
                        jumped = true;
                    }
                } else {
                    if (isEnemy(board, nr, nc, cannon.color)) {
                        moves.add(new Move(cannon, nr, nc));
                        break; // 吃完敌方棋子后停止
                    }
                    // 遇到己方棋子后停止搜索（空格子继续）
                    if (!isEmpty(board, nr, nc)) break;
                }
                nr += d[0];
                nc += d[1];
            }
        }
        return moves;
    }

    public static List<Move> getPawnMoves(Board board, Piece pawn) {
        List<Move> moves = new ArrayList<>();
        int forward = pawn.color == Color.RED ? -1 : 1;
        int nr = pawn.row + forward;
        if (nr >= 0 && nr < Board.ROWS) {
            if (isEmpty(board, nr, pawn.col) || isEnemy(board, nr, pawn.col, pawn.color)) {
                moves.add(new Move(pawn, nr, pawn.col));
            }
        }
        if (inRiver(pawn.color, pawn.row)) {
            int[] cols = {pawn.col - 1, pawn.col + 1};
            for (int nc : cols) {
                if (nc >= 0 && nc < Board.COLS) {
                    if (isEnemy(board, pawn.row, nc, pawn.color)) {
                        moves.add(new Move(pawn, pawn.row, nc));
                    }
                }
            }
        }
        return moves;
    }

    // === 主方法：根据棋子类型获取合法着法 ===

    public static List<Move> getLegalMoves(Board board, Piece piece) {
        if (piece == null) return List.of();
        return switch (piece.type) {
            case KING -> getKingMoves(board, piece);
            case ADVISOR -> getAdvisorMoves(board, piece);
            case ELEPHANT -> getElephantMoves(board, piece);
            case HORSE -> getHorseMoves(board, piece);
            case CHARIOT -> getChariotMoves(board, piece);
            case CANNON -> getCannonMoves(board, piece);
            case PAWN -> getPawnMoves(board, piece);
        };
    }

    // === 检测将帅对面 ===
    public static boolean isKingsFacing(Board board) {
        Piece redKing = null, blackKing = null;
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.get(r, c);
                if (p != null && p.type == PieceType.KING) {
                    if (p.color == Color.RED) redKing = p;
                    else blackKing = p;
                }
            }
        }
        if (redKing == null || blackKing == null) return false;
        if (redKing.col != blackKing.col) return false;
        for (int r = blackKing.row + 1; r < redKing.row; r++) {
            if (board.get(r, redKing.col) != null) return false;
        }
        return true;
    }

    // === 过滤导致己方被将军的着法 ===
    public static List<Move> filterMovesCausingOwnCheck(Board board, List<Move> moves) {
        List<Move> filtered = new ArrayList<>();
        for (Move m : moves) {
            m.execute(board);
            boolean isCheck = isCheck(board, m.piece.color);
            m.undo(board);
            if (!isCheck) filtered.add(m);
        }
        return filtered;
    }

    // === 检测是否将军 ===
    public static boolean isCheck(Board board, Color color) {
        Piece king = null;
        outer:
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.get(r, c);
                if (p != null && p.type == PieceType.KING && p.color == color) {
                    king = p;
                    break outer;
                }
            }
        }
        if (king == null) return true; // 将帅被吃了

        Color enemy = (color == Color.RED) ? Color.BLACK : Color.RED;
        for (Piece p : board.getAllPieces(enemy)) {
            List<Move> moves = getLegalMoves(board, p);
            for (Move m : moves) {
                if (m.toRow == king.row && m.toCol == king.col) return true;
            }
        }
        return false;
    }

    // === 获取所有合法着法 ===
    public static List<Move> getAllLegalMoves(Board board, Color color) {
        List<Move> allMoves = new ArrayList<>();
        for (Piece p : board.getAllPieces(color)) {
            allMoves.addAll(getLegalMoves(board, p));
        }
        return filterMovesCausingOwnCheck(board, allMoves);
    }
}
