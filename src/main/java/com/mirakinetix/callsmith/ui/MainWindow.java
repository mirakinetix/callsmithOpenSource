package com.mirakinetix.callsmith.ui;

import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.ui.request.RequestBuilderView;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.concurrent.ExecutorService;

public final class MainWindow {

    private static final double WIDTH = 1000;
    private static final double HEIGHT = 700;

    private final HttpRequestService httpRequestService;
    private final ExecutorService requestExecutor;

    public MainWindow(
            HttpRequestService httpRequestService,
            ExecutorService requestExecutor
    ) {
        this.httpRequestService = httpRequestService;
        this.requestExecutor = requestExecutor;
    }

    public void show(Stage stage) {
        RequestBuilderView requestBuilderView =
                new RequestBuilderView(
                        httpRequestService,
                        requestExecutor
                );

        Scene scene =
                new Scene(requestBuilderView, WIDTH, HEIGHT);

        stage.setTitle("Callsmith");
        stage.setScene(scene);
        stage.show();
    }
}