package com.chess.view;

import com.chess.ai.Difficulty;
import com.chess.controller.GameController;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.Stage;

public class GameView {

    // 棋盘尺寸：10行×9列，单元格60px，边距40px
    // 宽度  = 40*2 + 8*60 = 560
    // 高度  = 40*2 + 9*60 = 620
    private static final double BOARD_WIDTH  = 560;
    private static final double BOARD_HEIGHT = 620;

    private Stage         stage;
    private BoardCanvas   boardCanvas;
    private GameController controller;
    private Label         statusLabel;

    public void show() {
        stage = new Stage();
        stage.setTitle("中国象棋");
        stage.setResizable(false);

        boardCanvas = new BoardCanvas(BOARD_WIDTH, BOARD_HEIGHT);

        Difficulty defaultDifficulty = Difficulty.MEDIUM;
        controller = new GameController(boardCanvas, defaultDifficulty);

        // 鼠标点击选子/落子
        boardCanvas.setOnMouseClicked(e -> {
            int row = boardCanvas.toRow(e.getY());
            int col = boardCanvas.toCol(e.getX());
            if (row >= 0 && row < 10 && col >= 0 && col < 9) {
                controller.onCellClick(row, col);
            }
        });

        // === 顶部工具栏 ===
        ComboBox<Difficulty> difficultyBox = new ComboBox<>();
        difficultyBox.getItems().addAll(Difficulty.values());
        difficultyBox.setValue(defaultDifficulty);
        difficultyBox.setMinWidth(120);

        Button newGameBtn = new Button("新游戏");
        newGameBtn.setMinWidth(80);
        newGameBtn.setOnAction(e -> {
            Difficulty selected = difficultyBox.getValue();
            controller.restart(selected);
        });

        Button undoBtn = new Button("悔棋");
        undoBtn.setMinWidth(80);
        undoBtn.setOnAction(e -> {
            controller.undoMove();
        });

        Label turnLabel = new Label("执红先行");
        turnLabel.setFont(Font.font("KaiTi", FontWeight.BOLD, 15));
        turnLabel.setTextFill(Color.web("#8B0000"));

        statusLabel = new Label("红方回合");
        statusLabel.setFont(Font.font("KaiTi", FontPosture.ITALIC, 13));
        statusLabel.setTextFill(Color.web("#5C3A1E"));

        // 注册状态监听器
        controller.setStatusListener(status -> {
            javafx.application.Platform.runLater(() -> statusLabel.setText(status));
        });

        HBox toolbar = new HBox(18);
        toolbar.setPadding(new Insets(10, 20, 10, 20));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getChildren().addAll(
            new Label("难度:"),
            difficultyBox,
            newGameBtn,
            undoBtn,
            new Separator(),
            turnLabel,
            new Separator(),
            statusLabel
        );
        toolbar.setStyle(
            "-fx-background-color: #F5DEB3;" +
            "-fx-border-color: #8B4513;" +
            "-fx-border-width: 0 0 1 0;"
        );

        // === 主布局 ===
        VBox root = new VBox();
        root.getChildren().addAll(toolbar, boardCanvas);
        root.setStyle("-fx-background-color: #F5DEB3;");

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();

        stage.setOnCloseRequest(e -> {
            javafx.application.Platform.exit();
        });
    }
}
