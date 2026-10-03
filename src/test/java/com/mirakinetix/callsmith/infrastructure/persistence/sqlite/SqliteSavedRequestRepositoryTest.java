package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.mirakinetix.callsmith.domain.collection.Collection;
import com.mirakinetix.callsmith.domain.collection.CollectionFolder;
import com.mirakinetix.callsmith.domain.request.SavedRequest;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.json.JacksonJsonSerializer;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqliteSavedRequestRepositoryTest {

    private SqliteDatabase database;
    private SqliteSavedRequestRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        Path databasePath =
                Files.createTempFile(
                        "callsmith-saved-request-test-",
                        ".db"
                );

        database =
                new SqliteDatabase(databasePath);

        CallsmithDatabase callsmithDatabase =
                new CallsmithDatabase(
                        database,
                        new MigrationRunner()
                );

        callsmithDatabase.initialize();

        repository =
                new SqliteSavedRequestRepository(
                        database,
                        new JacksonJsonSerializer()
                );
    }

    @Test
    void shouldSaveAndFindRequest() {
        createCollection("collection-1");

        SavedRequest request =
                createRequest(
                        "request-1",
                        "collection-1",
                        null,
                        "Get Users",
                        0
                );

        repository.save(request);

        var result =
                repository.findById("request-1");

        assertTrue(result.isPresent());
        assertEquals(request, result.get());
    }

    @Test
    void shouldReturnEmptyWhenRequestDoesNotExist() {
        var result =
                repository.findById("missing");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldUpdateExistingRequest() {
        createCollection("collection-1");

        Instant createdAt =
                Instant.parse(
                        "2026-10-02T10:00:00Z"
                );

        SavedRequest original =
                new SavedRequest(
                        "request-1",
                        "collection-1",
                        null,
                        "Original Name",
                        "GET",
                        "https://example.com/original",
                        Map.of(
                                "Accept",
                                "application/json"
                        ),
                        Map.of(
                                "page",
                                "1"
                        ),
                        null,
                        0,
                        createdAt,
                        createdAt
                );

        repository.save(original);

        Instant updatedAt =
                Instant.parse(
                        "2026-10-02T11:00:00Z"
                );

        SavedRequest updated =
                new SavedRequest(
                        "request-1",
                        "collection-1",
                        null,
                        "Updated Name",
                        "POST",
                        "https://example.com/updated",
                        Map.of(
                                "Content-Type",
                                "application/json"
                        ),
                        Map.of(
                                "page",
                                "2"
                        ),
                        "{\"name\":\"Callsmith\"}",
                        3,
                        createdAt,
                        updatedAt
                );

        repository.save(updated);

        var result =
                repository.findById("request-1");

        assertTrue(result.isPresent());
        assertEquals(updated, result.get());
    }

    @Test
    void shouldPersistHeadersAndQueryParameters() {
        createCollection("collection-1");

        Map<String, String> headers =
                new LinkedHashMap<>();

        headers.put(
                "Accept",
                "application/json"
        );

        headers.put(
                "X-Callsmith-Test",
                "saved-request"
        );

        Map<String, String> queryParameters =
                new LinkedHashMap<>();

        queryParameters.put(
                "search",
                "Callsmith"
        );

        queryParameters.put(
                "page",
                "2"
        );

        Instant timestamp =
                Instant.parse(
                        "2026-10-02T12:00:00Z"
                );

        SavedRequest request =
                new SavedRequest(
                        "request-1",
                        "collection-1",
                        null,
                        "Search",
                        "GET",
                        "https://example.com/search",
                        headers,
                        queryParameters,
                        null,
                        0,
                        timestamp,
                        timestamp
                );

        repository.save(request);

        SavedRequest loaded =
                repository.findById("request-1")
                        .orElseThrow();

        assertEquals(
                headers,
                loaded.headers()
        );

        assertEquals(
                queryParameters,
                loaded.queryParameters()
        );
    }

    @Test
    void shouldFindRequestsByCollectionIdInDeterministicOrder() {
        createCollection("collection-1");
        createCollection("collection-2");

        SavedRequest request1 =
                createRequest(
                        "request-1",
                        "collection-1",
                        null,
                        "Zebra",
                        2
                );

        SavedRequest request2 =
                createRequest(
                        "request-2",
                        "collection-1",
                        null,
                        "Alpha",
                        1
                );

        SavedRequest request3 =
                createRequest(
                        "request-3",
                        "collection-1",
                        null,
                        "Beta",
                        1
                );

        SavedRequest otherCollection =
                createRequest(
                        "request-4",
                        "collection-2",
                        null,
                        "Other",
                        0
                );

        repository.save(request1);
        repository.save(request2);
        repository.save(request3);
        repository.save(otherCollection);

        List<SavedRequest> requests =
                repository.findByCollectionId(
                        "collection-1"
                );

        assertEquals(
                List.of(
                        request2,
                        request3,
                        request1
                ),
                requests
        );
    }

    @Test
    void shouldFindRequestsByFolderId() {
        createCollection("collection-1");

        createFolder(
                "folder-1",
                "collection-1",
                null
        );

        SavedRequest folderRequest =
                createRequest(
                        "request-1",
                        "collection-1",
                        "folder-1",
                        "Folder Request",
                        0
                );

        SavedRequest rootRequest =
                createRequest(
                        "request-2",
                        "collection-1",
                        null,
                        "Root Request",
                        0
                );

        repository.save(folderRequest);
        repository.save(rootRequest);

        List<SavedRequest> requests =
                repository.findByFolderId(
                        "folder-1"
                );

        assertEquals(
                List.of(folderRequest),
                requests
        );
    }

    @Test
    void shouldDeleteRequest() {
        createCollection("collection-1");

        SavedRequest request =
                createRequest(
                        "request-1",
                        "collection-1",
                        null,
                        "Delete Me",
                        0
                );

        repository.save(request);

        repository.deleteById("request-1");

        assertTrue(
                repository.findById(
                        "request-1"
                ).isEmpty()
        );
    }

    @Test
    void shouldRejectRequestWithMissingCollection() {
        SavedRequest request =
                createRequest(
                        "request-1",
                        "missing-collection",
                        null,
                        "Invalid",
                        0
                );

        assertThrows(
                IllegalStateException.class,
                () -> repository.save(request)
        );
    }

    @Test
    void shouldRejectRequestWithMissingFolder() {
        createCollection("collection-1");

        SavedRequest request =
                createRequest(
                        "request-1",
                        "collection-1",
                        "missing-folder",
                        "Invalid",
                        0
                );

        assertThrows(
                IllegalStateException.class,
                () -> repository.save(request)
        );
    }

    private void createCollection(
            String id
    ) {
        String timestamp =
                "2026-10-02T10:00:00Z";

        try (var connection =
                     database.openConnection();
             var statement =
                     connection.prepareStatement(
                             """
                             INSERT INTO collections (
                                 id,
                                 name,
                                 created_at,
                                 updated_at
                             )
                             VALUES (?, ?, ?, ?)
                             """
                     )) {

            statement.setString(1, id);
            statement.setString(2, id);
            statement.setString(3, timestamp);
            statement.setString(4, timestamp);
            statement.executeUpdate();

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to create test collection",
                    exception
            );
        }
    }

    private void createFolder(
            String id,
            String collectionId,
            String parentFolderId
    ) {
        String timestamp =
                "2026-10-02T10:00:00Z";

        try (var connection =
                     database.openConnection();
             var statement =
                     connection.prepareStatement(
                             """
                             INSERT INTO collection_folders (
                                 id,
                                 collection_id,
                                 parent_folder_id,
                                 name,
                                 sort_order,
                                 created_at,
                                 updated_at
                             )
                             VALUES (?, ?, ?, ?, ?, ?, ?)
                             """
                     )) {

            statement.setString(1, id);
            statement.setString(2, collectionId);
            statement.setString(3, parentFolderId);
            statement.setString(4, id);
            statement.setInt(5, 0);
            statement.setString(6, timestamp);
            statement.setString(7, timestamp);
            statement.executeUpdate();

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to create test folder",
                    exception
            );
        }
    }

    private SavedRequest createRequest(
            String id,
            String collectionId,
            String folderId,
            String name,
            int sortOrder
    ) {
        Instant timestamp =
                Instant.parse(
                        "2026-10-02T10:00:00Z"
                );

        return new SavedRequest(
                id,
                collectionId,
                folderId,
                name,
                "GET",
                "https://example.com/" + id,
                Map.of(
                        "Accept",
                        "application/json"
                ),
                Map.of(
                        "page",
                        "1"
                ),
                null,
                sortOrder,
                timestamp,
                timestamp
        );
    }
}