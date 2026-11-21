package com.mirakinetix.callsmith.ui.response;

import com.mirakinetix.callsmith.domain.http.HttpFailure;
import com.mirakinetix.callsmith.domain.http.HttpResponse;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Files;

public final class ResponseInspector extends VBox {
    private final Label status = new Label();
    private final Label timing = new Label();
    private final Label size = new Label();
    private final Label url = new Label();
    private final TextArea headers = new TextArea();
    private final TextArea body = new TextArea();
    private final CheckBox pretty = new CheckBox("Format JSON");
    private final Button save = new Button("Save Response Bytes...");
    private HttpResponse current;
    private ResponsePreview preview;

    public ResponseInspector() {
        setSpacing(8);
        setPadding(new Insets(12));
        status.setId("response-status");
        body.setId("response-body");
        headers.setEditable(false);
        headers.setWrapText(true);
        headers.setPrefRowCount(5);
        body.setEditable(false);
        // Avoid reflowing very long lines in the response area.
        body.setWrapText(false);
        url.setWrapText(true);
        pretty.setSelected(true);
        pretty.setOnAction(event -> displayBody());
        save.setOnAction(event -> saveResponse());
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().addAll(new Label("Response"), new HBox(16, status, timing, size), url,
                new Label("Headers"), headers, new HBox(10, new Label("Body"), pretty, save), body);
        clear();
    }

    public void displaySending() {
        clear();
        status.setText("Status: Sending...");
    }

    public void displayValidationError(String message) {
        clear();
        status.setText("Status: " + message);
    }

    public void displayResponse(HttpResponse response, ResponsePreview prepared) {
        current = response;
        preview = prepared;
        status.setText("Status: " + response.statusCode());
        timing.setText("Time: " + response.elapsedMilliseconds() + " ms");
        size.setText("Size: " + response.sizeBytes() + " bytes");
        url.setText("URL: " + (response.url() == null ? "[hidden]" : response.url()));
        var formatted = new StringBuilder();
        response.headers().forEach((name, value) -> formatted.append(name).append(": ").append(value).append('\n'));
        headers.setText(formatted.toString());
        save.setDisable(!response.hasBytes());
        pretty.setDisable(prepared.formatted() == null);
        displayBody();
    }

    public void displayError(RuntimeException exception) {
        clear();
        status.setText("Status: Request failed");
        if (exception instanceof HttpFailure failure) {
            body.setText(failure.getMessage());
        } else if (exception instanceof IllegalStateException && exception.getMessage() != null) {
            body.setText(exception.getMessage());
        } else {
            body.setText("An unexpected error interrupted the request.");
        }
    }

    public void clear() {
        current = null;
        preview = null;
        status.setText("Status: -");
        timing.setText("Time: -");
        size.setText("Size: -");
        url.setText("URL: -");
        headers.clear();
        body.clear();
        pretty.setDisable(true);
        save.setDisable(true);
    }

    private void displayBody() {
        if (preview != null) {
            body.setText(pretty.isSelected() && preview.formatted() != null ? preview.formatted() : preview.raw());
        }
    }

    private void saveResponse() {
        if (current == null || !current.hasBytes()) return;
        var chooser = new FileChooser();
        chooser.setTitle("Save Original Response Bytes");
        chooser.setInitialFileName("response.bin");
        var file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        var destination = file.toPath().toAbsolutePath();
        HttpResponse response = current;
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                if (Files.exists(destination)) throw new java.nio.file.FileAlreadyExistsException("");
                var temporary = Files.createTempFile(destination.getParent(), ".callsmith-response-", ".tmp");
                try {
                    Files.write(temporary, response.bytes());
                    Files.move(temporary, destination);
                } finally {
                    Files.deleteIfExists(temporary);
                }
                return null;
            }
        };
        task.setOnFailed(event -> {
            var alert = new Alert(Alert.AlertType.ERROR,
                    "Could not save the response. Choose a new filename in a writable folder.");
            if (getScene() != null) alert.initOwner(getScene().getWindow());
            alert.show();
        });
        Thread.startVirtualThread(task);
    }
}
