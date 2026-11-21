package com.mirakinetix.callsmith;

import com.mirakinetix.callsmith.domain.environment.Environment;
import com.mirakinetix.callsmith.domain.request.SavedRequest;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.json.JacksonJsonSerializer;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteCollectionRepository;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteEnvironmentRepository;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteEnvironmentVariableRepository;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteSavedRequestRepository;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteSecretStorage;
import com.sun.net.httpserver.HttpServer;
import javafx.event.Event;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.mirakinetix.callsmith.DesktopFx.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "callsmith.desktop.tests", matches = "true")
class WorkspaceDesktopTest {
    private static final String PASSPHRASE = "fictional workspace test passphrase";
    private static final String LEGACY = "fictional-old-key";
    @TempDir Path directory;

    @Test
    void createsEditsMovesDeletesAndReopensAnActualWorkspace() throws Exception {
        DesktopFx.start();
        Path database = directory.resolve("ui-workspace.db");
        var repository = new SqliteDatabase(database);
        var server = localServer();
        server.createContext("/request", exchange -> {
            try (exchange) {
                var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                var text = exchange.getRequestMethod() + "|" + exchange.getRequestURI().getRawQuery()
                        + "|" + exchange.getRequestHeaders().getFirst("X-Fixture") + "|" + body;
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        Opened opened = null;
        try {
            opened = open(database);
            Parent main = opened.root();
            Stage stage = opened.stage();
            assertEquals(List.of(16, 32, 48, 64, 128, 256), fx(() ->
                    stage.getIcons().stream().map(image -> (int) image.getWidth()).toList()));
            verifyResize(main, stage);
            addNamed(main, "New", "Create Collection", "First", stage);
            select(main, "First");
            addNamed(main, "Rename", "Rename Collection", "Renamed", stage);
            addNamed(main, "New", "Create Collection", "Other", stage);
            addFolder(main, "Renamed", "Parent", stage);
            expand(main, "Parent");
            addFolder(main, "Parent", "Nested", stage);
            expand(main, "Nested");
            select(main, "Nested");

            fx(() -> {
                combo(main, "#request-method").setValue("POST");
                ((TextField) main.lookup("#request-url")).setText(base(server) + "/request");
                ((TextArea) main.lookup("#request-body")).setText("fixture body");
                return null;
            });
            addPair(main, "#request-query", "Add Query Parameter", "page", "two words", stage);
            addPair(main, "#request-headers", "Add Header", "X-Fixture", "ui", stage);
            save(main, "Save", "Renamed / Parent / Nested", "Original", stage);
            var initial = findRequest(repository, "Renamed", "Original");
            assertNotNull(initial.folderId());
            assertEquals("POST", initial.method());
            assertEquals("two words", initial.queryParameters().get("page"));

            fire(main, "Send");
            awaitBody(main, "POST|page=two+words|ui|fixture body");
            fx(() -> { ((TextArea) main.lookup("#request-body")).setText("edited"); return null; });
            fire(main, "Save");
            var edited = findRequest(repository, "Renamed", "Original");
            assertEquals(initial.id(), edited.id());
            assertEquals("edited", edited.body());
            assertTrue(fx(() -> cell(main, "Parent").getTreeItem().isExpanded()));
            assertTrue(fx(() -> cell(main, "Nested").getTreeItem().isExpanded()));

            save(main, "Save As", "Renamed / Parent / Nested", "Second request", stage);
            var second = findRequest(repository, "Renamed", "Second request");
            assertNotEquals(initial.id(), second.id());
            var duplicate = action(() -> menu(main, "Second request", "Duplicate Request").fire());
            finish(duplicate);
            assertNotEquals(second.id(), findRequest(repository, "Renamed", "Second request copy").id());

            move(main, "Original", "Renamed / Parent", stage);
            assertNotEquals(initial.folderId(), findRequest(repository, "Renamed", "Original").folderId());
            move(main, "Original", "Other", stage);
            var moved = findRequest(repository, "Other", "Original");
            assertEquals(initial.id(), moved.id());
            assertNull(moved.folderId());

            var deletion = action(() -> menu(main, "Second request copy", "Delete Request").fire());
            var confirmation = dialog("Delete Request");
            fx(() -> { choose(confirmation, "OK"); return null; });
            finish(deletion);
            assertTrue(new SqliteSavedRequestRepository(repository, new JacksonJsonSerializer())
                    .findByCollectionId(second.collectionId()).stream()
                    .noneMatch(request -> request.name().equals("Second request copy")));

            select(main, "Second request");
            fx(() -> { ((TextArea) main.lookup("#request-body")).setText("unsaved draft"); return null; });
            var switching = action(() -> selectCell(main, "Original"));
            var warning = await(WorkspaceDesktopTest::discardDialog, pane -> pane != null, "discard warning");
            fx(() -> { choose(warning, "Cancel"); return null; });
            finish(switching);
            assertEquals("unsaved draft", fx(() -> ((TextArea) main.lookup("#request-body")).getText()));

            var closing = action(() -> Event.fireEvent(stage,
                    new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));
            var closeWarning = await(WorkspaceDesktopTest::discardDialog, pane -> pane != null, "close discard warning");
            fx(() -> { choose(closeWarning, "Yes"); return null; });
            finish(closing);
            assertFalse(fx(stage::isShowing));
            close(opened);
            opened = open(database);
            Parent reopened = opened.root();
            select(reopened, "Original");
            fire(reopened, "Send");
            awaitBody(reopened, "POST|page=two+words|ui|edited");
            assertEquals(initial.id(), findRequest(repository, "Other", "Original").id());
        } finally {
            close(opened);
            server.stop(0);
        }
    }

    @Test
    void setsUpMigratesLocksRejectsWrongPassphraseAndReopensProtectedWorkspace() throws Exception {
        DesktopFx.start();
        Path database = directory.resolve("secure-ui.db");
        var sqlite = new SqliteDatabase(database);
        new CallsmithDatabase(sqlite, new MigrationRunner()).initialize();
        var now = Instant.now();
        new SqliteEnvironmentRepository(sqlite).save(new Environment("env", "Test environment", true, now, now));
        try (var connection = sqlite.openConnection();
             var insert = connection.prepareStatement("""
                     INSERT INTO environment_variables (id, environment_id, name, stored_value,
                         is_secret, created_at, updated_at, protection_version)
                     VALUES ('legacy', 'env', 'legacy_key', ?, 1, ?, ?, 0)
                     """)) {
            insert.setString(1, LEGACY);
            insert.setString(2, now.toString());
            insert.setString(3, now.toString());
            insert.executeUpdate();
        }
        var request = new CompletableFuture<String>();
        var server = localServer();
        server.createContext("/secret", exchange -> {
            try (exchange) {
                request.complete(exchange.getRequestURI().getRawQuery());
                byte[] bytes = "protected request reached server".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        Opened opened = null;
        try {
            opened = open(database);
            Parent main = opened.root();
            Stage manager = manager(main);
            var setup = action(() -> button(manager.getScene().getRoot(), "Secret Storage").fire());
            var setupPane = dialog("Secret Storage");
            fx(() -> {
                assertSame(manager, ((Stage) setupPane.getScene().getWindow()).getOwner());
                for (var field : passwords(setupPane)) field.setText(PASSPHRASE);
                ((CheckBox) setupPane.lookup(".check-box")).setSelected(true);
                choose(setupPane, "Set Up and Protect");
                return null;
            });
            finish(setup);
            assertMasked(manager.getScene().getRoot(), LEGACY);
            var added = action(() -> button(manager.getScene().getRoot(), "Add Variable").fire());
            var variable = dialog("Add Variable");
            fx(() -> {
                ((CheckBox) variable.lookup(".check-box")).fire();
                textFields(variable).stream().filter(TextField::isVisible).findFirst().orElseThrow()
                        .setText("new_key");
                passwords(variable).getFirst().setText("fictional-added-secret");
                choose(variable, "OK");
                return null;
            });
            finish(added);
            assertMasked(manager.getScene().getRoot(), "fictional-added-secret");
            var lock = action(() -> button(manager.getScene().getRoot(), "Secret Storage").fire());
            var lockPane = dialog("Secret Storage");
            fx(() -> { choose(lockPane, "OK"); return null; });
            finish(lock);
            fx(() -> { manager.hide(); return null; });
            enterSecretRequest(main, base(server));
            fire(main, "Send");
            awaitBody(main, "Unlock Secret Storage");
            assertFalse(request.isDone());

            Stage managerAgain = manager(main);
            unlock(managerAgain, true);
            fx(() -> { managerAgain.hide(); return null; });
            fire(main, "Send");
            awaitBody(main, "protected request reached server");
            assertEquals("key=" + LEGACY, request.get(3, TimeUnit.SECONDS));

            close(opened);
            opened = open(database);
            Parent reopened = opened.root();
            Stage restartedManager = manager(reopened);
            unlock(restartedManager, false);
            assertMasked(restartedManager.getScene().getRoot(), "fictional-added-secret");

            var store = new SqliteSecretStorage(sqlite);
            store.unlock(PASSPHRASE.toCharArray());
            var variables = new SqliteEnvironmentVariableRepository(sqlite, store);
            assertEquals(LEGACY, variables.findByName("env", "legacy_key").orElseThrow().value());
            assertEquals("fictional-added-secret", variables.findByName("env", "new_key").orElseThrow().value());
        } finally {
            close(opened);
            server.stop(0);
        }
    }

    private static void unlock(Stage manager, boolean checkWrong) throws Exception {
        var attempt = action(() -> button(manager.getScene().getRoot(), "Secret Storage").fire());
        var pane = dialog("Secret Storage");
        if (checkWrong) {
            fx(() -> {
                passwords(pane).getFirst().setText("wrong fictional test passphrase");
                choose(pane, "Unlock");
                return null;
            });
            await(() -> labels(pane), text -> text.contains("Unable to unlock"), "wrong passphrase rejection");
        }
        fx(() -> {
            passwords(pane).getFirst().setText(PASSPHRASE);
            choose(pane, "Unlock");
            return null;
        });
        finish(attempt);
    }

    private static void verifyResize(Parent main, Stage stage) throws Exception {
        fx(() -> {
            stage.setWidth(900);
            stage.setHeight(560);
            return null;
        });
        await(() -> main.getScene().getWidth(), width -> width < 940, "resized scene");
        fx(() -> {
            main.applyCss();
            main.layout();
            var send = main.lookup("#request-send");
            var bounds = send.localToScene(send.getBoundsInLocal());
            assertTrue(bounds.getMaxX() <= main.getScene().getWidth(), "Send must stay inside the window");
            assertTrue(bounds.getWidth() > 0, "Send must keep a usable width");
            stage.setWidth(1200);
            stage.setHeight(750);
            return null;
        });
    }

    private static void enterSecretRequest(Parent main, String base) throws Exception {
        fx(() -> {
            ((TextField) main.lookup("#request-url")).setText(base + "/secret?key={{legacy_key}}");
            return null;
        });
    }

    private static void assertMasked(Parent root, String value) throws Exception {
        String rendered = await(() -> labels(root) + root.lookupAll(".table-cell").stream()
                        .filter(Labeled.class::isInstance).map(Labeled.class::cast).map(Labeled::getText).toList(),
                text -> text.contains("********"), "masked environment value");
        assertFalse(rendered.contains(value));
    }

    private static Stage manager(Parent main) throws Exception {
        fire(main, "Manage");
        return await(() -> Window.getWindows().stream().filter(Stage.class::isInstance).map(Stage.class::cast)
                .filter(stage -> "Callsmith - Environments".equals(stage.getTitle())).findFirst().orElse(null),
                stage -> stage != null, "environment manager");
    }

    private static void addPair(Parent root, String selector, String title, String key, String value, Stage owner)
            throws Exception {
        var opening = action(() -> button((Parent) root.lookup(selector), "Add").fire());
        var pane = dialog(title);
        fx(() -> {
            assertSame(owner, ((Stage) pane.getScene().getWindow()).getOwner());
            var fields = textFields(pane);
            fields.stream().filter(field -> field.getPromptText().endsWith("name")).findFirst().orElseThrow().setText(key);
            fields.stream().filter(field -> field.getPromptText().endsWith("value")).findFirst().orElseThrow().setText(value);
            choose(pane, "Save");
            return null;
        });
        finish(opening);
    }

    private static void addNamed(Parent root, String trigger, String title, String name, Stage owner) throws Exception {
        var opening = action(() -> button(root, trigger).fire());
        setName(dialog(title), name, owner);
        finish(opening);
    }

    private static void addFolder(Parent main, String parent, String name, Stage owner) throws Exception {
        var opening = action(() -> menu(main, parent, "New Folder").fire());
        setName(dialog("New Folder"), name, owner);
        finish(opening);
    }

    private static void setName(DialogPane pane, String name, Stage owner) throws Exception {
        fx(() -> {
            assertSame(owner, ((Stage) pane.getScene().getWindow()).getOwner());
            textFields(pane).getFirst().setText(name);
            choose(pane, "OK");
            return null;
        });
    }

    private static void save(Parent main, String trigger, String destination, String name, Stage owner) throws Exception {
        var opening = action(() -> button(main, trigger).fire());
        chooseDestination(dialog("Save Request"), destination, owner);
        setName(dialog("Save Request"), name, owner);
        finish(opening);
    }

    private static void move(Parent main, String request, String destination, Stage owner) throws Exception {
        var opening = action(() -> menu(main, request, "Move Request").fire());
        chooseDestination(dialog("Move Request"), destination, owner);
        finish(opening);
    }

    @SuppressWarnings("unchecked")
    private static void chooseDestination(DialogPane pane, String destination, Stage owner) throws Exception {
        fx(() -> {
            assertSame(owner, ((Stage) pane.getScene().getWindow()).getOwner());
            var choices = (ComboBox<Object>) pane.lookup(".combo-box");
            var chosen = choices.getItems().stream().filter(item -> item.toString().equals(destination))
                    .findFirst().orElseThrow();
            choices.getSelectionModel().select(chosen);
            choose(pane, "OK");
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> combo(Parent root, String selector) {
        return (ComboBox<String>) root.lookup(selector);
    }

    private static List<TextField> textFields(Parent root) {
        return root.lookupAll(".text-field").stream().filter(TextField.class::isInstance).map(TextField.class::cast)
                .filter(field -> !(field instanceof PasswordField)).toList();
    }

    private static List<PasswordField> passwords(Parent root) {
        return root.lookupAll(".password-field").stream().map(PasswordField.class::cast).toList();
    }

    private static String labels(Parent root) {
        return root.lookupAll(".label").stream().map(Label.class::cast).map(Label::getText).toList().toString();
    }

    private static DialogPane discardDialog() {
        return Window.getWindows().stream().map(window -> window.getScene().getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> pane.getContentText() != null && pane.getContentText().startsWith("Discard"))
                .findFirst().orElse(null);
    }

    private static void select(Parent root, String name) throws Exception {
        fx(() -> { selectCell(root, name); return null; });
    }

    @SuppressWarnings("unchecked")
    private static void selectCell(Parent root, String name) {
        var tree = (TreeView<Object>) root.lookup("#collection-tree");
        tree.getSelectionModel().select((TreeItem<Object>) cell(root, name).getTreeItem());
    }

    private static void expand(Parent root, String name) throws Exception {
        fx(() -> { cell(root, name).getTreeItem().setExpanded(true); return null; });
    }

    private static TreeCell<?> cell(Parent root, String name) {
        root.applyCss();
        root.layout();
        return root.lookupAll(".tree-cell").stream().filter(TreeCell.class::isInstance)
                .map(node -> (TreeCell<?>) node).filter(cell -> name.equals(cell.getText())).findFirst().orElseThrow();
    }

    private static MenuItem menu(Parent root, String target, String action) {
        return cell(root, target).getContextMenu().getItems().stream()
                .filter(item -> action.equals(item.getText())).findFirst().orElseThrow();
    }

    private static void fire(Parent root, String button) throws Exception {
        fx(() -> { button(root, button).fire(); return null; });
    }

    private static void awaitBody(Parent root, String expected) throws Exception {
        await(() -> ((TextArea) root.lookup("#response-body")).getText(),
                text -> text.contains(expected), "expected response");
    }

    private static SavedRequest findRequest(SqliteDatabase database, String collection, String name) {
        var id = new SqliteCollectionRepository(database).findAll().stream().filter(item -> item.name().equals(collection))
                .findFirst().orElseThrow().id();
        return new SqliteSavedRequestRepository(database, new JacksonJsonSerializer()).findByCollectionId(id).stream()
                .filter(item -> item.name().equals(name)).findFirst().orElseThrow();
    }

    private static Opened open(Path database) throws Exception {
        return fx(() -> {
            var stage = new Stage();
            var app = new CallsmithApplication();
            app.startWorkspace(stage, database);
            return new Opened(app, stage, stage.getScene().getRoot());
        });
    }

    private static void close(Opened opened) throws Exception {
        closeWindows();
        if (opened != null) opened.app().stop();
    }

    private static HttpServer localServer() throws Exception { return HttpServer.create(new InetSocketAddress("localhost", 0), 0); }
    private static String base(HttpServer server) { return "http://localhost:" + server.getAddress().getPort(); }
    private record Opened(CallsmithApplication app, Stage stage, Parent root) {}
}
