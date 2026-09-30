package com.mirakinetix.callsmith.ui;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class MainWindow {

    private static final double WIDTH = 1000;
    private static final double HEIGHT = 700;

    public void show(Stage stage) {
        Label title = new Label("Callsmith");

        StackPane root = new StackPane(title);
        Scene scene = new Scene(root, WIDTH, HEIGHT);

        stage.setTitle("Callsmith");
        stage.setScene(scene);
        stage.show();
    }
}
