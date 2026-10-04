package com.mirakinetix.callsmith.ui;

import com.mirakinetix.callsmith.application.environment.EnvironmentService;
import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.ui.request.RequestBuilderView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.concurrent.ExecutorService;

public final class MainWindow {

    private static final double WIDTH = 1000;
    private static final double HEIGHT = 700;

    private final HttpRequestService httpRequestService;
    private final EnvironmentService environmentService;
    private final ExecutorService requestExecutor;

    public MainWindow(
            HttpRequestService httpRequestService,
            EnvironmentService environmentService,
            ExecutorService requestExecutor
    ) {
        this.httpRequestService =
                httpRequestService;

        this.environmentService =
                environmentService;

        this.requestExecutor =
                requestExecutor;
    }

    public void show(Stage stage) {
        RequestBuilderView requestBuilderView =
                new RequestBuilderView(
                        httpRequestService,
                        environmentService,
                        requestExecutor
                );

        Label productName =
                new Label("Callsmith");

        Label brandName =
                new Label("by Mirakinetix");

        Label tagline =
                new Label("Build. Automate. Scale.");

        HBox brandingLine =
                new HBox(
                        8,
                        productName,
                        brandName
                );

        brandingLine.setAlignment(
                Pos.CENTER_LEFT
        );

        VBox branding =
                new VBox(
                        2,
                        brandingLine,
                        tagline
                );

        branding.setPadding(
                new Insets(
                        10,
                        12,
                        8,
                        12
                )
        );

        BorderPane root =
                new BorderPane();

        root.setTop(branding);
        root.setCenter(requestBuilderView);

        Scene scene =
                new Scene(
                        root,
                        WIDTH,
                        HEIGHT
                );

        stage.setTitle(
                "Callsmith - by Mirakinetix"
        );

        stage.setScene(scene);
        stage.show();
    }
}