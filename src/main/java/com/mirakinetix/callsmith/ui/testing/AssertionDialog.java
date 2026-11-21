package com.mirakinetix.callsmith.ui.testing;

import com.mirakinetix.callsmith.application.testing.AssertionRepository;
import com.mirakinetix.callsmith.application.testing.ResponseAssertion;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;

public final class AssertionDialog {
    private AssertionDialog() {}

    public static void show(Window owner, String requestId, AssertionRepository repository) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Response Assertions");
        dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        var types = new ComboBox<ResponseAssertion.Type>();
        types.getItems().setAll(ResponseAssertion.Type.values());
        types.setValue(ResponseAssertion.Type.STATUS_EQUALS);
        var target = new TextField();
        target.setAccessibleText("Assertion selector or minimum status");
        var expected = new TextField("200");
        expected.setAccessibleText("Expected value or maximum");
        var detail = new Label();
        detail.setWrapText(true);
        var list = new ListView<ResponseAssertion>();
        list.setPrefHeight(155);
        list.setAccessibleText("Saved assertion rules");
        try {
            list.getItems().setAll(repository.find(requestId));
        } catch (RuntimeException exception) {
            var alert = new Alert(Alert.AlertType.ERROR, "Callsmith could not read the saved assertions.");
            alert.initOwner(owner);
            alert.showAndWait();
            return;
        }
        list.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(ResponseAssertion item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : (getIndex() + 1) + ". " + item.type());
            }
        });

        Label targetLabel = new Label();
        targetLabel.setLabelFor(target);
        Label expectedLabel = new Label();
        expectedLabel.setLabelFor(expected);
        Runnable updateFields = () -> {
            TargetFields fields = labels(types.getValue());
            targetLabel.setText(fields.target() == null ? "Selector (unused)" : fields.target());
            expectedLabel.setText(fields.expected() == null ? "Expected value (unused)" : fields.expected());
            target.setDisable(fields.target() == null);
            expected.setDisable(fields.expected() == null);
        };
        types.valueProperty().addListener((o, before, after) -> updateFields.run());
        updateFields.run();

        var add = new Button("Add Rule");
        var replace = new Button("Update Rule");
        var remove = new Button("Remove Rule");
        replace.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        remove.disableProperty().bind(replace.disableProperty());
        list.getSelectionModel().selectedItemProperty().addListener((o, previous, selected) -> {
            if (selected == null) return;
            types.setValue(selected.type());
            target.setText(selected.target());
            expected.setText(selected.expected());
        });
        add.setOnAction(event -> applyRule(list, types, target, expected, detail, false));
        replace.setOnAction(event -> applyRule(list, types, target, expected, detail, true));
        remove.setOnAction(event -> {
            int index = list.getSelectionModel().getSelectedIndex();
            if (index >= 0) list.getItems().remove(index);
        });

        var help = new Label("Rules attach to this saved request. Add or update a rule, then save. "
                + "Use non-secret literals or {{protectedVariable}} references for sensitive comparisons.");
        help.setWrapText(true);
        var content = new VBox(7, help, list, new Label("Assertion type"), types, targetLabel, target,
                expectedLabel, expected, new HBox(6, add, replace, remove), detail);
        content.setPrefWidth(440);
        var save = new ButtonType("Save Assertions", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().setAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, event -> {
            try {
                repository.replace(requestId, List.copyOf(list.getItems()));
            } catch (IllegalArgumentException exception) {
                detail.setText(exception.getMessage());
                event.consume();
            } catch (RuntimeException exception) {
                detail.setText("Callsmith could not save the assertions.");
                event.consume();
            }
        });
        dialog.showAndWait();
    }

    private static void applyRule(ListView<ResponseAssertion> list, ComboBox<ResponseAssertion.Type> types,
                                  TextField target, TextField expected, Label detail, boolean update) {
        try {
            int selected = list.getSelectionModel().getSelectedIndex();
            if (!update && list.getItems().size() >= ResponseAssertion.MAX_PER_REQUEST) {
                throw new IllegalArgumentException("A request can have at most 32 assertions.");
            }
            var spec = new ResponseAssertion(types.getValue(), target.isDisabled() ? "" : target.getText(),
                    expected.isDisabled() ? "" : expected.getText());
            spec.validate();
            if (update && selected >= 0) list.getItems().set(selected, spec);
            else list.getItems().add(spec);
            detail.setText("");
        } catch (IllegalArgumentException exception) {
            detail.setText(exception.getMessage());
        }
    }

    private static TargetFields labels(ResponseAssertion.Type type) {
        return switch (type) {
            case STATUS_EQUALS -> new TargetFields(null, "Expected status code");
            case STATUS_RANGE -> new TargetFields("Lowest status code", "Highest status code");
            case HEADER_EXISTS -> new TargetFields("Header name", null);
            case HEADER_EQUALS -> new TargetFields("Header name", "Expected header value");
            case BODY_CONTAINS, BODY_EXCLUDES -> new TargetFields(null, "Comparison text");
            case JSON_EXISTS -> new TargetFields("JSON Pointer such as /user/id", null);
            case JSON_EQUALS -> new TargetFields("JSON Pointer such as /user/id", "Expected JSON value or protected reference");
            case DURATION_BELOW -> new TargetFields(null, "Milliseconds");
        };
    }

    private record TargetFields(String target, String expected) {}
}
