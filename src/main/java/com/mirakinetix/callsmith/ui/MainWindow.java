package com.mirakinetix.callsmith.ui;

import com.mirakinetix.callsmith.application.collection.CollectionWorkspaceService;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImportPersistenceService;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImportService;
import com.mirakinetix.callsmith.application.environment.EnvironmentService;
import com.mirakinetix.callsmith.application.environment.EnvironmentVariableService;
import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.ui.collection.CollectionSidebar;
import com.mirakinetix.callsmith.ui.environment.EnvironmentManager;
import com.mirakinetix.callsmith.ui.request.RequestBuilderView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.concurrent.ExecutorService;

public final class MainWindow {

    private static final double WIDTH = 1200;
    private static final double HEIGHT = 750;

    private final HttpRequestService httpRequestService;
    private final EnvironmentService environmentService;
    private final EnvironmentVariableService environmentVariableService;
    private final CollectionWorkspaceService collectionWorkspaceService;
    private final CollectionImportService collectionImportService;
    private final CollectionImportPersistenceService collectionImportPersistenceService;
    private final ExecutorService requestExecutor;

    public MainWindow(
            HttpRequestService httpRequestService,
            EnvironmentService environmentService,
            EnvironmentVariableService environmentVariableService,
            CollectionWorkspaceService collectionWorkspaceService,
            CollectionImportService collectionImportService,
            CollectionImportPersistenceService collectionImportPersistenceService,
            ExecutorService requestExecutor
    ) {
        this.httpRequestService =
                httpRequestService;

        this.environmentService =
                environmentService;

        this.environmentVariableService =
                environmentVariableService;

        this.collectionWorkspaceService =
                collectionWorkspaceService;

        this.collectionImportService =
                collectionImportService;

        this.collectionImportPersistenceService =
                collectionImportPersistenceService;

        this.requestExecutor =
                requestExecutor;
    }

    public void show(Stage stage) {
        RequestBuilderView requestBuilderView =
                new RequestBuilderView(
                        httpRequestService,
                        environmentService,
                        environmentVariableService,
                        requestExecutor
                );

        EnvironmentManager environmentManager =
                new EnvironmentManager(
                        environmentService,
                        environmentVariableService,
                        requestBuilderView::refreshEnvironments
                );

        requestBuilderView.setManageEnvironmentsAction(
                () ->
                        environmentManager.show(stage)
        );

        CollectionSidebar collectionSidebar =
                new CollectionSidebar(
                        collectionWorkspaceService,
                        collectionImportService,
                        collectionImportPersistenceService,
                        requestBuilderView::loadRequest
                );

        Label productName =
                new Label(
                        "Callsmith"
                );

        Label brandName =
                new Label(
                        "by Mirakinetix"
                );

        Label tagline =
                new Label(
                        "Build. Automate. Scale."
                );

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

        SplitPane workspace =
                new SplitPane(
                        collectionSidebar,
                        requestBuilderView
                );

        workspace.setDividerPositions(
                0.23
        );

        BorderPane root =
                new BorderPane();

        root.setTop(
                branding
        );

        root.setCenter(
                workspace
        );

        Scene scene =
                new Scene(
                        root,
                        WIDTH,
                        HEIGHT
                );

        stage.setTitle(
                "Callsmith - by Mirakinetix"
        );

        stage.setScene(
                scene
        );

        stage.show();
    }
}