package com.mirakinetix.callsmith.ui.request;

import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.HttpResponse;
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

import java.util.Map;
import java.util.concurrent.ExecutorService;

public final class RequestBuilderView extends BorderPane {

    private final HttpRequestService httpRequestService;
    private final ExecutorService requestExecutor;

    private final ComboBox<String> methodSelector =
            new ComboBox<>();

    private final TextField urlField =
            new TextField();

    private final Button sendButton =
            new Button("Send");

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

    private final TextArea responseBody =
            new TextArea();

    private final TextArea responseHeaders =
            new TextArea();

    private final Label responseStatus =
            new Label("Status: -");

    private final Label responseTiming =
            new Label("Time: -");

    private final Label responseSize =
            new Label("Size: -");

    public RequestBuilderView(
            HttpRequestService httpRequestService,
            ExecutorService requestExecutor
    ) {
        this.httpRequestService = httpRequestService;
        this.requestExecutor = requestExecutor;

        configureControls();
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

        urlField.setPromptText("https://example.com/api");

        requestBody.setPromptText("Request body");
        requestBody.setWrapText(true);

        responseHeaders.setEditable(false);
        responseHeaders.setWrapText(true);

        responseBody.setEditable(false);
        responseBody.setWrapText(true);

        sendButton.setOnAction(
                event -> executeRequest()
        );
    }

    private void buildLayout() {
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

        HBox responseMeta =
                new HBox(
                        16,
                        responseStatus,
                        responseTiming,
                        responseSize
                );

        VBox responseHeaderSection =
                new VBox(
                        6,
                        new Label("Headers"),
                        responseHeaders
                );

        responseHeaders.setPrefRowCount(5);

        VBox.setVgrow(
                responseHeaders,
                Priority.ALWAYS
        );

        VBox responseContent =
                new VBox(
                        8,
                        new Label("Response"),
                        responseMeta,
                        responseHeaderSection,
                        new Label("Body"),
                        responseBody
                );

        responseContent.setPadding(
                new Insets(12)
        );

        VBox.setVgrow(
                responseHeaderSection,
                Priority.ALWAYS
        );

        VBox.setVgrow(
                responseBody,
                Priority.ALWAYS
        );

        ScrollPane responseScrollPane =
                new ScrollPane(responseContent);

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
            responseStatus.setText(
                    "Status: Enter a URL"
            );

            responseTiming.setText(
                    "Time: -"
            );

            responseSize.setText(
                    "Size: -"
            );

            responseHeaders.clear();
            responseBody.clear();

            return;
        }

        sendButton.setDisable(true);

        responseStatus.setText(
                "Status: Sending..."
        );

        responseTiming.setText(
                "Time: -"
        );

        responseSize.setText(
                "Size: -"
        );

        responseHeaders.clear();
        responseBody.clear();

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

        requestExecutor.submit(() -> {
            try {
                HttpResponse response =
                        httpRequestService.execute(
                                request
                        );

                Platform.runLater(
                        () -> displayResponse(response)
                );

            } catch (RuntimeException exception) {
                Platform.runLater(
                        () -> displayError(exception)
                );
            }
        });
    }

    private void displayResponse(
            HttpResponse response
    ) {
        responseStatus.setText(
                "Status: "
                        + response.statusCode()
        );

        responseTiming.setText(
                "Time: "
                        + response.elapsedMilliseconds()
                        + " ms"
        );

        responseSize.setText(
                "Size: "
                        + response.sizeBytes()
                        + " bytes"
        );

        responseHeaders.setText(
                formatHeaders(
                        response.headers()
                )
        );

        responseBody.setText(
                response.body() == null
                        ? ""
                        : response.body()
        );

        sendButton.setDisable(false);
    }

    private void displayError(
            RuntimeException exception
    ) {
        responseStatus.setText(
                "Status: Request failed"
        );

        responseTiming.setText(
                "Time: -"
        );

        responseSize.setText(
                "Size: -"
        );

        responseHeaders.clear();

        String message =
                exception.getMessage();

        responseBody.setText(
                message == null
                        ? exception.toString()
                        : message
        );

        sendButton.setDisable(false);
    }

    private String formatHeaders(
            Map<String, String> headers
    ) {
        if (headers.isEmpty()) {
            return "";
        }

        StringBuilder formattedHeaders =
                new StringBuilder();

        headers.forEach(
                (name, value) ->
                        formattedHeaders
                                .append(name)
                                .append(": ")
                                .append(value)
                                .append(
                                        System.lineSeparator()
                                )
        );

        return formattedHeaders.toString();
    }
}