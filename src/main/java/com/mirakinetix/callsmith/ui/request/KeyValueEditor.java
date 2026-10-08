package com.mirakinetix.callsmith.ui.request;

import com.mirakinetix.callsmith.ui.Icon;
import com.mirakinetix.callsmith.ui.IconView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class KeyValueEditor extends VBox {

    private final VBox rowsContainer =
            new VBox(6);

    private final String keyPrompt;
    private final String valuePrompt;
    private final String itemName;

    public KeyValueEditor(
            String keyPrompt,
            String valuePrompt
    ) {
        this.keyPrompt = keyPrompt;
        this.valuePrompt = valuePrompt;

        this.itemName =
                keyPrompt
                        .toLowerCase()
                        .contains("header")
                        ? "Header"
                        : "Query Parameter";

        setSpacing(8);

        setPadding(
                new Insets(
                        4,
                        0,
                        4,
                        0
                )
        );

        buildLayout();
    }

    private void buildLayout() {

        Label sectionLabel =
                new Label(
                        itemName + "s"
                );

        Button addButton =
                new Button(
                        "Add",
                        new IconView(
                                Icon.ADD,
                                14
                        )
                );

        addButton.setOnAction(
                event ->
                        showEditor(
                                null,
                                null
                        )
        );

        HBox toolbar =
                new HBox(
                        8,
                        sectionLabel,
                        addButton
                );

        toolbar.setAlignment(
                Pos.CENTER_LEFT
        );

        ScrollPane scrollPane =
                new ScrollPane(
                        rowsContainer
                );

        scrollPane.setFitToWidth(
                true
        );

        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        scrollPane.setPrefViewportHeight(
                itemName.equals("Query Parameter")
                        ? 220
                        : 140
        );

        VBox.setVgrow(
                scrollPane,
                Priority.ALWAYS
        );

        getChildren().addAll(
                toolbar,
                scrollPane
        );
    }

    private void addRow(
            String key,
            String value
    ) {

        TextField keyField =
                new TextField(
                        key
                );

        keyField.setPromptText(
                keyPrompt
        );

        keyField.setEditable(
                false
        );

        TextField valueField =
                new TextField(
                        value == null
                                ? ""
                                : value
                );

        valueField.setPromptText(
                valuePrompt
        );

        valueField.setEditable(
                false
        );

        Button editButton =
                new Button(
                        "",
                        new IconView(
                                Icon.EDIT,
                                14
                        )
                );

        editButton.setTooltip(
                new Tooltip(
                        "Edit"
                )
        );

        Button deleteButton =
                new Button(
                        "",
                        new IconView(
                                Icon.DELETE,
                                14
                        )
                );

        deleteButton.setTooltip(
                new Tooltip(
                        "Delete"
                )
        );

        HBox actions =
                new HBox(
                        4,
                        editButton,
                        deleteButton
                );

        actions.setAlignment(
                Pos.CENTER_RIGHT
        );

        HBox row =
                new HBox(
                        8,
                        keyField,
                        valueField,
                        actions
                );

        HBox.setHgrow(
                keyField,
                Priority.ALWAYS
        );

        HBox.setHgrow(
                valueField,
                Priority.ALWAYS
        );

        editButton.setOnAction(
                event ->
                        showEditor(
                                keyField.getText(),
                                valueField.getText()
                        )
        );

        deleteButton.setOnAction(
                event ->
                        confirmDelete(
                                row,
                                keyField.getText()
                        )
        );

        rowsContainer
                .getChildren()
                .add(row);
    }

    private void confirmDelete(
            HBox row,
            String key
    ) {

        Alert confirmation =
                new Alert(
                        Alert.AlertType.CONFIRMATION
                );

        confirmation.setTitle(
                "Delete " + itemName
        );

        confirmation.setHeaderText(
                "Delete \"" + key + "\"?"
        );

        confirmation.setContentText(
                "This "
                        + itemName.toLowerCase()
                        + " will be removed from the request."
        );

        confirmation.showAndWait()
                .filter(
                        result ->
                                result == ButtonType.OK
                )
                .ifPresent(
                        result ->
                                rowsContainer
                                        .getChildren()
                                        .remove(row)
                );
    }

    private void showEditor(
            String existingKey,
            String existingValue
    ) {

        boolean editing =
                existingKey != null;

        Dialog<KeyValue> dialog =
                new Dialog<>();

        dialog.setTitle(
                editing
                        ? "Edit " + itemName
                        : "Add " + itemName
        );

        dialog.setHeaderText(
                editing
                        ? "Edit " + itemName.toLowerCase()
                        : "Add " + itemName.toLowerCase()
        );

        ButtonType saveButtonType =
                new ButtonType(
                        "Save",
                        ButtonData.OK_DONE
                );

        ButtonType cancelButtonType =
                new ButtonType(
                        "Cancel",
                        ButtonData.CANCEL_CLOSE
                );

        dialog.getDialogPane()
                .getButtonTypes()
                .addAll(
                        cancelButtonType,
                        saveButtonType
                );

        TextField keyField =
                new TextField();

        keyField.setPromptText(
                keyPrompt
        );

        if (existingKey != null) {
            keyField.setText(
                    existingKey
            );
        }

        TextField valueField =
                new TextField();

        valueField.setPromptText(
                valuePrompt
        );

        if (existingValue != null) {
            valueField.setText(
                    existingValue
            );
        }

        GridPane form =
                new GridPane();

        form.setHgap(
                10
        );

        form.setVgap(
                10
        );

        form.setPadding(
                new Insets(
                        10
                )
        );

        form.add(
                new Label(
                        keyPrompt
                ),
                0,
                0
        );

        form.add(
                keyField,
                1,
                0
        );

        form.add(
                new Label(
                        valuePrompt
                ),
                0,
                1
        );

        form.add(
                valueField,
                1,
                1
        );

        GridPane.setHgrow(
                keyField,
                Priority.ALWAYS
        );

        GridPane.setHgrow(
                valueField,
                Priority.ALWAYS
        );

        dialog.getDialogPane()
                .setContent(
                        form
                );

        dialog.getDialogPane()
                .setPrefWidth(
                        520
                );

        Button saveButton =
                (Button)
                        dialog.getDialogPane()
                                .lookupButton(
                                        saveButtonType
                                );

        updateSaveButton(
                saveButton,
                keyField.getText(),
                existingKey
        );

        keyField.textProperty()
                .addListener(
                        (
                                observable,
                                oldValue,
                                newValue
                        ) ->
                                updateSaveButton(
                                        saveButton,
                                        newValue,
                                        existingKey
                                )
                );

        dialog.setResultConverter(
                buttonType -> {

                    if (buttonType
                            != saveButtonType) {

                        return null;
                    }

                    String key =
                            keyField
                                    .getText()
                                    .trim();

                    String value =
                            valueField
                                    .getText();

                    if (key.isEmpty()) {
                        return null;
                    }

                    /*
                     * Final defensive check.
                     * The Save button already prevents this,
                     * but never rely only on UI state.
                     */
                    if (hasDuplicateKey(
                            key,
                            existingKey
                    )) {

                        showDuplicateWarning(
                                key
                        );

                        return null;
                    }

                    return new KeyValue(
                            key,
                            value
                    );
                }
        );

        Optional<KeyValue> result =
                dialog.showAndWait();

        if (result.isEmpty()) {
            return;
        }

        KeyValue keyValue =
                result.get();

        if (editing) {

            replaceRow(
                    existingKey,
                    keyValue.key(),
                    keyValue.value()
            );

        } else {

            addRow(
                    keyValue.key(),
                    keyValue.value()
            );
        }
    }

    private void updateSaveButton(
            Button saveButton,
            String key,
            String existingKey
    ) {

        String normalizedKey =
                normalizeKey(key);

        boolean empty =
                normalizedKey.isEmpty();

        boolean duplicate =
                !empty
                        && hasDuplicateKey(
                                key,
                                existingKey
                        );

        saveButton.setDisable(
                empty || duplicate
        );
    }

    private boolean hasDuplicateKey(
            String key,
            String existingKey
    ) {

        String normalizedKey =
                normalizeKey(key);

        if (normalizedKey.isEmpty()) {
            return false;
        }

        String normalizedExistingKey =
                normalizeKey(existingKey);

        /*
         * When editing a row, its original name
         * is allowed to remain unchanged.
         */
        boolean unchangedExistingKey =
                !normalizedExistingKey.isEmpty()
                        && normalizedKey.equals(
                                normalizedExistingKey
                        );

        for (Node node :
                rowsContainer.getChildren()) {

            if (!(node instanceof HBox row)) {
                continue;
            }

            if (row.getChildren().isEmpty()) {
                continue;
            }

            if (!(row.getChildren().get(0)
                    instanceof TextField keyField)) {

                continue;
            }

            String currentKey =
                    normalizeKey(
                            keyField.getText()
                    );

            if (!normalizedKey.equals(
                    currentKey
            )) {
                continue;
            }

            /*
             * If this is the same existing row being
             * edited, it is not a duplicate.
             */
            if (unchangedExistingKey) {

                String originalKey =
                        normalizeKey(
                                existingKey
                        );

                if (originalKey.equals(
                        currentKey
                )) {

                    continue;
                }
            }

            return true;
        }

        return false;
    }

    private void replaceRow(
            String existingKey,
            String newKey,
            String newValue
    ) {

        for (Node node :
                rowsContainer.getChildren()) {

            if (!(node instanceof HBox row)) {
                continue;
            }

            if (row.getChildren().size() < 2) {
                continue;
            }

            if (!(row.getChildren().get(0)
                    instanceof TextField keyField)) {

                continue;
            }

            if (!existingKey.equals(
                    keyField.getText()
            )) {

                continue;
            }

            if (!(row.getChildren().get(1)
                    instanceof TextField valueField)) {

                continue;
            }

            keyField.setText(
                    newKey
            );

            valueField.setText(
                    newValue
            );

            return;
        }
    }

    private String normalizeKey(
            String key
    ) {

        if (key == null) {
            return "";
        }

        return key
                .trim()
                .toLowerCase();
    }

    private void showDuplicateWarning(
            String key
    ) {

        Alert alert =
                new Alert(
                        Alert.AlertType.WARNING
                );

        alert.setTitle(
                "Duplicate " + itemName
        );

        alert.setHeaderText(
                "Duplicate name"
        );

        alert.setContentText(
                "A "
                        + itemName.toLowerCase()
                        + " named \""
                        + key
                        + "\" already exists."
        );

        alert.showAndWait();
    }

    public Map<String, String> getValues() {

        Map<String, String> values =
                new LinkedHashMap<>();

        for (Node node :
                rowsContainer.getChildren()) {

            if (!(node instanceof HBox row)) {
                continue;
            }

            if (row.getChildren().size() < 2) {
                continue;
            }

            if (!(row.getChildren().get(0)
                    instanceof TextField keyField)) {

                continue;
            }

            if (!(row.getChildren().get(1)
                    instanceof TextField valueField)) {

                continue;
            }

            String key =
                    keyField
                            .getText()
                            .trim();

            if (key.isEmpty()) {
                continue;
            }

            /*
             * Defensive protection:
             * even if duplicate rows somehow enter
             * the UI, only the first one is retained.
             */
            if (containsKey(
                    values,
                    key
            )) {
                continue;
            }

            values.put(
                    key,
                    valueField.getText()
            );
        }

        return values;
    }

    private boolean containsKey(
            Map<String, String> values,
            String key
    ) {

        String normalizedKey =
                normalizeKey(key);

        for (String existingKey :
                values.keySet()) {

            if (normalizeKey(
                    existingKey
            ).equals(
                    normalizedKey
            )) {

                return true;
            }
        }

        return false;
    }

    public void setValues(
            Map<String, String> values
    ) {

        rowsContainer
                .getChildren()
                .clear();

        if (values == null
                || values.isEmpty()) {

            return;
        }

        values.forEach(
                this::addRow
        );
    }

    public void clear() {

        rowsContainer
                .getChildren()
                .clear();
    }

    private record KeyValue(
            String key,
            String value
    ) {
    }
}