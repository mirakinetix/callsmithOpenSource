package com.mirakinetix.callsmith.ui.environment;

import com.mirakinetix.callsmith.application.environment.EnvironmentService;
import com.mirakinetix.callsmith.application.environment.EnvironmentVariableService;
import com.mirakinetix.callsmith.domain.environment.Environment;
import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;
import com.mirakinetix.callsmith.ui.Icon;
import com.mirakinetix.callsmith.ui.IconView;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EnvironmentManager {

    private static final double WIDTH = 900;
    private static final double HEIGHT = 600;

    private final EnvironmentService environmentService;
    private final EnvironmentVariableService variableService;
    private final Runnable onChanged;

    private final ListView<Environment> environmentList =
            new ListView<>();

    private final TableView<EnvironmentVariable> variableTable =
            new TableView<>();

    private Stage stage;

    public EnvironmentManager(
            EnvironmentService environmentService,
            EnvironmentVariableService variableService,
            Runnable onChanged
    ) {
        if (environmentService == null) {
            throw new IllegalArgumentException(
                    "Environment service is required"
            );
        }

        if (variableService == null) {
            throw new IllegalArgumentException(
                    "Environment variable service is required"
            );
        }

        if (onChanged == null) {
            throw new IllegalArgumentException(
                    "Change callback is required"
            );
        }

        this.environmentService = environmentService;
        this.variableService = variableService;
        this.onChanged = onChanged;
    }

    public void show(Window owner) {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }

        stage = new Stage();

        stage.initModality(
                Modality.WINDOW_MODAL
        );

        stage.initOwner(owner);

        stage.setTitle(
                "Callsmith - Environments"
        );

        stage.setResizable(true);

        BorderPane root =
                buildView();

        Scene scene =
                new Scene(
                        root,
                        WIDTH,
                        HEIGHT
                );

        stage.setScene(scene);

        loadEnvironments();

        stage.show();
    }

    private BorderPane buildView() {
        BorderPane root =
                new BorderPane();

        root.setTop(
                buildHeader()
        );

        root.setLeft(
                buildEnvironmentPanel()
        );

        root.setCenter(
                buildVariablePanel()
        );

        BorderPane.setMargin(
                root.getLeft(),
                new Insets(
                        12,
                        0,
                        12,
                        12
                )
        );

        BorderPane.setMargin(
                root.getCenter(),
                new Insets(
                        12
                )
        );

        return root;
    }

    private HBox buildHeader() {
        Label title =
                new Label(
                        "Environments"
                );

        title.setStyle(
                "-fx-font-size: 18px; "
                        + "-fx-font-weight: bold;"
        );

        Label description =
                new Label(
                        "Manage environments and their variables."
                );

        description.setStyle(
                "-fx-text-fill: #666666;"
        );

        VBox text =
                new VBox(
                        3,
                        title,
                        description
                );

        HBox header =
                new HBox(
                        text
                );

        header.setAlignment(
                Pos.CENTER_LEFT
        );

        header.setPadding(
                new Insets(
                        14,
                        16,
                        12,
                        16
                )
        );

        return header;
    }

    private VBox buildEnvironmentPanel() {
        Label title =
                new Label(
                        "Environments"
                );

        title.setStyle(
                "-fx-font-weight: bold;"
        );

        environmentList.setPrefWidth(
                260
        );

        environmentList.setMinWidth(
                240
        );

        environmentList
                .getSelectionModel()
                .setSelectionMode(
                        SelectionMode.SINGLE
                );

        environmentList.setCellFactory(
                ignored ->
                        new EnvironmentListCell()
        );

        environmentList
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable, oldValue, newValue) ->
                                loadVariables(
                                        newValue
                                )
                );

        Button newButton =
                createButton(
                        "New",
                        Icon.ADD
                );

        Button renameButton =
                createButton(
                        "Rename",
                        Icon.EDIT
                );

        Button deleteButton =
                createButton(
                        "Delete",
                        Icon.DELETE
                );

        Button activateButton =
                createButton(
                        "Set Active",
                        Icon.ACTIVATE
                );

        newButton.setOnAction(
                event ->
                        createEnvironment()
        );

        renameButton.setOnAction(
                event ->
                        renameEnvironment()
        );

        deleteButton.setOnAction(
                event ->
                        deleteEnvironment()
        );

        activateButton.setOnAction(
                event ->
                        activateEnvironment()
        );

        HBox actions =
                new HBox(
                        6,
                        newButton,
                        renameButton
                );

        HBox secondaryActions =
                new HBox(
                        6,
                        deleteButton,
                        activateButton
                );

        VBox panel =
                new VBox(
                        8,
                        title,
                        environmentList,
                        actions,
                        secondaryActions
                );

        VBox.setVgrow(
                environmentList,
                Priority.ALWAYS
        );

        return panel;
    }

    private VBox buildVariablePanel() {
        Label title =
                new Label(
                        "Variables"
                );

        title.setStyle(
                "-fx-font-weight: bold;"
        );

        variableTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN
        );

        TableColumn<
                EnvironmentVariable,
                String
                > nameColumn =
                new TableColumn<>(
                        "Name"
                );

        nameColumn.setCellValueFactory(
                cellData ->
                        new ReadOnlyObjectWrapper<>(
                                cellData.getValue().name()
                        )
        );

        TableColumn<
                EnvironmentVariable,
                String
                > valueColumn =
                new TableColumn<>(
                        "Value"
                );

        valueColumn.setCellValueFactory(
                cellData ->
                        new ReadOnlyObjectWrapper<>(
                                cellData.getValue().value()
                        )
        );

        valueColumn.setCellFactory(
                ignored ->
                        new EnvironmentVariableValueCell()
        );

        TableColumn<
                EnvironmentVariable,
                Boolean
                > secretColumn =
                new TableColumn<>(
                        "Secret"
                );

        secretColumn.setCellValueFactory(
                cellData ->
                        new ReadOnlyObjectWrapper<>(
                                cellData.getValue().secret()
                        )
        );

        variableTable.getColumns().setAll(
                nameColumn,
                valueColumn,
                secretColumn
        );

        Button addButton =
                createButton(
                        "Add Variable",
                        Icon.ADD
                );

        Button editButton =
                createButton(
                        "Edit",
                        Icon.EDIT
                );

        Button deleteButton =
                createButton(
                        "Delete",
                        Icon.DELETE
                );

        addButton.setOnAction(
                event ->
                        addVariable()
        );

        editButton.setOnAction(
                event ->
                        editVariable()
        );

        deleteButton.setOnAction(
                event ->
                        deleteVariable()
        );

        HBox actions =
                new HBox(
                        6,
                        addButton,
                        editButton,
                        deleteButton
                );

        VBox panel =
                new VBox(
                        8,
                        title,
                        variableTable,
                        new Separator(),
                        actions
                );

        VBox.setVgrow(
                variableTable,
                Priority.ALWAYS
        );

        return panel;
    }

    private void loadEnvironments() {
        List<Environment> environments =
                environmentService.findAll();

        Environment previouslySelected =
                environmentList
                        .getSelectionModel()
                        .getSelectedItem();

        environmentList.setItems(
                FXCollections.observableArrayList(
                        environments
                )
        );

        if (previouslySelected != null) {
            environments.stream()
                    .filter(
                            environment ->
                                    environment.id()
                                            .equals(
                                                    previouslySelected.id()
                                            )
                    )
                    .findFirst()
                    .ifPresent(
                            environment ->
                                    environmentList
                                            .getSelectionModel()
                                            .select(
                                                    environment
                                            )
                    );
        }

        if (environmentList
                .getSelectionModel()
                .getSelectedItem() == null
                && !environments.isEmpty()) {

            Environment active =
                    environments.stream()
                            .filter(
                                    Environment::active
                            )
                            .findFirst()
                            .orElse(
                                    environments.getFirst()
                            );

            environmentList
                    .getSelectionModel()
                    .select(
                            active
                    );
        }

        if (environments.isEmpty()) {
            variableTable.getItems().clear();
        }
    }

    private void loadVariables(
            Environment environment
    ) {
        if (environment == null) {
            variableTable.getItems().clear();
            return;
        }

        variableTable.setItems(
                FXCollections.observableArrayList(
                        variableService
                                .findByEnvironmentId(
                                        environment.id()
                                )
                )
        );
    }

    private void createEnvironment() {
        TextField nameField =
                new TextField();

        nameField.setPromptText(
                "Environment name"
        );

        Dialog<ButtonType> dialog =
                new Dialog<>();

        dialog.setTitle(
                "Create Environment"
        );

        dialog.setHeaderText(
                "Create a new environment"
        );

        dialog.getDialogPane()
                .getButtonTypes()
                .addAll(
                        ButtonType.OK,
                        ButtonType.CANCEL
                );

        GridPane content =
                createFormGrid();

        content.add(
                new Label("Name:"),
                0,
                0
        );

        content.add(
                nameField,
                1,
                0
        );

        GridPane.setHgrow(
                nameField,
                Priority.ALWAYS
        );

        dialog.getDialogPane()
                .setContent(
                        content
                );

        dialog.showAndWait()
                .filter(
                        result ->
                                result == ButtonType.OK
                )
                .ifPresent(
                        result -> {
                            String name =
                                    nameField
                                            .getText()
                                            .trim();

                            if (name.isBlank()) {
                                showError(
                                        "Environment name cannot be empty."
                                );
                                return;
                            }

                            Instant now =
                                    Instant.now();

                            Environment environment =
                                    new Environment(
                                            UUID.randomUUID()
                                                    .toString(),
                                            name,
                                            false,
                                            now,
                                            now
                                    );

                            try {
                                environmentService.save(
                                        environment
                                );

                                loadEnvironments();

                                selectEnvironment(
                                        environment.id()
                                );

                                onChanged.run();
                            } catch (RuntimeException exception) {
                                showError(
                                        "Unable to create environment:\n"
                                                + exception.getMessage()
                                );
                            }
                        }
                );
    }

    private void renameEnvironment() {
        Environment environment =
                selectedEnvironment();

        if (environment == null) {
            showError(
                    "Select an environment first."
            );
            return;
        }

        TextField nameField =
                new TextField(
                        environment.name()
                );

        Dialog<ButtonType> dialog =
                new Dialog<>();

        dialog.setTitle(
                "Rename Environment"
        );

        dialog.setHeaderText(
                "Rename " + environment.name()
        );

        dialog.getDialogPane()
                .getButtonTypes()
                .addAll(
                        ButtonType.OK,
                        ButtonType.CANCEL
                );

        GridPane content =
                createFormGrid();

        content.add(
                new Label("Name:"),
                0,
                0
        );

        content.add(
                nameField,
                1,
                0
        );

        GridPane.setHgrow(
                nameField,
                Priority.ALWAYS
        );

        dialog.getDialogPane()
                .setContent(
                        content
                );

        dialog.showAndWait()
                .filter(
                        result ->
                                result == ButtonType.OK
                )
                .ifPresent(
                        result -> {
                            String name =
                                    nameField
                                            .getText()
                                            .trim();

                            if (name.isBlank()) {
                                showError(
                                        "Environment name cannot be empty."
                                );
                                return;
                            }

                            Environment updated =
                                    new Environment(
                                            environment.id(),
                                            name,
                                            environment.active(),
                                            environment.createdAt(),
                                            Instant.now()
                                    );

                            try {
                                environmentService.save(
                                        updated
                                );

                                loadEnvironments();

                                selectEnvironment(
                                        updated.id()
                                );

                                onChanged.run();
                            } catch (RuntimeException exception) {
                                showError(
                                        "Unable to rename environment:\n"
                                                + exception.getMessage()
                                );
                            }
                        }
                );
    }

    private void deleteEnvironment() {
        Environment environment =
                selectedEnvironment();

        if (environment == null) {
            showError(
                    "Select an environment first."
            );
            return;
        }

        Alert confirmation =
                new Alert(
                        Alert.AlertType.CONFIRMATION
                );

        confirmation.setTitle(
                "Delete Environment"
        );

        confirmation.setHeaderText(
                "Delete \"" + environment.name() + "\"?"
        );

        confirmation.setContentText(
                "The environment and all of its variables will be deleted."
        );

        confirmation.showAndWait()
                .filter(
                        result ->
                                result == ButtonType.OK
                )
                .ifPresent(
                        result -> {
                            try {
                                environmentService.deleteById(
                                        environment.id()
                                );

                                loadEnvironments();

                                onChanged.run();
                            } catch (RuntimeException exception) {
                                showError(
                                        "Unable to delete environment:\n"
                                                + exception.getMessage()
                                );
                            }
                        }
                );
    }

    private void activateEnvironment() {
        Environment environment =
                selectedEnvironment();

        if (environment == null) {
            showError(
                    "Select an environment first."
            );
            return;
        }

        try {
            environmentService.setActive(
                    environment.id()
            );

            loadEnvironments();

            selectEnvironment(
                    environment.id()
            );

            onChanged.run();
        } catch (RuntimeException exception) {
            showError(
                    "Unable to activate environment:\n"
                            + exception.getMessage()
            );
        }
    }

    private void addVariable() {
        Environment environment =
                selectedEnvironment();

        if (environment == null) {
            showError(
                    "Create or select an environment first."
            );
            return;
        }

        showVariableDialog(
                environment,
                null
        );
    }

    private void editVariable() {
        Environment environment =
                selectedEnvironment();

        EnvironmentVariable variable =
                variableTable
                        .getSelectionModel()
                        .getSelectedItem();

        if (environment == null) {
            showError(
                    "Select an environment first."
            );
            return;
        }

        if (variable == null) {
            showError(
                    "Select a variable first."
            );
            return;
        }

        showVariableDialog(
                environment,
                variable
        );
    }

    private void deleteVariable() {
        EnvironmentVariable variable =
                variableTable
                        .getSelectionModel()
                        .getSelectedItem();

        if (variable == null) {
            showError(
                    "Select a variable first."
            );
            return;
        }

        Alert confirmation =
                new Alert(
                        Alert.AlertType.CONFIRMATION
                );

        confirmation.setTitle(
                "Delete Variable"
        );

        confirmation.setHeaderText(
                "Delete \"" + variable.name() + "\"?"
        );

        confirmation.setContentText(
                "This variable will be permanently deleted."
        );

        confirmation.showAndWait()
                .filter(
                        result ->
                                result == ButtonType.OK
                )
                .ifPresent(
                        result -> {
                            try {
                                variableService.deleteById(
                                        variable.id()
                                );

                                loadVariables(
                                        selectedEnvironment()
                                );

                                onChanged.run();
                            } catch (RuntimeException exception) {
                                showError(
                                        "Unable to delete variable:\n"
                                                + exception.getMessage()
                                );
                            }
                        }
                );
    }

    private void showVariableDialog(
        Environment environment,
        EnvironmentVariable existing
) {
    TextField nameField =
            new TextField(
                    existing == null
                            ? ""
                            : existing.name()
            );

    TextField normalValueField =
            new TextField();

    PasswordField secretValueField =
            new PasswordField();

    String initialValue =
            existing == null
                    ? ""
                    : existing.value();

    normalValueField.setText(
            initialValue
    );

    secretValueField.setText(
            initialValue
    );

    CheckBox secretCheckBox =
            new CheckBox(
                    "Secret"
            );

    secretCheckBox.setSelected(
            existing != null
                    && existing.secret()
    );

    StackPane valueContainer =
            new StackPane(
                    normalValueField,
                    secretValueField
            );

    normalValueField.setVisible(
            !secretCheckBox.isSelected()
    );

    normalValueField.setManaged(
            !secretCheckBox.isSelected()
    );

    secretValueField.setVisible(
            secretCheckBox.isSelected()
    );

    secretValueField.setManaged(
            secretCheckBox.isSelected()
    );

    GridPane content =
            createFormGrid();

    content.add(
            new Label("Name:"),
            0,
            0
    );

    content.add(
            nameField,
            1,
            0
    );

    content.add(
            new Label("Value:"),
            0,
            1
    );

    content.add(
            valueContainer,
            1,
            1
    );

    content.add(
            secretCheckBox,
            1,
            2
    );

    GridPane.setHgrow(
            nameField,
            Priority.ALWAYS
    );

    GridPane.setHgrow(
            valueContainer,
            Priority.ALWAYS
    );

    secretCheckBox.setOnAction(
            event -> {
                boolean secret =
                        secretCheckBox.isSelected();

                String currentValue =
                        secret
                                ? normalValueField
                                        .getText()
                                : secretValueField
                                        .getText();

                normalValueField.setText(
                        currentValue
                );

                secretValueField.setText(
                        currentValue
                );

                normalValueField.setVisible(
                        !secret
                );

                normalValueField.setManaged(
                        !secret
                );

                secretValueField.setVisible(
                        secret
                );

                secretValueField.setManaged(
                        secret
                );
            }
    );

    Dialog<ButtonType> dialog =
            new Dialog<>();

    dialog.setTitle(
            existing == null
                    ? "Add Variable"
                    : "Edit Variable"
    );

    dialog.setHeaderText(
            existing == null
                    ? "Add variable to "
                            + environment.name()
                    : "Edit variable "
                            + existing.name()
    );

    dialog.getDialogPane()
            .getButtonTypes()
            .addAll(
                    ButtonType.OK,
                    ButtonType.CANCEL
            );

    dialog.getDialogPane()
            .setContent(
                    content
            );

    javafx.scene.Node okButton =
            dialog.getDialogPane()
                    .lookupButton(ButtonType.OK);

    okButton.addEventFilter(
            javafx.event.ActionEvent.ACTION,
            event -> {
                String name =
                        nameField
                                .getText()
                                .trim();

                if (isDuplicateVariableName(
                        environment.id(),
                        name,
                        existing
                )) {
                    showError(
                            "A variable with the name \""
                                    + name
                                    + "\" already exists in this environment."
                    );

                    event.consume();
                }
            }
    );

    dialog.showAndWait()
            .filter(
                    result ->
                            result == ButtonType.OK
            )
            .ifPresent(
                    result -> {
                        String name =
                                nameField
                                        .getText()
                                        .trim();

                        String value =
                                secretCheckBox.isSelected()
                                        ? secretValueField
                                                .getText()
                                        : normalValueField
                                                .getText();

                        if (name.isBlank()) {
                            showError(
                                    "Variable name cannot be empty."
                            );
                            return;
                        }

                        Instant now =
                                Instant.now();

                        EnvironmentVariable variable =
                                existing == null
                                        ? new EnvironmentVariable(
                                                UUID.randomUUID()
                                                        .toString(),
                                                environment.id(),
                                                name,
                                                value,
                                                secretCheckBox
                                                        .isSelected(),
                                                now,
                                                now
                                        )
                                        : new EnvironmentVariable(
                                                existing.id(),
                                                environment.id(),
                                                name,
                                                value,
                                                secretCheckBox
                                                        .isSelected(),
                                                existing.createdAt(),
                                                now
                                        );

                        try {
                            variableService.save(
                                    variable
                            );

                            loadVariables(
                                    environment
                            );

                            onChanged.run();
                        } catch (RuntimeException exception) {
                            showError(
                                    "Unable to save variable:\n"
                                            + exception.getMessage()
                            );
                        }
                    }
            );
}

    private boolean isDuplicateVariableName(
            String environmentId,
            String name,
            EnvironmentVariable existing
    ) {
        return variableService
                .findByName(
                        environmentId,
                        name
                )
                .filter(
                        variable ->
                                existing == null
                                        || !variable.id().equals(existing.id())
                )
                .isPresent();
    }

    private GridPane createFormGrid() {
        GridPane grid =
                new GridPane();

        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(
                new Insets(10)
        );

        return grid;
    }

    private Environment selectedEnvironment() {
        return environmentList
                .getSelectionModel()
                .getSelectedItem();
    }

    private void selectEnvironment(
            String environmentId
    ) {
        environmentList
                .getItems()
                .stream()
                .filter(
                        environment ->
                                environment.id()
                                        .equals(
                                                environmentId
                                        )
                )
                .findFirst()
                .ifPresent(
                        environment ->
                                environmentList
                                        .getSelectionModel()
                                        .select(
                                                environment
                                        )
                );
    }

    private Button createButton(
            String text,
            Icon icon
    ) {
        Button button =
                new Button(
                        text
                );

        button.setGraphic(
                new IconView(
                        icon,
                        14
                )
        );

        return button;
    }

    private void showError(
            String message
    ) {
        Alert alert =
                new Alert(
                        Alert.AlertType.ERROR
                );

        alert.setTitle(
                "Callsmith"
        );

        alert.setHeaderText(
                "Operation failed"
        );

        alert.setContentText(
                message
        );

        alert.showAndWait();
    }

    private static final class EnvironmentListCell
            extends ListCell<Environment> {

        @Override
        protected void updateItem(
                Environment environment,
                boolean empty
        ) {
            super.updateItem(
                    environment,
                    empty
            );

            if (empty || environment == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            HBox content =
                    new HBox(
                            6
                    );

            content.setAlignment(
                    Pos.CENTER_LEFT
            );

            Label icon =
                    new Label();

            icon.setGraphic(
                    new IconView(
                            environment.active()
                                    ? Icon.ACTIVATE
                                    : Icon.ENVIRONMENT,
                            14
                    )
            );

            Label name =
                    new Label(
                            environment.name()
                    );

            content.getChildren().addAll(
                    icon,
                    name
            );

            setGraphic(
                    content
            );

            setText(null);
        }
    }

    private static final class EnvironmentVariableValueCell
            extends TableCell<
                    EnvironmentVariable,
                    String> {

        @Override
        protected void updateItem(
                String value,
                boolean empty
        ) {
            super.updateItem(
                    value,
                    empty
            );

            if (empty || value == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            EnvironmentVariable variable =
                    getTableView()
                            .getItems()
                            .get(
                                    getIndex()
                            );

            if (variable.secret()) {
                setText(
                        "********"
                );
            } else {
                setText(
                        value
                );
            }

            setGraphic(null);
        }
    }
}