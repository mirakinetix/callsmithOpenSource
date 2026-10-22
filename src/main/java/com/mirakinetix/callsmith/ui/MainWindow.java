package com.mirakinetix.callsmith.ui;

import com.mirakinetix.callsmith.application.collection.CollectionExportService;
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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.ExecutorService;

public final class MainWindow {

    private static final double WIDTH = 1200;
    private static final double HEIGHT = 750;

    private static final String LINKEDIN_URL =
            "https://www.linkedin.com/in/devdutt-parab/";

    private static final String PERSONAL_WEBSITE_URL =
            "https://dparab.me/";

    private static final String MIRAKINETIX_WEBSITE_URL =
            "https://mirakinetix.com/";

    private final HttpRequestService httpRequestService;
    private final EnvironmentService environmentService;
    private final EnvironmentVariableService environmentVariableService;
    private final CollectionWorkspaceService collectionWorkspaceService;
    private final CollectionExportService collectionExportService;
    private final CollectionImportService collectionImportService;
    private final CollectionImportPersistenceService collectionImportPersistenceService;
    private final ExecutorService requestExecutor;

    public MainWindow(
            HttpRequestService httpRequestService,
            EnvironmentService environmentService,
            EnvironmentVariableService environmentVariableService,
            CollectionWorkspaceService collectionWorkspaceService,
            CollectionExportService collectionExportService,
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

        this.collectionExportService =
                collectionExportService;

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
                        collectionExportService,
                        collectionImportService,
                        collectionImportPersistenceService,
                        requestBuilderView::loadRequest
                );

        Label productName =
                new Label("Callsmith");

        productName.getStyleClass().add("product-name");

        Region headerSpacer =
                new Region();

        HBox.setHgrow(
                headerSpacer,
                Priority.ALWAYS
        );

        Button aboutButton =
                new Button("About");

        aboutButton.setTooltip(
                new Tooltip("About Callsmith and Mirakinetix")
        );

        aboutButton.setOnAction(
                event -> showAbout(stage)
        );

        HBox branding =
                new HBox(
                        12,
                        productName,
                        headerSpacer,
                        aboutButton
                );

        branding.setAlignment(Pos.CENTER_LEFT);
        branding.setPadding(new Insets(4, 16, 4, 16));
        branding.getStyleClass().add("brand-header");

        SplitPane workspace =
                new SplitPane(
                        collectionSidebar,
                        requestBuilderView
                );

        workspace.setDividerPositions(0.23);

        BorderPane root =
                new BorderPane();

        root.setTop(branding);
        root.setCenter(workspace);

        Scene scene =
                new Scene(
                        root,
                        WIDTH,
                        HEIGHT
                );

        stage.setTitle("Callsmith - by Mirakinetix");

        scene.getStylesheets().add(
                MainWindow.class
                        .getResource("/styles/brand.css")
                        .toExternalForm()
        );

        stage.setScene(scene);
        stage.show();
    }

    private void showAbout(Stage owner) {
        Label appName =
                new Label("Callsmith");

        appName.getStyleClass().add("product-name");

        Label companyName =
                new Label("By Mirakinetix");

        Label tagline =
                new Label("Build. Automate. Scale.");

        Label linksHeading =
                new Label("More about us");

        Hyperlink linkedIn =
                createLink(
                        "LinkedIn - Devdutt Parab",
                        LINKEDIN_URL
                );

        Hyperlink personalWebsite =
                createLink(
                        "Personal website - dparab.me",
                        PERSONAL_WEBSITE_URL
                );

        Hyperlink companyWebsite =
                createLink(
                        "Mirakinetix - mirakinetix.com",
                        MIRAKINETIX_WEBSITE_URL
                );

        VBox content =
                new VBox(
                        8,
                        appName,
                        companyName,
                        tagline,
                        linksHeading,
                        linkedIn,
                        personalWebsite,
                        companyWebsite
                );

        content.setPadding(new Insets(16));

        Dialog<ButtonType> dialog =
                new Dialog<>();

        dialog.initOwner(owner);
        dialog.setTitle("About Callsmith");
        dialog.setHeaderText(null);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(420);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.getDialogPane()
                .getStylesheets()
                .add(
                        MainWindow.class
                                .getResource("/styles/brand.css")
                                .toExternalForm()
                );

        dialog.showAndWait();
    }

    private Hyperlink createLink(
            String label,
            String url
    ) {
        Hyperlink hyperlink =
                new Hyperlink(label);

        hyperlink.setTooltip(
                new Tooltip(url)
        );

        hyperlink.setOnAction(
                event -> openExternalLink(url)
        );

        return hyperlink;
    }

    private void openExternalLink(String url) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException(
                        "Opening links is not supported on this system."
                );
            }

            Desktop desktop =
                    Desktop.getDesktop();

            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                throw new IllegalStateException(
                        "Opening web links is not supported on this system."
                );
            }

            desktop.browse(
                    URI.create(url)
            );

        } catch (Exception exception) {
            Alert alert =
                    new Alert(
                            Alert.AlertType.ERROR
                    );

            alert.setTitle("Unable to open link");
            alert.setHeaderText(null);
            alert.setContentText(
                    exception.getMessage() == null
                            ? "Could not open " + url
                            : exception.getMessage()
            );

            alert.getDialogPane()
                    .getStylesheets()
                    .add(
                            MainWindow.class
                                    .getResource("/styles/brand.css")
                                    .toExternalForm()
                    );

            alert.showAndWait();
        }
    }
}