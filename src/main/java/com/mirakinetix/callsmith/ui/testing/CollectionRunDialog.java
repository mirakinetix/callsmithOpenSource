package com.mirakinetix.callsmith.ui.testing;

import com.mirakinetix.callsmith.application.testing.CollectionRunner;
import com.mirakinetix.callsmith.domain.collection.Collection;
import com.mirakinetix.callsmith.domain.environment.Environment;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CollectionRunDialog {
    private CollectionRunDialog() {}

    public static void show(Window owner, Collection collection, List<Environment> environments,
                            String currentEnvironment, CollectionRunner runner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Run Collection");
        dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        dialog.setHeaderText(collection.name());

        var environment = new ComboBox<Choice>();
        var none = new Choice(null, "No Environment");
        environment.getItems().add(none);
        environment.getItems().addAll(environments.stream().map(e -> new Choice(e.id(), e.name())).toList());
        environment.setValue(environment.getItems().stream()
                .filter(choice -> java.util.Objects.equals(choice.id(), currentEnvironment)).findFirst().orElse(none));
        environment.setAccessibleText("Execution environment");

        var help = new Label("Start sends every saved request in this collection, including requests that change data. "
                + "Review imported requests first. Temporary Send Options are not used.");
        help.setWrapText(true);
        var progress = new ProgressBar(0);
        progress.setMaxWidth(Double.MAX_VALUE);
        var status = new Label("Ready");
        var results = new TextArea();
        results.setEditable(false);
        results.setWrapText(true);
        results.setAccessibleText("Collection execution report");
        var content = new VBox(8, help, new Label("Environment"), environment, progress, status, results);
        content.setPrefWidth(540);
        results.setPrefRowCount(15);
        dialog.getDialogPane().setContent(content);

        var start = new ButtonType("Start Run");
        var cancel = new ButtonType("Cancel Run");
        dialog.getDialogPane().getButtonTypes().setAll(start, cancel, ButtonType.CLOSE);
        var startButton = dialog.getDialogPane().lookupButton(start);
        var cancelButton = dialog.getDialogPane().lookupButton(cancel);
        var closeButton = dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        cancelButton.setDisable(true);
        AtomicBoolean stop = new AtomicBoolean();
        Thread[] worker = {null};
        int[] total = {0};

        cancelButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            stop.set(true);
            cancelButton.setDisable(true);
            status.setText("Cancelling...");
            if (worker[0] != null) worker[0].interrupt();
        });
        dialog.setOnHidden(event -> {
            if (worker[0] != null) {
                stop.set(true);
                worker[0].interrupt();
            }
        });
        dialog.setOnCloseRequest(event -> {
            if (worker[0] != null) event.consume();
        });
        startButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (worker[0] != null) return;
            String selected = environment.getValue().id();
            startButton.setDisable(true);
            cancelButton.setDisable(false);
            closeButton.setDisable(true);
            environment.setDisable(true);
            status.setText("Preparing saved requests...");
            progress.setProgress(-1);
            worker[0] = Thread.startVirtualThread(() -> {
                try {
                    var summary = runner.run(collection, selected, stop::get,
                            count -> Platform.runLater(() -> {
                                total[0] = count;
                                status.setText("0 / " + count + " requests completed");
                                progress.setProgress(count == 0 ? 1 : 0);
                            }),
                            step -> Platform.runLater(() -> {
                                progress.setProgress((double) step.number() / total[0]);
                                status.setText(step.number() + " / " + total[0] + " requests completed");
                                append(results, step);
                            }));
                    Platform.runLater(() -> status.setText(summary.completion() + " — "
                            + summary.completed().size() + " / " + summary.total() + " completed, "
                            + summary.passed() + " passed, " + summary.failed() + " failed"
                            + (summary.total() == 0 ? " (no saved requests)" : "")));
                } catch (IllegalArgumentException exception) {
                    Platform.runLater(() -> status.setText(exception.getMessage()));
                } catch (RuntimeException exception) {
                    Platform.runLater(() -> status.setText("Unable to read or run the selected collection."));
                } finally {
                    Platform.runLater(() -> {
                        worker[0] = null;
                        if (progress.getProgress() < 0) progress.setProgress(0);
                        cancelButton.setDisable(true);
                        closeButton.setDisable(false);
                    });
                }
            });
        });
        dialog.showAndWait();
    }

    private static void append(TextArea report, CollectionRunner.Step step) {
        StringBuilder line = new StringBuilder()
                .append("Request ").append(step.number())
                .append(step.passed() ? " PASS" : " FAIL");
        if (step.status() != null) line.append(" (HTTP ").append(step.status()).append(')');
        line.append(" — ").append(step.detail()).append('\n');
        for (var check : step.assertions()) line.append("  ").append(check.reportLine()).append('\n');
        report.appendText(line.toString());
    }

    private record Choice(String id, String label) {
        @Override public String toString() { return label; }
    }
}
