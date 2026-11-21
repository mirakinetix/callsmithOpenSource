package com.mirakinetix.callsmith.ui.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.mirakinetix.callsmith.DesktopFx;
import com.mirakinetix.callsmith.application.collection.CollectionExportService;
import com.mirakinetix.callsmith.application.collection.CollectionWorkspaceService;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImportPersistenceService;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImportService;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImporter;
import com.mirakinetix.callsmith.domain.collection.Collection;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import com.mirakinetix.callsmith.infrastructure.collection.PostmanCollectionExporter;
import com.mirakinetix.callsmith.infrastructure.collection.importation.SqliteCollectionImportPersistenceService;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.json.JacksonJsonSerializer;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteCollectionFolderRepository;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteCollectionRepository;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteSavedRequestRepository;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.mirakinetix.callsmith.DesktopFx.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "callsmith.desktop.tests", matches = "true")
class CollectionFilesDesktopTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private CollectionWorkspaceService workspace;
    private CollectionImportPersistenceService persistence;

    @BeforeEach
    void setup() throws Exception {
        DesktopFx.start();
        var database = new SqliteDatabase(directory.resolve("desktop.db"));
        new CallsmithDatabase(database, new MigrationRunner()).initialize();
        var json = new JacksonJsonSerializer();
        workspace = new CollectionWorkspaceService(new SqliteCollectionRepository(database),
                new SqliteCollectionFolderRepository(database), new SqliteSavedRequestRepository(database, json));
        persistence = new SqliteCollectionImportPersistenceService(database, json);
    }

    @AfterEach
    void close() throws Exception {
        closeWindows();
    }

    @Test
    void importWorkersLeaveJavaFxResponsiveAndPreventDuplicateImports() throws Exception {
        var reading = new CountDownLatch(1);
        var readRelease = new CountDownLatch(1);
        var saving = new CountDownLatch(1);
        var saveRelease = new CountDownLatch(1);
        var reads = new AtomicInteger();
        var saves = new AtomicInteger();
        var sidebar = show(root -> {
            assertFalse(Platform.isFxApplicationThread());
            reads.incrementAndGet();
            reading.countDown();
            waitFor(readRelease);
            return collection("Imported fixture");
        }, imported -> {
            assertFalse(Platform.isFxApplicationThread());
            saves.incrementAndGet();
            saving.countDown();
            waitFor(saveRelease);
            persistence.persist(imported);
        });
        var file = fixture();
        try {
            fx(() -> { sidebar.importFile(file); return null; });
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            fx(() -> {
                assertTrue(button(sidebar, "Import").isDisabled());
                assertTrue(button(sidebar, "Cancel import").isVisible());
                assertEquals("Reading collection...", status(sidebar));
                sidebar.importFile(file);
                return null;
            });
            readRelease.countDown();
            assertTrue(saving.await(5, TimeUnit.SECONDS));
            fx(() -> {
                assertEquals("Saving collection...", status(sidebar));
                assertFalse(button(sidebar, "Cancel import").isVisible());
                sidebar.importFile(file);
                return null;
            });
            saveRelease.countDown();
            var success = dialog("Import successful");
            assertTrue(fx(() -> success.getContentText().contains("Imported fixture")));
            fx(() -> { choose(success, "OK"); return null; });
            assertFalse(fx(() -> button(sidebar, "Import").isDisabled()));
            assertEquals(List.of("Imported fixture"), workspace.findCollections().stream().map(Collection::name).toList());
            assertEquals(1, reads.get());
            assertEquals(1, saves.get());
        } finally {
            readRelease.countDown();
            saveRelease.countDown();
        }
    }

    @Test
    void cancelsBeforeSavingAndReportsAFailedImport() throws Exception {
        var reading = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var saves = new AtomicInteger();
        var sidebar = show(root -> {
            reading.countDown();
            waitFor(release);
            return collection("Cancelled fixture");
        }, imported -> saves.incrementAndGet());
        var file = fixture();
        try {
            fx(() -> { sidebar.importFile(file); return null; });
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            fx(() -> {
                button(sidebar, "Cancel import").fire();
                assertTrue(button(sidebar, "Import").isDisabled());
                return null;
            });
            release.countDown();
            await(sidebar::isImportRunning, running -> !running, "cancelled import");
            assertEquals(0, saves.get());
            assertTrue(workspace.findCollections().isEmpty());

            Files.writeString(file, "{");
            fx(() -> { sidebar.importFile(file); return null; });
            var error = dialog("Import failed");
            assertEquals("Malformed collection JSON.", fx(error::getContentText));
            fx(() -> { choose(error, "OK"); return null; });
            assertFalse(fx(() -> button(sidebar, "Import").isDisabled()));
            assertEquals(0, saves.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void confirmsActualNormalizedExportPathAndPreservesCancelledExport() throws Exception {
        var saved = workspace.saveCollection(new Collection("fixture", "Export fixture", Instant.EPOCH, Instant.EPOCH));
        var sidebar = show(root -> collection("Unused"), persistence);
        var selected = directory.resolve("report");
        var destination = Files.writeString(directory.resolve("report.json"), "original");
        var opening = action(() -> sidebar.exportCollection(saved, selected));
        var confirm = dialog("Replace collection export");
        assertTrue(fx(() -> confirm.getContentText().contains(destination.toString())));
        fx(() -> { choose(confirm, "Cancel"); return null; });
        finish(opening);
        assertEquals("original", Files.readString(destination));

        var exporting = action(() -> sidebar.exportCollection(saved, selected));
        var replacement = dialog("Replace collection export");
        fx(() -> { choose(replacement, "OK"); return null; });
        var success = dialog("Export successful");
        fx(() -> { choose(success, "OK"); return null; });
        finish(exporting);
        assertEquals("Export fixture", mapper.readTree(destination.toFile()).path("info").path("name").asText());
        assertFalse(Files.exists(selected));
    }

    private CollectionSidebar show(Function<JsonNode, ImportedCollection> read,
                                   CollectionImportPersistenceService save) throws Exception {
        var importer = new CollectionImporter() {
            @Override public ObjectReader documentReader() { return mapper.reader(); }
            @Override public boolean supportsDocument(JsonNode root) { return true; }
            @Override public ImportedCollection importDocument(JsonNode root) { return read.apply(root); }
        };
        return fx(() -> {
            var stage = new Stage();
            var sidebar = new CollectionSidebar(workspace,
                    new CollectionExportService(workspace, List.of(new PostmanCollectionExporter(mapper))),
                    new CollectionImportService(List.of(importer)), save, request -> true);
            stage.setScene(new Scene(sidebar, 300, 550));
            stage.show();
            return sidebar;
        });
    }

    private Path fixture() throws Exception {
        return Files.writeString(directory.resolve("fictional.json"), "{}");
    }

    private static ImportedCollection collection(String name) {
        return new ImportedCollection(name, List.of(), List.of());
    }

    private static String status(CollectionSidebar sidebar) {
        return ((Label) sidebar.lookup("#collection-import-status")).getText();
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            assertTrue(latch.await(7, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Fixture interrupted", exception);
        }
    }
}
