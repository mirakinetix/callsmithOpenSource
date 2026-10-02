package com.mirakinetix.callsmith.ui.request;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

public final class KeyValueEditor extends VBox {

    private final VBox rowsContainer =
            new VBox(6);

    private final String keyPrompt;
    private final String valuePrompt;

    public KeyValueEditor(
            String keyPrompt,
            String valuePrompt
    ) {
        this.keyPrompt = keyPrompt;
        this.valuePrompt = valuePrompt;

        setSpacing(8);
        setPadding(new Insets(4, 0, 4, 0));

        buildLayout();
        addRow();
    }

    private void buildLayout() {
        Button addButton =
                new Button("Add");

        addButton.setOnAction(
                event -> addRow()
        );

        HBox toolbar =
                new HBox(
                        8,
                        addButton
                );

        ScrollPane scrollPane =
                new ScrollPane(rowsContainer);

        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(130);

        VBox.setVgrow(
                scrollPane,
                Priority.ALWAYS
        );

        getChildren().addAll(
                toolbar,
                scrollPane
        );
    }

    private void addRow() {
        TextField keyField =
                new TextField();

        keyField.setPromptText(keyPrompt);

        TextField valueField =
                new TextField();

        valueField.setPromptText(valuePrompt);

        Button removeButton =
                new Button("Remove");

        HBox row =
                new HBox(
                        8,
                        keyField,
                        valueField,
                        removeButton
                );

        HBox.setHgrow(
                keyField,
                Priority.ALWAYS
        );

        HBox.setHgrow(
                valueField,
                Priority.ALWAYS
        );

        removeButton.setOnAction(
                event -> rowsContainer
                        .getChildren()
                        .remove(row)
        );

        rowsContainer.getChildren().add(row);
    }

    public Map<String, String> getValues() {
        Map<String, String> values =
                new LinkedHashMap<>();

        rowsContainer.getChildren()
                .forEach(node -> {
                    if (!(node instanceof HBox row)) {
                        return;
                    }

                    if (row.getChildren().size() < 2) {
                        return;
                    }

                    if (!(row.getChildren().get(0)
                            instanceof TextField keyField)) {
                        return;
                    }

                    if (!(row.getChildren().get(1)
                            instanceof TextField valueField)) {
                        return;
                    }

                    String key =
                            keyField.getText().trim();

                    if (key.isEmpty()) {
                        return;
                    }

                    values.put(
                            key,
                            valueField.getText()
                    );
                });

        return values;
    }

    public void clear() {
        rowsContainer.getChildren().clear();
    }
}