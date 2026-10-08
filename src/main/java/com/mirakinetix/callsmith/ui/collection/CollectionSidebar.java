package com.mirakinetix.callsmith.ui.collection;

import com.mirakinetix.callsmith.application.collection.CollectionWorkspaceService;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImportPersistenceService;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImportService;
import com.mirakinetix.callsmith.domain.collection.Collection;
import com.mirakinetix.callsmith.domain.collection.CollectionFolder;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import com.mirakinetix.callsmith.domain.request.SavedRequest;
import com.mirakinetix.callsmith.ui.Icon;
import com.mirakinetix.callsmith.ui.IconView;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class CollectionSidebar extends BorderPane {

    private static final double WIDTH = 280;

    private final CollectionWorkspaceService workspaceService;
    private final CollectionImportService importService;
    private final CollectionImportPersistenceService
            importPersistenceService;
    private final Consumer<SavedRequest> requestSelectionHandler;

    private final TreeView<WorkspaceItem> treeView =
            new TreeView<>();

    public CollectionSidebar(
            CollectionWorkspaceService workspaceService,
            CollectionImportService importService,
            CollectionImportPersistenceService
                    importPersistenceService,
            Consumer<SavedRequest> requestSelectionHandler
    ) {
        if (workspaceService == null) {
            throw new IllegalArgumentException(
                    "Collection workspace service is required"
            );
        }

        if (importService == null) {
            throw new IllegalArgumentException(
                    "Collection import service is required"
            );
        }

        if (importPersistenceService == null) {
            throw new IllegalArgumentException(
                    "Collection import persistence service is required"
            );
        }

        if (requestSelectionHandler == null) {
            throw new IllegalArgumentException(
                    "Request selection handler is required"
            );
        }

        this.workspaceService =
                workspaceService;

        this.importService =
                importService;

        this.importPersistenceService =
                importPersistenceService;

        this.requestSelectionHandler =
                requestSelectionHandler;

        setPrefWidth(WIDTH);
        setMinWidth(WIDTH);
        setMaxWidth(WIDTH);

        buildLayout();
        loadCollections();
    }

    private void buildLayout() {
        Label title =
                new Label(
                        "Collections",
                        new IconView(
                                Icon.COLLECTION,
                                16
                        )
                );

        title.setPadding(
                new Insets(
                        10,
                        10,
                        8,
                        10
                )
        );

        Button createButton =
                new Button(
                        "New",
                        new IconView(
                                Icon.ADD,
                                16
                        )
                );

        createButton.setOnAction(
                event -> createCollection()
        );

        Button importButton =
                new Button(
                        "Import",
                        new IconView(
                                Icon.UPLOAD,
                                16
                        )
                );

        importButton.setOnAction(
                event -> importCollection()
        );

        Button renameButton =
                new Button(
                        "Rename",
                        new IconView(
                                Icon.EDIT,
                                16
                        )
                );

        renameButton.setOnAction(
                event -> renameSelectedCollection()
        );

        Button deleteButton =
                new Button(
                        "Delete",
                        new IconView(
                                Icon.DELETE,
                                16
                        )
                );

        deleteButton.setOnAction(
                event -> deleteSelectedCollection()
        );

        HBox toolbar =
                new HBox(
                        6,
                        title,
                        createButton,
                        importButton,
                        renameButton,
                        deleteButton
                );

        toolbar.setPadding(
                new Insets(
                        8,
                        10,
                        8,
                        10
                )
        );

        HBox.setHgrow(
                title,
                Priority.ALWAYS
        );

        treeView.setShowRoot(false);

        treeView.setCellFactory(
                ignored -> new WorkspaceTreeCell()
        );

        treeView.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable, oldItem, newItem) ->
                                handleSelection(newItem)
                );

        setTop(toolbar);
        setCenter(treeView);
    }

    private void loadCollections() {
        TreeItem<WorkspaceItem> root =
                new TreeItem<>();

        for (Collection collection :
                workspaceService.findCollections()) {

            TreeItem<WorkspaceItem> collectionItem =
                    new TreeItem<>(
                            WorkspaceItem.collection(
                                    collection
                            )
                    );

            CollectionWorkspaceService.CollectionWorkspace workspace =
                    workspaceService.loadCollection(
                            collection
                    );

            addRootRequests(
                    collectionItem,
                    workspace.requestsByFolder()
            );

            addFolders(
                    collectionItem,
                    null,
                    workspace.foldersByParent(),
                    workspace.requestsByFolder()
            );

            collectionItem.setExpanded(true);

            root.getChildren().add(
                    collectionItem
            );
        }

        treeView.setRoot(root);
    }

    private void createCollection() {
        TextField nameField =
                new TextField();

        nameField.setPromptText(
                "Collection name"
        );

        Dialog<ButtonType> dialog =
                new Dialog<>();

        dialog.setTitle(
                "Create Collection"
        );

        dialog.setHeaderText(
                "Create a new collection"
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

        dialog.getDialogPane()
                .setContent(content);

        Button okButton =
                (Button) dialog.getDialogPane()
                        .lookupButton(
                                ButtonType.OK
                        );

        okButton.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {
                    String name =
                            nameField
                                    .getText()
                                    .trim();

                    if (name.isBlank()) {
                        showError(
                                "Create Collection",
                                "Collection name is required."
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
                        ignored -> {
                            String name =
                                    nameField
                                            .getText()
                                            .trim();

                            Instant now =
                                    Instant.now();

                            Collection collection =
                                    new Collection(
                                            UUID.randomUUID()
                                                    .toString(),
                                            name,
                                            now,
                                            now
                                    );

                            workspaceService.saveCollection(
                                    collection
                            );

                            loadCollections();
                        }
                );
    }

    private void renameSelectedCollection() {
        Collection collection =
                getSelectedCollection();

        if (collection == null) {
            showError(
                    "Rename Collection",
                    "Select a collection first."
            );

            return;
        }

        TextField nameField =
                new TextField(
                        collection.name()
                );

        nameField.selectAll();

        Dialog<ButtonType> dialog =
                new Dialog<>();

        dialog.setTitle(
                "Rename Collection"
        );

        dialog.setHeaderText(
                "Rename collection"
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

        dialog.getDialogPane()
                .setContent(content);

        Button okButton =
                (Button) dialog.getDialogPane()
                        .lookupButton(
                                ButtonType.OK
                        );

        okButton.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {
                    String name =
                            nameField
                                    .getText()
                                    .trim();

                    if (name.isBlank()) {
                        showError(
                                "Rename Collection",
                                "Collection name is required."
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
                        ignored -> {
                            String name =
                                    nameField
                                            .getText()
                                            .trim();

                            Collection renamed =
                                    new Collection(
                                            collection.id(),
                                            name,
                                            collection.createdAt(),
                                            Instant.now()
                                    );

                            workspaceService.saveCollection(
                                    renamed
                            );

                            loadCollections();
                        }
                );
    }

    private void deleteSelectedCollection() {
        Collection collection =
                getSelectedCollection();

        if (collection == null) {
            showError(
                    "Delete Collection",
                    "Select a collection first."
            );

            return;
        }

        Alert confirmation =
                new Alert(
                        Alert.AlertType.CONFIRMATION
                );

        confirmation.setTitle(
                "Delete Collection"
        );

        confirmation.setHeaderText(
                "Delete collection?"
        );

        confirmation.setContentText(
                "Are you sure you want to delete \""
                        + collection.name()
                        + "\"?"
                        + System.lineSeparator()
                        + System.lineSeparator()
                        + "This action cannot be undone."
        );

        confirmation.getButtonTypes().setAll(
                ButtonType.OK,
                ButtonType.CANCEL
        );

        confirmation.showAndWait()
                .filter(
                        result ->
                                result == ButtonType.OK
                )
                .ifPresent(
                        ignored -> {
                            workspaceService.deleteCollection(
                                    collection.id()
                            );

                            loadCollections();
                        }
                );
    }

    private Collection getSelectedCollection() {
        TreeItem<WorkspaceItem> selectedItem =
                treeView.getSelectionModel()
                        .getSelectedItem();

        if (selectedItem == null
                || selectedItem.getValue() == null) {
            return null;
        }

        WorkspaceItem item =
                selectedItem.getValue();

        if (item.type()
                != WorkspaceItemType.COLLECTION) {
            return null;
        }

        return item.collection();
    }

    private GridPane createFormGrid() {
        GridPane grid =
                new GridPane();

        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(
                new Insets(12)
        );

        return grid;
    }

    private void importCollection() {
        FileChooser fileChooser =
                new FileChooser();

        fileChooser.setTitle(
                "Import API Collection"
        );

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                        "API Collections",
                        "*.json",
                        "*.yaml",
                        "*.yml"
                ),
                new FileChooser.ExtensionFilter(
                        "JSON",
                        "*.json"
                ),
                new FileChooser.ExtensionFilter(
                        "YAML",
                        "*.yaml",
                        "*.yml"
                ),
                new FileChooser.ExtensionFilter(
                        "All Files",
                        "*.*"
                )
        );

        Window window =
                getScene() == null
                        ? null
                        : getScene().getWindow();

        File selectedFile =
                fileChooser.showOpenDialog(window);

        if (selectedFile == null) {
            return;
        }

        try {
            Path file =
                    selectedFile.toPath();

            ImportedCollection importedCollection =
                    importService.importCollection(
                            file
                    );

            importPersistenceService.persist(
                    importedCollection
            );

            loadCollections();

            showInformation(
                    "Import successful",
                    "Imported collection: "
                            + importedCollection.name()
            );

        } catch (RuntimeException exception) {
            showError(
                    "Import failed",
                    exception.getMessage() == null
                            ? exception.toString()
                            : exception.getMessage()
            );
        }
    }

    private void addRootRequests(
            TreeItem<WorkspaceItem> collectionItem,
            Map<String, List<SavedRequest>> requestsByFolder
    ) {
        List<SavedRequest> requests =
                requestsByFolder.get("");

        if (requests == null) {
            return;
        }

        for (SavedRequest request : requests) {
            collectionItem.getChildren().add(
                    new TreeItem<>(
                            WorkspaceItem.request(request)
                    )
            );
        }
    }

    private void addFolders(
            TreeItem<WorkspaceItem> parentItem,
            String parentFolderId,
            Map<String, List<CollectionFolder>> foldersByParent,
            Map<String, List<SavedRequest>> requestsByFolder
    ) {
        String parentKey =
                parentFolderId == null
                        ? ""
                        : parentFolderId;

        List<CollectionFolder> folders =
                foldersByParent.get(parentKey);

        if (folders == null) {
            return;
        }

        for (CollectionFolder folder : folders) {
            TreeItem<WorkspaceItem> folderItem =
                    new TreeItem<>(
                            WorkspaceItem.folder(folder)
                    );

            addFolderRequests(
                    folderItem,
                    folder.id(),
                    requestsByFolder
            );

            addFolders(
                    folderItem,
                    folder.id(),
                    foldersByParent,
                    requestsByFolder
            );

            parentItem.getChildren().add(
                    folderItem
            );
        }
    }

    private void addFolderRequests(
            TreeItem<WorkspaceItem> folderItem,
            String folderId,
            Map<String, List<SavedRequest>> requestsByFolder
    ) {
        List<SavedRequest> requests =
                requestsByFolder.get(folderId);

        if (requests == null) {
            return;
        }

        for (SavedRequest request : requests) {
            folderItem.getChildren().add(
                    new TreeItem<>(
                            WorkspaceItem.request(request)
                    )
            );
        }
    }

    private void handleSelection(
            TreeItem<WorkspaceItem> selectedItem
    ) {
        if (selectedItem == null
                || selectedItem.getValue() == null) {
            return;
        }

        WorkspaceItem item =
                selectedItem.getValue();

        if (item.type() != WorkspaceItemType.REQUEST) {
            return;
        }

        SavedRequest request =
                item.request();

        if (request != null) {
            requestSelectionHandler.accept(
                    request
            );
        }
    }

    private void showInformation(
            String title,
            String message
    ) {
        Alert alert =
                new Alert(
                        Alert.AlertType.INFORMATION
                );

        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(
            String title,
            String message
    ) {
        Alert alert =
                new Alert(
                        Alert.AlertType.ERROR
                );

        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static final class WorkspaceTreeCell
            extends TreeCell<WorkspaceItem> {

        @Override
        protected void updateItem(
                WorkspaceItem item,
                boolean empty
        ) {
            super.updateItem(
                    item,
                    empty
            );

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            setText(item.displayName());

            setGraphic(
                    new IconView(
                            item.icon(),
                            16
                    )
            );
        }
    }

    private enum WorkspaceItemType {
        COLLECTION,
        FOLDER,
        REQUEST
    }

    private record WorkspaceItem(
            WorkspaceItemType type,
            String id,
            String displayName,
            Icon icon,
            SavedRequest request,
            Collection collection
    ) {

        private static WorkspaceItem collection(
                Collection collection
        ) {
            return new WorkspaceItem(
                    WorkspaceItemType.COLLECTION,
                    collection.id(),
                    collection.name(),
                    Icon.COLLECTION,
                    null,
                    collection
            );
        }

        private static WorkspaceItem folder(
                CollectionFolder folder
        ) {
            return new WorkspaceItem(
                    WorkspaceItemType.FOLDER,
                    folder.id(),
                    folder.name(),
                    Icon.FOLDER,
                    null,
                    null
            );
        }

        private static WorkspaceItem request(
                SavedRequest request
        ) {
            return new WorkspaceItem(
                    WorkspaceItemType.REQUEST,
                    request.id(),
                    request.name(),
                    Icon.REQUEST,
                    request,
                    null
            );
        }
    }
}