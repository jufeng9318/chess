package com.chess;

import javafx.application.Application;
import javafx.stage.Stage;
import com.chess.view.GameView;

public class Main extends Application {
    @Override
    public void start(Stage stage) {
        GameView gameView = new GameView();
        gameView.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
