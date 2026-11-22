package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirakinetix.callsmith.application.collection.CollectionExportService;
import com.mirakinetix.callsmith.application.collection.CollectionWorkspaceService;
import com.mirakinetix.callsmith.domain.collection.Collection;
import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;
import com.mirakinetix.callsmith.domain.request.SavedRequest;
import com.mirakinetix.callsmith.infrastructure.collection.PostmanCollectionExporter;
import com.mirakinetix.callsmith.infrastructure.collection.importation.PostmanCollectionImporter;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.json.JacksonJsonSerializer;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceRecoveryTest {
    @TempDir
    Path directory;

    private CollectionWorkspaceService openWorkspace() {
        var database = new SqliteDatabase(directory.resolve("workspace.db"));
        new CallsmithDatabase(database, new MigrationRunner()).initialize();
        return new CollectionWorkspaceService(new SqliteCollectionRepository(database),
                new SqliteCollectionFolderRepository(database),
                new SqliteSavedRequestRepository(database, new JacksonJsonSerializer()));
    }

    @Test
    void savesEditsAndMovesAcrossReopeningWithoutDuplicating() {
        var workspace = openWorkspace();
        Instant now = Instant.now();
        workspace.saveCollection(new Collection("first", "First", now, now));
        var second = workspace.saveCollection(new Collection("second", "Second", now, now));
        var folder = workspace.createFolder(second.id(), null, "Original folder");
        workspace.renameFolder(folder.id(), "Renamed folder");

        workspace.saveRequest(request("first", null, "Original"));
        assertThrows(IllegalArgumentException.class,
                () -> workspace.saveRequest(request("first", folder.id(), "Bad destination")));
        assertEquals("Original", workspace.findSavedRequest("request").orElseThrow().name());
        var updated = workspace.saveRequest(request(second.id(), folder.id(), "Edited"));

        var reopened = openWorkspace();
        assertEquals(updated, reopened.findSavedRequest("request").orElseThrow());
        assertEquals(List.of(updated), reopened.loadCollection(second).requestsByFolder().get(folder.id()));
        assertTrue(reopened.loadCollection(workspace.findCollections().stream()
                .filter(collection -> collection.id().equals("first")).findFirst().orElseThrow())
                .requestsByFolder().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> reopened.deleteEmptyFolder(folder.id()));
        reopened.deleteRequest(updated.id());
        reopened.deleteEmptyFolder(folder.id());
        assertTrue(openWorkspace().findSavedRequest(updated.id()).isEmpty());
        assertTrue(openWorkspace().loadCollection(second).foldersByParent().isEmpty());
    }

    @Test
    void postmanExportRoundTripsNestedFolderAndRequest() {
        var workspace = openWorkspace();
        Instant now = Instant.now();
        var collection = workspace.saveCollection(new Collection("first", "First", now, now));
        var parent = workspace.createFolder(collection.id(), null, "Parent");
        var folder = workspace.createFolder(collection.id(), parent.id(), "Child");
        var request = workspace.saveRequest(request(collection.id(), folder.id(), "Example"));
        var mapper = new ObjectMapper();
        Path export = directory.resolve("collection.json");
        new CollectionExportService(workspace, List.of(new PostmanCollectionExporter(mapper)))
                .exportCollection(collection, export);
        var imported = new PostmanCollectionImporter(mapper).importCollection(export);

        assertEquals(collection.name(), imported.name());
        assertEquals("Parent", imported.folders().getFirst().name());
        var child = imported.folders().getFirst().folders().getFirst();
        assertEquals("Child", child.name());
        var result = child.requests().getFirst();
        assertEquals(request.name(), result.name());
        assertEquals(request.method(), result.method());
        assertEquals(request.url(), result.url());
        assertEquals(request.headers(), result.headers());
        assertEquals(request.queryParameters(), result.queryParameters());
        assertEquals(request.body(), result.body());
    }

    @Test
    void environmentVariableStringOmitsItsValue() {
        Instant now = Instant.now();
        var variable = new EnvironmentVariable("id", "env", "NAME", "dummy-sensitive-value", true, now, now);
        assertFalse(variable.toString().contains(variable.value()));
    }

    private SavedRequest request(String collection, String folder, String name) {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        return new SavedRequest("request", collection, folder, name, "POST", "https://example.test/api",
                Map.of("Content-Type", "application/json"), Map.of("page", "2"),
                "{\"example\":true}", 0, timestamp, timestamp);
    }
}
