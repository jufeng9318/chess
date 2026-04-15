package com.chess.ai;

import com.chess.model.*;
import java.io.*;
import java.util.*;

/**
 * ElephantEye 引擎包装器
 * 通过 UCCI 协议与 ElephantEye.exe 通信
 *
 * UCCI 协议核心命令：
 *   ucci           → 握手
 *   position fen <FEN> → 设置局面
 *   go depth N     → 开始搜索
 *   quit           → 退出
 *
 * 关键 UCCI 响应：
 *   bestmove e2e7  → 返回最佳着法（ICCS 坐标）
 */
public class ElephantEyeEngine implements ChessEngine {
    private Process process;
    private BufferedReader reader;
    private PrintWriter writer;
    private boolean initialized = false;
    private final String exePath;
    private final int depth;

    public ElephantEyeEngine(String exePath, int depth) {
        this.exePath = exePath;
        this.depth = depth;
    }

    @Override
    public synchronized Move getBestMove(Board board, Color color) {
        if (!initialized) {
            if (!startProcess()) return null;
        }

        String fen = toFen(board, color);
        try {
            // 发送局面
            writer.println("position fen " + fen);
            writer.flush();

            // 发送搜索命令
            writer.println("go depth " + depth);
            writer.flush();

            // 等待最佳着法
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("bestmove ")) {
                    String moveStr = line.split("\\s+")[1]; // e2e7
                    return parseMove(moveStr, board, color);
                }
                // 处理超时或其他消息
                if (line.contains("nobestmove") || line.contains("error")) {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[ElephantEye] 错误: " + e.getMessage());
            restartProcess();
        }
        return null;
    }

    private boolean startProcess() {
        try {
            process = new ProcessBuilder(exePath).redirectErrorStream(true).start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(process.getOutputStream()), true);

            // UCCI 握手
            writer.println("ucci");
            writer.flush();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals("ucciok")) {
                    initialized = true;
                    System.out.println("[ElephantEye] 初始化成功，引擎路径: " + exePath);
                    return true;
                }
                if (line.contains("unknown") || line.contains("error")) {
                    System.err.println("[ElephantEye] UCCI 初始化失败: " + line);
                    return false;
                }
            }
        } catch (Exception e) {
            System.err.println("[ElephantEye] 启动失败: " + e.getMessage());
        }
        return false;
    }

    private void restartProcess() {
        shutdown();
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        initialized = false;
        startProcess();
    }

    @Override
    public void shutdown() {
        try {
            if (writer != null) {
                writer.println("quit");
                writer.flush();
                writer.close();
            }
            if (reader != null) reader.close();
            if (process != null) process.destroy();
        } catch (Exception ignored) {}
        initialized = false;
    }

    // ===================== FEN 转换 =====================
    // 中国象棋 FEN（红方视角）格式：
    //   每行从右到左（col 8→0），用数字表示空格
    //   行顺序：黑方底线(0) → 红方底线(9)
    //   大写=红方，小写=黑方
    //   r=车 n=马 b=象 a=仕 k=帥 c=炮 p=兵（大写=红方）

    public static String toFen(Board board, Color sideToMove) {
        StringBuilder fen = new StringBuilder();

        for (int r = 0; r < Board.ROWS; r++) {
            int empty = 0;
            for (int c = Board.COLS - 1; c >= 0; c--) { // 从右到左
                Piece p = board.get(r, c);
                if (p == null) {
                    empty++;
                } else {
                    if (empty > 0) {
                        fen.append(empty);
                        empty = 0;
                    }
                    fen.append(toFenChar(p));
                }
            }
            if (empty > 0) fen.append(empty);
            if (r < Board.ROWS - 1) fen.append('/');
        }

        // 轮到哪方走
        fen.append(' ').append(sideToMove == Color.RED ? 'r' : 'b');
        // 剩余字段（简化处理）
        fen.append(" - 0 1");
        return fen.toString();
    }

    private static char toFenChar(Piece p) {
        char ch = switch (p.type) {
            case KING -> 'k';
            case CHARIOT -> 'r';
            case HORSE -> 'n';
            case ELEPHANT -> 'b';
            case ADVISOR -> 'a';
            case CANNON -> 'c';
            case PAWN -> 'p';
        };
        return p.color == Color.RED ? Character.toUpperCase(ch) : ch;
    }

    // ===================== 解析引擎返回的着法 =====================
    // ElephantEye 返回 ICCS 坐标：e2e7
    // 列: a-i (col 0-8)，行: 0-9 (row 0-9)
    // e = col 4, 2 = row 2, 7 = row 7
    // from=(row=2, col=4), to=(row=7, col=4) → 炮2进5（纵向）

    private static Move parseMove(String moveStr, Board board, Color color) {
        if (moveStr == null || moveStr.length() != 4) return null;

        try {
            int fromCol = moveStr.charAt(0) - 'a';
            int fromRow = moveStr.charAt(1) - '0';
            int toCol   = moveStr.charAt(2) - 'a';
            int toRow   = moveStr.charAt(3) - '0';

            // ICCS row 0-9 = board row 0-9（ElephantEye 和我们都用同一坐标系）
            Piece piece = board.get(fromRow, fromCol);
            if (piece == null || piece.color != color) return null;

            // 从合法着法中找匹配的
            for (Move m : MoveValidator.getLegalMoves(board, piece)) {
                if (m.toRow == toRow && m.toCol == toCol) {
                    // 深拷贝，确保 undo 正确
                    return new Move(piece, toRow, toCol);
                }
            }
        } catch (Exception e) {
            System.err.println("[ElephantEye] 解析着法失败: " + moveStr + " -> " + e.getMessage());
        }
        return null;
    }
}
