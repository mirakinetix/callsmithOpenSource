package com.mirakinetix.callsmith;

import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.infrastructure.http.JavaHttpRequestExecutor;
import com.mirakinetix.callsmith.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CallsmithApplication extends Application {

    private ExecutorService requestExecutor;

    @Override
    public void start(Stage stage) {
        JavaHttpRequestExecutor httpRequestExecutor =
                new JavaHttpRequestExecutor();

        HttpRequestService httpRequestService =
                new HttpRequestService(httpRequestExecutor);

        requestExecutor =
                Executors.newVirtualThreadPerTaskExecutor();

        MainWindow mainWindow =
                new MainWindow(
                        httpRequestService,
                        requestExecutor
                );

        mainWindow.show(stage);
    }

    @Override
    public void stop() {
        if (requestExecutor != null) {
            requestExecutor.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}