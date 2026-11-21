package com.mirakinetix.callsmith.ui.environment;

import com.mirakinetix.callsmith.application.environment.SecretStorage;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Arrays;

final class SecretStorageDialog {
    private final SecretStorage storage;

    SecretStorageDialog(SecretStorage storage) {
        this.storage = storage;
    }

    void show(Window owner) {
        if (storage.isUnlocked()) {
            Alert lock = new Alert(Alert.AlertType.CONFIRMATION,
                    "Locking prevents further use of saved secrets in this session until you unlock them.",
                    ButtonType.OK, ButtonType.CANCEL);
            style(lock, owner);
            lock.setHeaderText("Lock Secret Storage?");
            if (lock.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                storage.lock();
            }
            return;
        }

        boolean configured;
        try {
            configured = storage.isConfigured();
        } catch (IllegalStateException exception) {
            Alert error = new Alert(Alert.AlertType.ERROR, exception.getMessage());
            style(error, owner);
            error.showAndWait();
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        style(dialog, owner);
        dialog.setHeaderText(configured ? "Unlock this workspace" : "Set up Secret Storage");

        PasswordField passphrase = new PasswordField();
        passphrase.setPromptText("Workspace passphrase");
        passphrase.setAccessibleText("Workspace passphrase");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm passphrase");
        confirm.setAccessibleText("Confirm passphrase");
        Label status = new Label();
        status.setWrapText(true);
        CheckBox backup = new CheckBox("I have a backup or this workspace has no existing secret values.");
        backup.setWrapText(true);

        VBox content = new VBox(8, passphrase);
        content.setPrefWidth(425);
        if (!configured) {
            content.getChildren().addAll(wrappingLabel(
                    "Choose a strong, unique passphrase of at least 16 characters. Store it separately "
                    + "from the database. Callsmith cannot recover a lost passphrase."),
                    confirm, wrappingLabel(
                    "Setup encrypts existing secrets in this workspace. Back up the database first "
                    + "if it already contains secrets."), backup);
        }
        content.getChildren().add(status);
        dialog.getDialogPane().setContent(content);
        ButtonType submit = new ButtonType(configured ? "Unlock" : "Set Up and Protect");
        dialog.getDialogPane().getButtonTypes().setAll(submit, ButtonType.CANCEL);
        var action = dialog.getDialogPane().lookupButton(submit);
        var cancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        dialog.setOnCloseRequest(event -> {
            if (action.isDisabled()) {
                event.consume();
            }
        });
        action.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (!configured && (!backup.isSelected() || !passphrase.getText().equals(confirm.getText()))) {
                status.setText("Confirm the passphrase and acknowledge the backup before continuing.");
                return;
            }
            char[] password = passphrase.getText().toCharArray();
            passphrase.clear();
            confirm.clear();
            action.setDisable(true);
            cancel.setDisable(true);
            content.setDisable(true);
            status.setText(configured ? "Unlocking..." : "Protecting workspace secrets...");
            Task<Void> operation = new Task<>() {
                @Override
                protected Void call() {
                    try {
                        if (configured) {
                            storage.unlock(password);
                        } else {
                            storage.initialize(password);
                        }
                        return null;
                    } finally {
                        Arrays.fill(password, '\0');
                    }
                }
            };
            operation.setOnSucceeded(ignored -> {
                action.setDisable(false);
                dialog.close();
            });
            operation.setOnFailed(ignored -> {
                action.setDisable(false);
                cancel.setDisable(false);
                content.setDisable(false);
                Throwable error = operation.getException();
                status.setText(error instanceof IllegalStateException || error instanceof IllegalArgumentException
                        ? error.getMessage() : "Callsmith could not complete the Secret Storage operation.");
            });
            Thread.startVirtualThread(operation);
        });
        try {
            dialog.showAndWait();
        } finally {
            passphrase.clear();
            confirm.clear();
        }
    }

    private static Label wrappingLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private static void style(Dialog<?> dialog, Window owner) {
        dialog.initOwner(owner);
        dialog.setTitle("Secret Storage");
        dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
    }
}
