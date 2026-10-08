package com.mirakinetix.callsmith.infrastructure.collection.importation;

import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedFolder;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedRequest;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.json.JacksonJsonSerializer;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteCollectionImportPersistenceServiceTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteDatabase sqliteDatabase;
    private SqliteCollectionImportPersistenceService service;

    @BeforeEach
    void setUp() {
        Path databasePath =
                temporaryDirectory.resolve(
                        "callsmith.db"
                );

        sqliteDatabase =
                new SqliteDatabase(databasePath);

        CallsmithDatabase database =
                new CallsmithDatabase(
                        sqliteDatabase,
                        new MigrationRunner()
                );

        database.initialize();

        service =
                new SqliteCollectionImportPersistenceService(
                        sqliteDatabase,
                        new JacksonJsonSerializer()
                );
    }

    @Test
    void shouldPersistCollection() throws Exception {
        ImportedCollection collection =
                new ImportedCollection(
                        "Imported API",
                        List.of(),
                        List.of()
                );

        service.persist(collection);

        assertEquals(
                1,
                countRows("collections")
        );

        assertEquals(
                "Imported API",
                queryString(
                        "SELECT name FROM collections"
                )
        );
    }

    @Test
    void shouldPersistRootRequests() throws Exception {
        ImportedRequest request =
                new ImportedRequest(
                        "Get Users",
                        "GET",
                        "https://example.com/users",
                        Map.of(
                                "Accept",
                                "application/json"
                        ),
                        Map.of(
                                "page",
                                "1"
                        ),
                        null
                );

        ImportedCollection collection =
                new ImportedCollection(
                        "Users API",
                        List.of(),
                        List.of(request)
                );

        service.persist(collection);

        assertEquals(
                1,
                countRows("collections")
        );

        assertEquals(
                1,
                countRows("saved_requests")
        );

        assertEquals(
                "Get Users",
                queryString(
                        "SELECT name FROM saved_requests"
                )
        );

        assertEquals(
                "GET",
                queryString(
                        "SELECT method FROM saved_requests"
                )
        );

        assertEquals(
                "https://example.com/users",
                queryString(
                        "SELECT url FROM saved_requests"
                )
        );
    }

    @Test
    void shouldPersistFoldersAndFolderRequests()
            throws Exception {

        ImportedRequest request =
                new ImportedRequest(
                        "Create User",
                        "POST",
                        "https://example.com/users",
                        Map.of(
                                "Content-Type",
                                "application/json"
                        ),
                        Map.of(),
                        "{\"name\":\"Callsmith\"}"
                );

        ImportedFolder folder =
                new ImportedFolder(
                        "Users",
                        List.of(),
                        List.of(request)
                );

        ImportedCollection collection =
                new ImportedCollection(
                        "User API",
                        List.of(folder),
                        List.of()
                );

        service.persist(collection);

        assertEquals(
                1,
                countRows("collections")
        );

        assertEquals(
                1,
                countRows("collection_folders")
        );

        assertEquals(
                1,
                countRows("saved_requests")
        );

        assertEquals(
                "Users",
                queryString(
                        "SELECT name FROM collection_folders"
                )
        );

        assertEquals(
                "Create User",
                queryString(
                        "SELECT name FROM saved_requests"
                )
        );

        assertEquals(
                "{\"name\":\"Callsmith\"}",
                queryString(
                        "SELECT body FROM saved_requests"
                )
        );

        assertEquals(
                0,
                queryInt(
                        "SELECT sort_order FROM saved_requests"
                )
        );
    }

    @Test
    void shouldPersistNestedFolders() throws Exception {
        ImportedRequest request =
                new ImportedRequest(
                        "Nested Request",
                        "GET",
                        "https://example.com/nested",
                        Map.of(),
                        Map.of(),
                        null
                );

        ImportedFolder childFolder =
                new ImportedFolder(
                        "Child",
                        List.of(),
                        List.of(request)
                );

        ImportedFolder parentFolder =
                new ImportedFolder(
                        "Parent",
                        List.of(childFolder),
                        List.of()
                );

        ImportedCollection collection =
                new ImportedCollection(
                        "Nested API",
                        List.of(parentFolder),
                        List.of()
                );

        service.persist(collection);

        assertEquals(
                2,
                countRows("collection_folders")
        );

        assertEquals(
                1,
                countRows("saved_requests")
        );

        String parentId =
                queryString(
                        """
                        SELECT id
                        FROM collection_folders
                        WHERE name = 'Parent'
                        """
                );

        assertEquals(
                parentId,
                queryString(
                        """
                        SELECT parent_folder_id
                        FROM collection_folders
                        WHERE name = 'Child'
                        """
                )
        );

        assertEquals(
                queryString(
                        """
                        SELECT id
                        FROM collection_folders
                        WHERE name = 'Child'
                        """
                ),
                queryString(
                        """
                        SELECT folder_id
                        FROM saved_requests
                        """
                )
        );
    }

    @Test
    void shouldPreserveRequestHeadersAndQueryParameters()
            throws Exception {

        Map<String, String> headers =
                new LinkedHashMap<>();

        headers.put(
                "Accept",
                "application/json"
        );

        headers.put(
                "X-Callsmith-Test",
                "true"
        );

        Map<String, String> queryParameters =
                new LinkedHashMap<>();

        queryParameters.put(
                "page",
                "2"
        );

        queryParameters.put(
                "limit",
                "25"
        );

        ImportedRequest request =
                new ImportedRequest(
                        "List Users",
                        "GET",
                        "https://example.com/users",
                        headers,
                        queryParameters,
                        null
                );

        ImportedCollection collection =
                new ImportedCollection(
                        "Users",
                        List.of(),
                        List.of(request)
                );

        service.persist(collection);

        String headersJson =
                queryString(
                        "SELECT headers_json FROM saved_requests"
                );

        String queryParametersJson =
                queryString(
                        "SELECT query_parameters_json FROM saved_requests"
                );

        assertEquals(
                "{\"Accept\":\"application/json\",\"X-Callsmith-Test\":\"true\"}",
                headersJson
        );

        assertEquals(
                "{\"page\":\"2\",\"limit\":\"25\"}",
                queryParametersJson
        );
    }

    @Test
    void shouldPreserveRequestAndFolderOrder()
            throws Exception {

        ImportedRequest rootRequestOne =
                new ImportedRequest(
                        "Root One",
                        "GET",
                        "https://example.com/one",
                        Map.of(),
                        Map.of(),
                        null
                );

        ImportedRequest rootRequestTwo =
                new ImportedRequest(
                        "Root Two",
                        "GET",
                        "https://example.com/two",
                        Map.of(),
                        Map.of(),
                        null
                );

        ImportedFolder firstFolder =
                new ImportedFolder(
                        "First",
                        List.of(),
                        List.of()
                );

        ImportedFolder secondFolder =
                new ImportedFolder(
                        "Second",
                        List.of(),
                        List.of()
                );

        ImportedCollection collection =
                new ImportedCollection(
                        "Ordered API",
                        List.of(
                                firstFolder,
                                secondFolder
                        ),
                        List.of(
                                rootRequestOne,
                                rootRequestTwo
                        )
                );

        service.persist(collection);

        assertEquals(
                List.of(
                        "Root One",
                        "Root Two"
                ),
                queryStrings(
                        """
                        SELECT name
                        FROM saved_requests
                        ORDER BY sort_order
                        """
                )
        );

        assertEquals(
                List.of(
                        "First",
                        "Second"
                ),
                queryStrings(
                        """
                        SELECT name
                        FROM collection_folders
                        ORDER BY sort_order
                        """
                )
        );
    }

    @Test
    void shouldPersistEmptyCollection() throws Exception {
        ImportedCollection collection =
                new ImportedCollection(
                        "Empty Collection",
                        List.of(),
                        List.of()
                );

        service.persist(collection);

        assertEquals(
                1,
                countRows("collections")
        );

        assertEquals(
                0,
                countRows("collection_folders")
        );

        assertEquals(
                0,
                countRows("saved_requests")
        );
    }

    @Test
    void shouldRejectNullCollection() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.persist(null)
        );
    }

    private int countRows(
            String tableName
    ) throws SQLException {

        return queryInt(
                "SELECT COUNT(*) FROM "
                        + tableName
        );
    }

    private String queryString(
            String sql
    ) throws SQLException {

        try (Connection connection =
                     sqliteDatabase.openConnection();
             Statement statement =
                     connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery(sql)) {

            if (!resultSet.next()) {
                throw new IllegalStateException(
                        "Query returned no rows"
                );
            }

            return resultSet.getString(1);
        }
    }

    private int queryInt(
            String sql
    ) throws SQLException {

        try (Connection connection =
                     sqliteDatabase.openConnection();
             Statement statement =
                     connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery(sql)) {

            if (!resultSet.next()) {
                throw new IllegalStateException(
                        "Query returned no rows"
                );
            }

            return resultSet.getInt(1);
        }
    }

    private List<String> queryStrings(
            String sql
    ) throws SQLException {

        try (Connection connection =
                     sqliteDatabase.openConnection();
             Statement statement =
                     connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery(sql)) {

            List<String> values =
                    new java.util.ArrayList<>();

            while (resultSet.next()) {
                values.add(
                        resultSet.getString(1)
                );
            }

            return values;
        }
    }
}