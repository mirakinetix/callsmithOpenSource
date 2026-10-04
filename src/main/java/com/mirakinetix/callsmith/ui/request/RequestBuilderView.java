package com.mirakinetix.callsmith.ui.request;

import com.mirakinetix.callsmith.application.environment.EnvironmentService;
import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.domain.environment.Environment;
import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.HttpResponse;
import com.mirakinetix.callsmith.ui.Icon;
import com.mirakinetix.callsmith.ui.IconView;
import com.mirakinetix.callsmith.ui.response.ResponseInspector;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

public final class RequestBuilderView extends BorderPane {

    private final HttpRequestService httpRequestService;
    private final EnvironmentService environmentService;
    private final ExecutorService requestExecutor;

    private final ComboBox<String> methodSelector =
            new ComboBox<>();

    private final ComboBox<EnvironmentOption> environmentSelector =
            new ComboBox<>();

    private final TextField urlField =
            new TextField();

    private final Button sendButton =
            new Button(
                    "Send",
                    new IconView(
                            Icon.SEND,
                            16
                    )
            );

    private final KeyValueEditor queryParametersEditor =
            new KeyValueEditor(
                    "Parameter name",
                    "Parameter value"
            );

    private final KeyValueEditor headersEditor =
            new KeyValueEditor(
                    "Header name",
                    "Header value"
            );

    private final TextArea requestBody =
            new TextArea();

    private final ResponseInspector responseInspector =
            new ResponseInspector();

    public RequestBuilderView(
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

        configureControls();
        loadEnvironments();
        buildLayout();
    }

    private void configureControls() {
        methodSelector.getItems().addAll(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "HEAD",
                "OPTIONS"
        );

        methodSelector.getSelectionModel().select("GET");

        environmentSelector.setPromptText(
                "Environment"
        );

        environmentSelector.setPrefWidth(220);

        environmentSelector.setOnAction(
                event -> updateActiveEnvironment()
        );

        urlField.setPromptText(
                "https://example.com/api"
        );

        requestBody.setPromptText(
                "Request body"
        );

        requestBody.setWrapText(true);

        sendButton.setOnAction(
                event -> executeRequest()
        );
    }

    private void loadEnvironments() {
        List<Environment> environments =
                environmentService.findAll();

        environmentSelector.getItems().clear();

        environmentSelector.getItems().add(
                EnvironmentOption.none()
        );

        environments.forEach(
                environment ->
                        environmentSelector.getItems().add(
                                new EnvironmentOption(
                                        environment.id(),
                                        environment.name()
                                )
                        )
        );

        Optional<Environment> activeEnvironment =
                environmentService.findActive();

        if (activeEnvironment.isPresent()) {
            selectEnvironment(
                    activeEnvironment.get().id()
            );

        } else {
            environmentSelector
                    .getSelectionModel()
                    .selectFirst();
        }
    }

    private void selectEnvironment(
            String environmentId
    ) {
        for (EnvironmentOption option :
                environmentSelector.getItems()) {

            if (option.environmentId()
                    .equals(environmentId)) {

                environmentSelector
                        .getSelectionModel()
                        .select(option);

                return;
            }
        }

        environmentSelector
                .getSelectionModel()
                .selectFirst();
    }

    private void updateActiveEnvironment() {
        EnvironmentOption selected =
                environmentSelector
                        .getSelectionModel()
                        .getSelectedItem();

        if (selected == null) {
            return;
        }

        if (selected.environmentId().isBlank()) {
            environmentService.clearActive();
            return;
        }

        environmentService.setActive(
                selected.environmentId()
        );
    }

    private void buildLayout() {
        Label environmentLabel =
                new Label(
                        "Environment",
                        new IconView(
                                Icon.ENVIRONMENT,
                                16
                        )
                );

        HBox environmentBar =
                new HBox(
                        8,
                        environmentLabel,
                        environmentSelector
                );

        HBox requestBar =
                new HBox(
                        8,
                        methodSelector,
                        urlField,
                        sendButton
                );

        HBox.setHgrow(
                urlField,
                Priority.ALWAYS
        );

        VBox requestContent =
                new VBox(
                        8,
                        new Label("Query Parameters"),
                        queryParametersEditor,
                        new Label("Headers"),
                        headersEditor,
                        new Label("Body"),
                        requestBody
                );

        requestContent.setPadding(
                new Insets(12)
        );

        VBox.setVgrow(
                requestBody,
                Priority.ALWAYS
        );

        ScrollPane requestScrollPane =
                new ScrollPane(requestContent);

        requestScrollPane.setFitToWidth(true);
        requestScrollPane.setFitToHeight(true);

        VBox requestSection =
                new VBox(
                        8,
                        environmentBar,
                        new Label("Request"),
                        requestBar,
                        requestScrollPane
                );

        requestSection.setPadding(
                new Insets(12)
        );

        VBox.setVgrow(
                requestScrollPane,
                Priority.ALWAYS
        );

        ScrollPane responseScrollPane =
                new ScrollPane(responseInspector);

        responseScrollPane.setFitToWidth(true);
        responseScrollPane.setFitToHeight(true);

        VBox.setVgrow(
                responseScrollPane,
                Priority.ALWAYS
        );

        SplitPane requestResponsePane =
                new SplitPane(
                        requestSection,
                        responseScrollPane
                );

        requestResponsePane.setOrientation(
                javafx.geometry.Orientation.VERTICAL
        );

        requestResponsePane.setDividerPositions(
                0.5
        );

        setCenter(requestResponsePane);
    }

    private void executeRequest() {
        String method =
                methodSelector.getValue();

        String url =
                urlField.getText().trim();

        String body =
                requestBody.getText();

        if (url.isEmpty()) {
            responseInspector.displayValidationError(
                    "Enter a URL"
            );

            return;
        }

        sendButton.setDisable(true);

        responseInspector.displaySending();

        Map<String, String> queryParameters =
                queryParametersEditor.getValues();

        Map<String, String> headers =
                headersEditor.getValues();

        HttpRequest request =
                new HttpRequest(
                        method,
                        url,
                        headers,
                        queryParameters,
                        body.isEmpty()
                                ? null
                                : body
                );

        String environmentId =
                getSelectedEnvironmentId();

        requestExecutor.submit(() -> {
            try {
                HttpResponse response =
                        httpRequestService.execute(
                                request,
                                environmentId
                        );

                Platform.runLater(
                        () -> {
                            responseInspector
                                    .displayResponse(
                                            response
                                    );

                            sendButton.setDisable(false);
                        }
                );

            } catch (RuntimeException exception) {
                Platform.runLater(
                        () -> {
                            responseInspector
                                    .displayError(
                                            exception
                                    );

                            sendButton.setDisable(false);
                        }
                );
            }
        });
    }

    private String getSelectedEnvironmentId() {
        EnvironmentOption selected =
                environmentSelector
                        .getSelectionModel()
                        .getSelectedItem();

        if (selected == null
                || selected.environmentId().isBlank()) {
            return null;
        }

        return selected.environmentId();
    }

    private record EnvironmentOption(
            String environmentId,
            String name
    ) {

        private static EnvironmentOption none() {
            return new EnvironmentOption(
                    "",
                    "No Environment"
            );
        }

        @Override
        public String toString() {
            return name;
        }
    }
}