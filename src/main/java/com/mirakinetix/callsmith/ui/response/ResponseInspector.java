package com.mirakinetix.callsmith.ui.response;

import com.mirakinetix.callsmith.domain.http.HttpResponse;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Map;

public final class ResponseInspector extends VBox {

    private final Label responseStatus =
            new Label("Status: -");

    private final Label responseTiming =
            new Label("Time: -");

    private final Label responseSize =
            new Label("Size: -");

    private final Label responseUrl =
            new Label("URL: -");

    private final TextArea responseHeaders =
            new TextArea();

    private final TextArea responseBody =
            new TextArea();

    public ResponseInspector() {
        setSpacing(8);
        setPadding(new Insets(12));

        configureControls();
        buildLayout();
    }

    private void configureControls() {
        responseHeaders.setEditable(false);
        responseHeaders.setWrapText(true);
        responseHeaders.setPrefRowCount(5);

        responseBody.setEditable(false);
        responseBody.setWrapText(true);

        responseUrl.setWrapText(true);
    }

    private void buildLayout() {
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

        VBox.setVgrow(
                responseHeaders,
                Priority.ALWAYS
        );

        VBox.setVgrow(
                responseHeaderSection,
                Priority.ALWAYS
        );

        VBox.setVgrow(
                responseBody,
                Priority.ALWAYS
        );

        getChildren().addAll(
                new Label("Response"),
                responseMeta,
                responseUrl,
                responseHeaderSection,
                new Label("Body"),
                responseBody
        );
    }

    public void displaySending() {
        responseStatus.setText(
                "Status: Sending..."
        );

        responseTiming.setText(
                "Time: -"
        );

        responseSize.setText(
                "Size: -"
        );

        responseUrl.setText(
                "URL: -"
        );

        responseHeaders.clear();
        responseBody.clear();
    }

    public void displayValidationError(
            String message
    ) {
        responseStatus.setText(
                "Status: " + message
        );

        responseTiming.setText(
                "Time: -"
        );

        responseSize.setText(
                "Size: -"
        );

        responseUrl.setText(
                "URL: -"
        );

        responseHeaders.clear();
        responseBody.clear();
    }

    public void displayResponse(
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

        responseUrl.setText(
                "URL: "
                        + response.url()
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
    }

    public void displayError(
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

        responseUrl.setText(
                "URL: -"
        );

        responseHeaders.clear();

        String message =
                exception.getMessage();

        responseBody.setText(
                message == null
                        ? exception.toString()
                        : message
        );
    }

    public void clear() {
        responseStatus.setText("Status: -");
        responseTiming.setText("Time: -");
        responseSize.setText("Size: -");
        responseUrl.setText("URL: -");
        responseHeaders.clear();
        responseBody.clear();
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