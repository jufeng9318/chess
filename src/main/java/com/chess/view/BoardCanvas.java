package com.chess.view;

import com.chess.model.Board;
import com.chess.model.Color;
import com.chess.model.Move;
import com.chess.model.Piece;
import javafx.scene.canvas.*;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Stop;
import javafx.scene.text.*;

import java.util.List;

public class BoardCanvas extends Canvas {
    private static final double CELL_SIZE    = 60.0;
    private static final double PADDING      = 40.0;
    private static final double PIECE_RADIUS = CELL_SIZE * 0.45;

    private Board           board;
    private Piece           selectedPiece;
    private List<Move>      validMoves = List.of();

    // 通过 classpath 资源 URL 加载字体（全局注册，最可靠）
    private static final Font RED_FONT   = loadFont("/fonts/simkai.ttf", "KaiTi",   28);
    private static final Font BLACK_FONT = loadFont("/fonts/simfang.ttf", "FangSong", 28);

    private static Font loadFont(String resourcePath, String family, double size) {
        try {
            var url = BoardCanvas.class.getResource(resourcePath);
            if (url != null) {
                // Font.loadFont(String url, double size) 全局注册字体，返回的 Font 可直接使用
                Font f = Font.loadFont(url.toExternalForm(), size);
                System.out.println("[Font] Loaded: " + resourcePath + " -> " + (f != null ? f.getName() : "NULL"));
                if (f != null) return f;
            } else {
                System.err.println("[Font] Resource not found: " + resourcePath);
            }
        } catch (Exception e) {
            System.err.println("[Font] FAILED: " + resourcePath + " -> " + e);
        }
        return Font.font("Arial Unicode MS", FontWeight.BOLD, size);
    }

    public BoardCanvas(double width, double height) {
        super(width, height);
        this.board = new Board();
        this.board.init();
        draw();
    }

    public double getCellSize() { return CELL_SIZE; }
    public double getPadding()  { return PADDING; }

    public double toX(int col) { return PADDING + col * CELL_SIZE; }
    public double toY(int row) { return PADDING + row * CELL_SIZE; }

    public int toRow(double y) { return (int) Math.round((y - PADDING) / CELL_SIZE); }
    public int toCol(double x) { return (int) Math.round((x - PADDING) / CELL_SIZE); }

    public void setBoard(Board board)             { this.board = board; draw(); }
    public Board getBoard()                       { return board; }
    public void setSelected(Piece piece)          { this.selectedPiece = piece; draw(); }
    public void setValidMoves(List<Move> moves)   { this.validMoves = moves; draw(); }
    public void clearSelection()                  { this.selectedPiece = null; this.validMoves = List.of(); draw(); }

    public void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());
        drawBackground(gc);
        drawBoardLines(gc);
        drawMarks(gc);
        drawPieces(gc);
        drawHighlights(gc);
    }

    // === 木质纹理背景 ===
    private void drawBackground(GraphicsContext gc) {
        // 木质单色底
        gc.setFill(javafx.scene.paint.Color.web("#C4954A"));
        gc.fillRect(0, 0, getWidth(), getHeight());

        // 木纹线条
        gc.setStroke(javafx.scene.paint.Color.web("#B8853A", 0.15));
        gc.setLineWidth(1);
        for (double x = 0; x < getWidth(); x += 15) {
            gc.strokeLine(x, 0, x, getHeight());
        }
    }

    // === 棋盘网格线 ===
    private void drawBoardLines(GraphicsContext gc) {
        gc.setStroke(javafx.scene.paint.Color.web("#5C3A1E"));
        gc.setLineWidth(2.0);

        double left  = PADDING;
        double right = PADDING + 8 * CELL_SIZE;

        // 10条横线
        for (int r = 0; r <= 9; r++) {
            double y = PADDING + r * CELL_SIZE;
            gc.strokeLine(left, y, right, y);
        }

        // 竖线（楚河汉界处断开）
        for (int c = 0; c <= 8; c++) {
            double x = PADDING + c * CELL_SIZE;
            if (c == 0 || c == 8) {
                gc.strokeLine(x, PADDING, x, PADDING + 9 * CELL_SIZE);
            } else {
                gc.strokeLine(x, PADDING, x, PADDING + 4 * CELL_SIZE);
                gc.strokeLine(x, PADDING + 5 * CELL_SIZE, x, PADDING + 9 * CELL_SIZE);
            }
        }

        // 九宫格斜线
        drawPalace(gc, 3, 5, 0, 2);
        drawPalace(gc, 3, 5, 7, 9);
    }

    private void drawPalace(GraphicsContext gc, int c1, int c2, int r1, int r2) {
        double x1 = toX(c1), x2 = toX(c2);
        double y1 = toY(r1), y2 = toY(r2);
        gc.strokeLine(x1, y1, x2, y2);
        gc.strokeLine(x1, y2, x2, y1);
    }

    // === 楚河汉界 + 炮/兵位置标记 ===
    private void drawMarks(GraphicsContext gc) {
        Font chFont = Font.font("KaiTi", FontWeight.BOLD, 22);
        gc.setFont(chFont);
        gc.setFill(javafx.scene.paint.Color.web("#5C3A1E"));
        double midY = toY(4) + 8;
        gc.fillText("楚 河", toX(1) + 5,  midY);
        gc.fillText("漢 界", toX(5) + 15, midY);

        int[][] marks = {
            {3,0},{3,2},{3,4},{3,6},{3,8},
            {6,0},{6,2},{6,4},{6,6},{6,8},
            {2,1},{2,7},{7,1},{7,7}
        };
        for (int[] m : marks) drawPositionMark(gc, m[0], m[1]);
    }

    private void drawPositionMark(GraphicsContext gc, int row, int col) {
        double cx = toX(col), cy = toY(row);
        gc.setStroke(javafx.scene.paint.Color.web("#5C3A1E"));
        gc.setLineWidth(1.5);
        double off = 5, len = 9;
        // 左上角
        gc.strokeLine(cx - off - len, cy - off, cx - off, cy - off);
        gc.strokeLine(cx - off, cy - off - len, cx - off, cy - off);
        // 右上角
        gc.strokeLine(cx + off, cy - off - len, cx + off, cy - off);
        gc.strokeLine(cx + off, cy - off, cx + off + len, cy - off);
        // 左下角
        gc.strokeLine(cx - off - len, cy + off, cx - off, cy + off);
        gc.strokeLine(cx - off, cy + off, cx - off, cy + off + len);
        // 右下角
        gc.strokeLine(cx + off, cy + off, cx + off + len, cy + off);
        gc.strokeLine(cx + off, cy + off, cx + off, cy + off + len);
    }

    // === 绘制所有棋子 ===
    private void drawPieces(GraphicsContext gc) {
        for (int r = 0; r < Board.ROWS; r++) {
            for (int c = 0; c < Board.COLS; c++) {
                Piece p = board.get(r, c);
                if (p != null) drawPiece(gc, p);
            }
        }
    }

    private void drawPiece(GraphicsContext gc, Piece p) {
        double cx = toX(p.col);
        double cy = toY(p.row);

        javafx.scene.paint.Color bg, textColor, borderColor;
        if (p.color == Color.RED) {
            bg        = javafx.scene.paint.Color.web("#E84040");
            textColor = javafx.scene.paint.Color.web("#FFF5E0");
            borderColor = javafx.scene.paint.Color.web("#8B0000");
        } else {
            bg        = javafx.scene.paint.Color.web("#1A1A3E");
            textColor = javafx.scene.paint.Color.web("#E0E0E0");
            borderColor = javafx.scene.paint.Color.web("#0D0D2B");
        }

        // 底色圆
        gc.setFill(bg);
        gc.fillOval(cx - PIECE_RADIUS, cy - PIECE_RADIUS, PIECE_RADIUS * 2, PIECE_RADIUS * 2);

        // 边框
        gc.setStroke(borderColor);
        gc.setLineWidth(2.5);
        gc.strokeOval(cx - PIECE_RADIUS, cy - PIECE_RADIUS, PIECE_RADIUS * 2, PIECE_RADIUS * 2);

        // 内圈装饰
        gc.setStroke(javafx.scene.paint.Color.web("#FFFFFF", 0.2));
        gc.setLineWidth(1);
        gc.strokeOval(cx - PIECE_RADIUS + 4, cy - PIECE_RADIUS + 4,
                      (PIECE_RADIUS - 4) * 2, (PIECE_RADIUS - 4) * 2);

        // 棋子文字（显式加载的中文字体）
        Font pieceFont = p.color == Color.RED ? RED_FONT : BLACK_FONT;
        gc.setFont(pieceFont);
        gc.setFill(textColor);

        Text text = new Text(p.getChar());
        text.setFont(pieceFont);
        double tw = text.getLayoutBounds().getWidth();
        double th = text.getLayoutBounds().getHeight();
        gc.fillText(p.getChar(), cx - tw / 2, cy + th / 3.0);
    }

    // === 高亮 ===
    private void drawHighlights(GraphicsContext gc) {
        if (selectedPiece != null) {
            gc.setStroke(javafx.scene.paint.Color.web("#FFD700"));
            gc.setLineWidth(3);
            double r = PIECE_RADIUS + 4;
            gc.strokeOval(toX(selectedPiece.col) - r, toY(selectedPiece.row) - r, r * 2, r * 2);
        }

        for (Move m : validMoves) {
            double cx = toX(m.toCol);
            double cy = toY(m.toRow);
            Piece target = board.get(m.toRow, m.toCol);

            if (target != null) {
                // 吃子：红圈
                gc.setStroke(javafx.scene.paint.Color.web("#FF4444"));
                gc.setLineWidth(3);
                double r = PIECE_RADIUS + 4;
                gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
            } else {
                // 空位：绿点
                gc.setFill(javafx.scene.paint.Color.web("#44FF44", 0.6));
                gc.fillOval(cx - 8, cy - 8, 16, 16);
            }
        }
    }
}
