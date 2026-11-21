package com.mirakinetix.callsmith.ui.collection;

import com.mirakinetix.callsmith.application.collection.CollectionExportService;
import com.mirakinetix.callsmith.application.collection.CollectionWorkspaceService;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImportPersistenceService;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImportService;
import com.mirakinetix.callsmith.domain.collection.Collection;
import com.mirakinetix.callsmith.domain.collection.CollectionFolder;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import com.mirakinetix.callsmith.domain.request.SavedRequest;
import com.mirakinetix.callsmith.ui.Icon;
import com.mirakinetix.callsmith.ui.IconView;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.ArrayList;
import com.mirakinetix.callsmith.ui.request.RequestBuilderView;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;

public final class CollectionSidebar extends BorderPane {

    private static final double WIDTH = 280;

    private final CollectionWorkspaceService workspaceService;
    private final CollectionExportService exportService;
    private final CollectionImportService importService;
    private final CollectionImportPersistenceService
            importPersistenceService;
    private final Predicate<SavedRequest> requestSelectionHandler;

    private final TreeView<WorkspaceItem> treeView =
            new TreeView<>();

    public CollectionSidebar(
            CollectionWorkspaceService workspaceService,
            CollectionExportService exportService,
            CollectionImportService importService,
            CollectionImportPersistenceService
                    importPersistenceService,
            Predicate<SavedRequest> requestSelectionHandler
    ) {
        if (workspaceService == null) {
            throw new IllegalArgumentException(
                    "Collection workspace service is required"
            );
        }

        if (exportService == null) {
            throw new IllegalArgumentException(
                    "Collection export service is required"
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

        this.exportService =
                exportService;

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

    private RequestBuilderView editor;
    private boolean restoringSelection;

    public void bindRequestEditor(RequestBuilderView editor) {
        this.editor = editor;
        editor.setRequestActions(() -> saveEditor(false), () -> saveEditor(true));
    }

    private void saveEditor(boolean copy) {
        SavedRequest existing = editor.savedRequest();
        try {
            if (!copy && existing != null) {
                SavedRequest current = workspaceService.findSavedRequest(existing.id())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "The saved request was deleted. Use Save As to keep your draft."));
                persistEditor(current, current.collectionId(), current.folderId(), current.name());
                return;
            }

            Destination destination = chooseDestination("Save Request");
            if (destination == null) {
                return;
            }
            promptName("Save Request", existing == null ? "" : existing.name() + " copy")
                    .ifPresent(name -> persistEditor(null, destination.collectionId(),
                            destination.folderId(), name));
        } catch (RuntimeException exception) {
            showError("Unable to save request", workspaceError(exception));
        }
    }

    private void persistEditor(SavedRequest existing, String collectionId,
                               String folderId, String name) {
        RequestBuilderView.Draft draft = editor.draft();
        if (draft.url().isBlank()) {
            throw new IllegalArgumentException("Enter a URL before saving.");
        }

        Instant now = Instant.now();
        SavedRequest saved = workspaceService.saveRequest(new SavedRequest(
                existing == null ? UUID.randomUUID().toString() : existing.id(),
                collectionId, folderId, name, draft.method(), draft.url().trim(),
                draft.headers(), draft.query(), draft.body(),
                existing == null ? 0 : existing.sortOrder(),
                existing == null ? now : existing.createdAt(), now));
        editor.markSaved(saved);
        reloadAfterWrite(WorkspaceItem.request(saved));
    }

    private Destination chooseDestination(String title) {
        List<Destination> destinations = new ArrayList<>();
        collectDestinations(treeView.getRoot(), "", null, destinations);
        if (destinations.isEmpty()) {
            showInformation(title, "Create a collection first.");
            return null;
        }
        TreeItem<WorkspaceItem> selected = treeView.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getValue().type() == WorkspaceItemType.REQUEST) {
            selected = selected.getParent();
        }
        String selectedId = selected == null ? null : selected.getValue().id();
        Destination initial = destinations.stream()
                .filter(destination -> destination.selectionId().equals(selectedId))
                .findFirst().orElse(destinations.getFirst());
        ChoiceDialog<Destination> dialog = new ChoiceDialog<>(initial, destinations);
        prepareDialog(dialog, title);
        dialog.setHeaderText("Select a collection or folder");
        return dialog.showAndWait().orElse(null);
    }

    private void collectDestinations(TreeItem<WorkspaceItem> parent, String path,
                                     String collectionId, List<Destination> destinations) {
        for (TreeItem<WorkspaceItem> child : parent.getChildren()) {
            WorkspaceItem item = child.getValue();
            if (item.type() == WorkspaceItemType.REQUEST) {
                continue;
            }
            String childPath = path.isEmpty() ? item.displayName() : path + " / " + item.displayName();
            boolean collection = item.type() == WorkspaceItemType.COLLECTION;
            String ownerId = collection ? item.id() : collectionId;
            destinations.add(new Destination(ownerId, collection ? null : item.id(), childPath));
            collectDestinations(child, childPath, ownerId, destinations);
        }
    }

    private record Destination(String collectionId, String folderId, String path) {
        String selectionId() {
            return folderId == null ? collectionId : folderId;
        }

        @Override
        public String toString() {
            return path;
        }
    }

    private java.util.Optional<String> promptName(String title, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        prepareDialog(dialog, title);
        dialog.setHeaderText(null);
        dialog.setContentText("Name");
        return dialog.showAndWait().map(String::trim).filter(name -> !name.isEmpty());
    }

    private void prepareDialog(Dialog<?> dialog, String title) {
        dialog.setTitle(title);
        applyBrandStyles(dialog);
    }

    private boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        prepareDialog(alert, title);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void reloadAfterWrite() {
        reloadAfterWrite(null);
    }

    private void reloadAfterWrite(WorkspaceItem preferred) {
        try {
            loadCollections(preferred);
        } catch (RuntimeException exception) {
            showError("Workspace refresh failed", "The change was saved, but the tree could not be refreshed.");
        }
    }

    private String workspaceError(RuntimeException exception) {
        if (exception instanceof IllegalArgumentException) {
            return exception.getMessage();
        }
        return "Callsmith could not access the workspace database.";
    }

    private MenuItem action(String label, Icon icon, Runnable operation) {
        MenuItem menuItem = new MenuItem(label, new IconView(icon));
        menuItem.setOnAction(event -> {
            try {
                operation.run();
            } catch (RuntimeException exception) {
                showError(label, workspaceError(exception));
            }
        });
        return menuItem;
    }

    private ContextMenu contextMenu(WorkspaceItem item, TreeItem<WorkspaceItem> treeItem) {
        if (item.type() != WorkspaceItemType.REQUEST) {
            TreeItem<WorkspaceItem> ancestor = treeItem;
            while (ancestor.getValue().type() == WorkspaceItemType.FOLDER) {
                ancestor = ancestor.getParent();
            }
            String collectionId = ancestor.getValue().id();
            String parentId = item.type() == WorkspaceItemType.FOLDER ? item.id() : null;
            ContextMenu menu = new ContextMenu(action("New Folder", Icon.FOLDER, () ->
                    promptName("New Folder", "").ifPresent(name -> {
                        workspaceService.createFolder(collectionId, parentId, name);
                        reloadAfterWrite();
                    })));
            if (parentId != null) {
                menu.getItems().addAll(
                        action("Rename Folder", Icon.RENAME, () -> promptName("Rename Folder", item.displayName())
                                .ifPresent(name -> {
                                    workspaceService.renameFolder(item.id(), name);
                                    reloadAfterWrite();
                                })),
                        action("Delete Empty Folder", Icon.DELETE, () -> {
                            if (confirm("Delete Folder", "Delete " + item.displayName() + "?")) {
                                workspaceService.deleteEmptyFolder(item.id());
                                reloadAfterWrite();
                            }
                        }));
            }
            return menu;
        }

        SavedRequest request = item.request();
        return new ContextMenu(
                action("Rename Request", Icon.RENAME, () -> promptName("Rename Request", request.name())
                        .ifPresent(name -> updateRequest(request, name, null, false))),
                action("Duplicate Request", Icon.DUPLICATE, () ->
                        updateRequest(request, request.name() + " copy", null, true)),
                action("Move Request", Icon.MOVE, () -> {
                    Destination destination = chooseDestination("Move Request");
                    if (destination != null) {
                        updateRequest(request, request.name(), destination, false);
                    }
                }),
                action("Delete Request", Icon.DELETE, () -> {
                    boolean active = isOpen(request);
                    if (confirm("Delete Request", "Delete " + request.name() + "?")
                            && (!active || editor.allowDiscard())) {
                        workspaceService.deleteRequest(request.id());
                        if (active) {
                            editor.clearRequest();
                        }
                        reloadAfterWrite();
                    }
                }));
    }

    private boolean isOpen(SavedRequest request) {
        return editor != null && editor.savedRequest() != null
                && editor.savedRequest().id().equals(request.id());
    }

    private void updateRequest(SavedRequest request, String name, Destination destination, boolean copy) {
        SavedRequest current = workspaceService.findSavedRequest(request.id()).orElseThrow(
                () -> new IllegalArgumentException("This request no longer exists."));
        Instant now = Instant.now();
        SavedRequest saved = workspaceService.saveRequest(new SavedRequest(
                copy ? UUID.randomUUID().toString() : current.id(),
                destination == null ? current.collectionId() : destination.collectionId(),
                destination == null ? current.folderId() : destination.folderId(),
                name, current.method(), current.url(), current.headers(), current.queryParameters(),
                current.body(), current.sortOrder(), copy ? now : current.createdAt(), now));
        if (!copy && isOpen(current)) {
            editor.updateSavedMetadata(saved);
            reloadAfterWrite(WorkspaceItem.request(saved));
        } else {
            reloadAfterWrite();
        }
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
                        8,
                        10,
                        4,
                        10
                )
        );

        Button createButton =
                createActionButton(
                        "New",
                        Icon.ADD,
                        "Create a collection",
                        this::createCollection
                );

        Button importButton =
                createActionButton(
                        "Import",
                        Icon.IMPORT,
                        "Import a collection from a file",
                        this::importCollection
                );

        Button exportButton =
                createActionButton(
                        "Export",
                        Icon.EXPORT,
                        "Export the selected collection as Postman JSON",
                        this::exportSelectedCollection
                );

        Button renameButton =
                createActionButton(
                        "Rename",
                        Icon.RENAME,
                        "Rename the selected collection",
                        this::renameSelectedCollection
                );

        Button deleteButton =
                createActionButton(
                        "Delete",
                        Icon.DELETE,
                        "Delete the selected collection",
                        this::deleteSelectedCollection
                );

        GridPane actions =
                new GridPane();

        actions.setHgap(6);
        actions.setVgap(6);
        actions.setPadding(
                new Insets(
                        4,
                        10,
                        8,
                        10
                )
        );

        actions.add(createButton, 0, 0);
        actions.add(importButton, 1, 0);
        actions.add(exportButton, 0, 1);
        actions.add(renameButton, 1, 1);
        actions.add(deleteButton, 0, 2);

        for (Button button :
                List.of(
                        createButton,
                        importButton,
                        exportButton,
                        renameButton,
                        deleteButton
                )) {
            button.setMaxWidth(Double.MAX_VALUE);
            GridPane.setHgrow(button, Priority.ALWAYS);
        }

        GridPane.setHgrow(actions, Priority.ALWAYS);

        VBox header =
                new VBox(
                        title,
                        actions
                );

        treeView.setId("collection-tree");
        treeView.setShowRoot(false);

        treeView.setCellFactory(
                ignored -> new WorkspaceTreeCell()
        );

        treeView.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable, oldItem, newItem) -> {
                            if (!restoringSelection && !handleSelection(newItem)) {
                                javafx.application.Platform.runLater(() -> {
                                    restoringSelection = true;
                                    try {
                                        treeView.getSelectionModel().select(oldItem);
                                    } finally {
                                        restoringSelection = false;
                                    }
                                });
                            }
                        }
                );

        setTop(header);
        setCenter(treeView);
    }

    private Button createActionButton(
            String text,
            Icon icon,
            String tooltipText,
            Runnable action
    ) {
        Button button =
                new Button(
                        text,
                        new IconView(
                                icon,
                                16
                        )
                );

        button.setTooltip(
                new Tooltip(tooltipText)
        );

        button.setOnAction(
                event -> {
                    try {
                        action.run();
                    } catch (RuntimeException exception) {
                        showError(text, workspaceError(exception));
                    }
                }
        );

        return button;
    }

    private void loadCollections() {
        loadCollections(null);
    }

    private void loadCollections(WorkspaceItem preferred) {
        TreeItem<WorkspaceItem> current = treeView.getSelectionModel().getSelectedItem();
        WorkspaceItem target = preferred != null ? preferred : current == null ? null : current.getValue();
        Map<ItemKey, Boolean> expansion = new java.util.HashMap<>();
        rememberExpansion(treeView.getRoot(), expansion);
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

        TreeItem<WorkspaceItem> selection = restoreExpansion(root, expansion,
                target == null ? null : ItemKey.of(target));
        restoringSelection = true;
        try {
            treeView.setRoot(root);
            if (selection != null) {
                for (var ancestor = selection.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
                    ancestor.setExpanded(true);
                }
                treeView.getSelectionModel().select(selection);
                treeView.scrollTo(treeView.getRow(selection));
            }
        } finally {
            restoringSelection = false;
        }
    }

    private void rememberExpansion(TreeItem<WorkspaceItem> item, Map<ItemKey, Boolean> expansion) {
        if (item == null) return;
        if (item.getValue() != null && item.getValue().type() != WorkspaceItemType.REQUEST) {
            expansion.put(ItemKey.of(item.getValue()), item.isExpanded());
        }
        for (var child : item.getChildren()) rememberExpansion(child, expansion);
    }

    private TreeItem<WorkspaceItem> restoreExpansion(TreeItem<WorkspaceItem> item,
                                                     Map<ItemKey, Boolean> expansion, ItemKey target) {
        TreeItem<WorkspaceItem> selection = null;
        if (item.getValue() != null) {
            ItemKey key = ItemKey.of(item.getValue());
            if (expansion.containsKey(key)) item.setExpanded(expansion.get(key));
            if (key.equals(target)) selection = item;
        }
        for (var child : item.getChildren()) {
            var match = restoreExpansion(child, expansion, target);
            if (match != null) selection = match;
        }
        return selection;
    }

    private record ItemKey(WorkspaceItemType type, String id) {
        private static ItemKey of(WorkspaceItem item) {
            return new ItemKey(item.type(), item.id());
        }
    }

    private void createCollection() {
        TextField nameField =
                new TextField();

        nameField.setPromptText(
                "Collection name"
        );

        Dialog<ButtonType> dialog =
                new Dialog<>();

        applyBrandStyles(dialog);

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
                ActionEvent.ACTION,
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

        applyBrandStyles(dialog);

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
                ActionEvent.ACTION,
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

        applyBrandStyles(confirmation);

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
                            boolean open = editor != null && editor.savedRequest() != null
                                    && editor.savedRequest().collectionId().equals(collection.id());
                            if (open && !editor.allowDiscard()) return;
                            try {
                                workspaceService.deleteCollection(collection.id());
                                if (open) editor.clearRequest();
                                reloadAfterWrite();
                            } catch (RuntimeException exception) {
                                showError("Delete Collection", workspaceError(exception));
                            }
                        }
                );
    }

    private void exportSelectedCollection() {
        Collection collection =
                getSelectedCollection();

        if (collection == null) {
            showError(
                    "Export Collection",
                    "Select a collection first."
            );

            return;
        }

        FileChooser fileChooser =
                new FileChooser();

        fileChooser.setTitle(
                "Export Postman Collection"
        );

        fileChooser.setInitialFileName(
                createExportFileName(
                        collection.name()
                )
        );

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Postman Collection JSON",
                        "*.json"
                )
        );

        Window window =
                getScene() == null
                        ? null
                        : getScene().getWindow();

        File selectedFile =
                fileChooser.showSaveDialog(window);

        if (selectedFile == null) {
            return;
        }

        File exportFile =
                ensureJsonExtension(selectedFile);

        try {
            exportService.exportCollection(
                    collection,
                    exportFile.toPath()
            );

            showInformation(
                    "Export successful",
                    "Exported collection to:"
                            + System.lineSeparator()
                            + exportFile.getAbsolutePath()
            );

        } catch (RuntimeException exception) {
            showError(
                    "Export failed",
                    exception.getMessage() == null
                            ? exception.toString()
                            : exception.getMessage()
            );
        }
    }

    private String createExportFileName(
            String collectionName
    ) {
        String safeName =
                collectionName == null
                        ? ""
                        : collectionName
                                .replaceAll(
                                        "[\\\\/:*?\"<>|]",
                                        "_"
                                )
                                .trim();

        if (safeName.isBlank()) {
            safeName = "collection";
        }

        return safeName + ".postman_collection.json";
    }

    private File ensureJsonExtension(
            File file
    ) {
        String fileName =
                file.getName();

        if (fileName.toLowerCase()
                .endsWith(".json")) {
            return file;
        }

        return new File(
                file.getParentFile(),
                fileName + ".json"
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

    private boolean handleSelection(
            TreeItem<WorkspaceItem> selectedItem
    ) {
        if (selectedItem == null
                || selectedItem.getValue() == null) {
            return true;
        }
        WorkspaceItem item = selectedItem.getValue();
        return item.type() != WorkspaceItemType.REQUEST || isOpen(item.request())
                || requestSelectionHandler.test(item.request());
    }

    private void applyBrandStyles(
            Dialog<?> dialog
    ) {
        if (getScene() != null && dialog.getOwner() == null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.getDialogPane()
                .getStylesheets()
                .add(
                        CollectionSidebar.class
                                .getResource("/styles/brand.css")
                                .toExternalForm()
                );
    }

    private void showInformation(
            String title,
            String message
    ) {
        Alert alert =
                new Alert(
                        Alert.AlertType.INFORMATION
                );

        applyBrandStyles(alert);

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

        applyBrandStyles(alert);

        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private final class WorkspaceTreeCell
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
                setContextMenu(null);
                return;
            }

            setText(item.displayName());
            TreeItem<WorkspaceItem> treeItem = getTreeItem();
            setContextMenu(treeItem == null ? null : contextMenu(item, treeItem));

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