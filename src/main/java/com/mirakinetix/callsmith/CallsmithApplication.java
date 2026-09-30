package com.mirakinetix.callsmith;

import com.mirakinetix.callsmith.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

public class CallsmithApplication extends Application {

    @Override
    public void start(Stage stage) {
        MainWindow mainWindow = new MainWindow();
        mainWindow.show(stage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
