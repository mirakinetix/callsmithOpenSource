package com.mirakinetix.callsmith.ui.request;

import com.mirakinetix.callsmith.domain.http.RequestOptions;
import com.mirakinetix.callsmith.domain.http.RequestOptions.AuthType;
import com.mirakinetix.callsmith.domain.http.RequestOptions.BodyFormat;
import com.mirakinetix.callsmith.domain.http.SelectedUpload;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class RequestOptionsDialog {
    private RequestOptionsDialog() {}

    static Optional<RequestOptions> show(Window owner, RequestOptions current) {
        var dialog = new Dialog<RequestOptions>();
        dialog.initOwner(owner);
        dialog.setTitle("Send Options");
        dialog.setHeaderText("Authentication, body format and timeout");
        dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());

        var authType = new ComboBox<>(FXCollections.observableArrayList(AuthType.values()));
        authType.setValue(current.authentication().type());
        var authName = new TextField(current.authentication().name());
        authName.setPromptText("Username or API key name");
        var credential = new PasswordField();
        credential.setText(current.authentication().credential());
        credential.setPromptText("Literal value or {{variable}}");
        credential.setAccessibleText("Authentication value");
        var format = new ComboBox<>(FXCollections.observableArrayList(BodyFormat.values()));
        format.setValue(current.format());
        var timeout = new TextField(Long.toString(current.timeout().toSeconds()));
        var fields = new TextArea(String.join("\n", current.fields().stream()
                .map(field -> field.name() + "=" + field.value()).toList()));
        fields.setPromptText("One name=value per line. Repeated names are allowed.");
        fields.setPrefRowCount(3);

        SelectedUpload[] selected = {current.upload()};
        var fileName = new Label(selected[0] == null ? "No file selected" : selected[0].filename());
        var fileField = new TextField(selected[0] == null ? "file" : selected[0].field());
        var choose = new Button("Choose File...");
        var clear = new Button("Clear");
        var fileControls = new HBox(6, choose, clear, fileName);
        var error = new Label();
        error.setWrapText(true);
        choose.setOnAction(event -> {
            var chooser = new FileChooser();
            chooser.setTitle("Choose a multipart upload file");
            var file = chooser.showOpenDialog(owner);
            if (file != null) {
                try {
                    selected[0] = SelectedUpload.select(file.toPath(), fileField.getText());
                    fileName.setText(selected[0].filename());
                    error.setText("");
                } catch (IllegalStateException exception) {
                    error.setText(exception.getMessage());
                }
            }
        });
        clear.setOnAction(event -> {
            selected[0] = null;
            fileName.setText("No file selected");
        });

        Runnable update = () -> {
            AuthType auth = authType.getValue();
            authName.setDisable(auth == AuthType.NONE || auth == AuthType.BEARER);
            credential.setDisable(auth == AuthType.NONE);
            BodyFormat body = format.getValue();
            fields.setDisable(body != BodyFormat.FORM && body != BodyFormat.MULTIPART);
            fileControls.setDisable(body != BodyFormat.MULTIPART);
            fileField.setDisable(body != BodyFormat.MULTIPART);
        };
        authType.valueProperty().addListener((o, old, value) -> update.run());
        format.valueProperty().addListener((o, old, value) -> update.run());
        update.run();

        var grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(8);
        grid.setVgap(8);
        var help = new Label("Options stay in memory for this request. For form bodies, clear the raw Body field. "
                + "Use protected {{variable}} references for credentials you need again.");
        help.setWrapText(true);
        help.setMaxWidth(440);
        grid.add(help, 0, 0, 2, 1);
        grid.addRow(1, new Label("Authentication"), authType);
        grid.addRow(2, new Label("Username / key name"), authName);
        grid.addRow(3, new Label("Credential"), credential);
        grid.addRow(4, new Label("Timeout (seconds)"), timeout);
        grid.addRow(5, new Label("Body format"), format);
        grid.addRow(6, new Label("Form fields"), fields);
        grid.addRow(7, new Label("Upload field name"), fileField);
        grid.addRow(8, new Label("Upload"), fileControls);
        grid.add(error, 0, 9, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL);
        RequestOptions[] result = {null};

        dialog.getDialogPane().lookupButton(ButtonType.APPLY).addEventFilter(ActionEvent.ACTION, event -> {
            try {
                var body = format.getValue();
                boolean isForm = body == BodyFormat.FORM || body == BodyFormat.MULTIPART;
                var auth = authType.getValue();
                var authentication = auth == AuthType.NONE ? RequestOptions.Authentication.NONE
                        : new RequestOptions.Authentication(auth, auth == AuthType.BEARER ? "" : authName.getText(),
                                credential.getText());
                result[0] = new RequestOptions(Duration.ofSeconds(Integer.parseInt(timeout.getText().strip())),
                        authentication, body, isForm ? parseFields(fields.getText()) : List.of(),
                        body == BodyFormat.MULTIPART && selected[0] != null
                                ? selected[0].withField(fileField.getText().strip()) : null);
            } catch (NumberFormatException exception) {
                error.setText("Enter a whole number of seconds from 1 to 300.");
                event.consume();
            } catch (IllegalArgumentException exception) {
                error.setText(exception.getMessage());
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == ButtonType.APPLY ? result[0] : null);
        try {
            return dialog.showAndWait();
        } finally {
            credential.clear();
        }
    }

    private static List<RequestOptions.Field> parseFields(String raw) {
        List<RequestOptions.Field> result = new ArrayList<>();
        for (String line : raw.lines().toList()) {
            if (line.isBlank()) continue;
            int separator = line.indexOf('=');
            if (separator < 1 || line.substring(0, separator).isBlank()) {
                throw new IllegalArgumentException("Enter each form field as name=value.");
            }
            result.add(new RequestOptions.Field(line.substring(0, separator).strip(), line.substring(separator + 1)));
        }
        return result;
    }
}
