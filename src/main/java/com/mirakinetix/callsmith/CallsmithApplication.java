package com.mirakinetix.callsmith;

import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.infrastructure.http.JavaHttpRequestExecutor;
import com.mirakinetix.callsmith.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

public class CallsmithApplication extends Application {

    private HttpRequestService httpRequestService;

    @Override
    public void start(Stage stage) {
        JavaHttpRequestExecutor requestExecutor =
                new JavaHttpRequestExecutor();

        httpRequestService =
                new HttpRequestService(requestExecutor);

        MainWindow mainWindow = new MainWindow();
        mainWindow.show(stage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}