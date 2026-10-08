package com.mirakinetix.callsmith.infrastructure.collection.importation;

import com.mirakinetix.callsmith.application.collection.importation.CollectionImportService;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImporter;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.json.JacksonJsonSerializer;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InsomniaCollectionImportPersistenceIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private SqliteDatabase sqliteDatabase;
    private CollectionImportService importService;
    private SqliteCollectionImportPersistenceService persistenceService;

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

        CollectionImporter insomniaImporter =
                new InsomniaCollectionImporter();

        importService =
                new CollectionImportService(
                        List.of(insomniaImporter)
                );

        persistenceService =
                new SqliteCollectionImportPersistenceService(
                        sqliteDatabase,
                        new JacksonJsonSerializer()
                );
    }

    @Test
    void shouldImportInsomniaCollectionAndPersistIt()
            throws Exception {

        Path insomniaFile =
                temporaryDirectory.resolve(
                        "users-api.json"
                );

        Files.writeString(
                insomniaFile,
                """
                {
                  "__export_format": 4,
                  "__export_date": "2026-10-05T00:00:00.000Z",
                  "__export_source": "callsmith-test",
                  "resources": [
                    {
                      "_id": "wrk_users",
                      "_type": "workspace",
                      "name": "Users API"
                    },
                    {
                      "_id": "fld_users",
                      "_type": "request_group",
                      "parentId": "wrk_users",
                      "name": "Users"
                    },
                    {
                      "_id": "req_get_users",
                      "_type": "request",
                      "parentId": "fld_users",
                      "name": "Get Users",
                      "method": "GET",
                      "url": "https://example.com/users",
                      "headers": [
                        {
                          "name": "Accept",
                          "value": "application/json"
                        }
                      ],
                      "parameters": [
                        {
                          "name": "page",
                          "value": "1"
                        }
                      ]
                    },
                    {
                      "_id": "req_create_user",
                      "_type": "request",
                      "parentId": "fld_users",
                      "name": "Create User",
                      "method": "POST",
                      "url": "https://example.com/users",
                      "headers": [
                        {
                          "name": "Content-Type",
                          "value": "application/json"
                        }
                      ],
                      "body": {
                        "mimeType": "application/json",
                        "text": "{\\"name\\":\\"Callsmith\\"}"
                      }
                    }
                  ]
                }
                """
        );

        ImportedCollection importedCollection =
                importService.importCollection(
                        insomniaFile
                );

        persistenceService.persist(
                importedCollection
        );

        assertEquals(
                1,
                countRows("collections")
        );

        assertEquals(
                1,
                countRows("collection_folders")
        );

        assertEquals(
                2,
                countRows("saved_requests")
        );

        assertEquals(
                "Users API",
                queryString(
                        "SELECT name FROM collections"
                )
        );

        assertEquals(
                "Users",
                queryString(
                        "SELECT name FROM collection_folders"
                )
        );

        assertEquals(
                List.of(
                        "Get Users",
                        "Create User"
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
                "GET",
                queryString(
                        """
                        SELECT method
                        FROM saved_requests
                        WHERE name = 'Get Users'
                        """
                )
        );

        assertEquals(
                "POST",
                queryString(
                        """
                        SELECT method
                        FROM saved_requests
                        WHERE name = 'Create User'
                        """
                )
        );

        assertEquals(
                "https://example.com/users",
                queryString(
                        """
                        SELECT url
                        FROM saved_requests
                        WHERE name = 'Get Users'
                        """
                )
        );

        assertEquals(
                "{\"Accept\":\"application/json\"}",
                queryString(
                        """
                        SELECT headers_json
                        FROM saved_requests
                        WHERE name = 'Get Users'
                        """
                )
        );

        assertEquals(
                "{\"page\":\"1\"}",
                queryString(
                        """
                        SELECT query_parameters_json
                        FROM saved_requests
                        WHERE name = 'Get Users'
                        """
                )
        );

        assertEquals(
                "{\"Content-Type\":\"application/json\"}",
                queryString(
                        """
                        SELECT headers_json
                        FROM saved_requests
                        WHERE name = 'Create User'
                        """
                )
        );

        assertEquals(
                "{\"name\":\"Callsmith\"}",
                queryString(
                        """
                        SELECT body
                        FROM saved_requests
                        WHERE name = 'Create User'
                        """
                )
        );

        assertEquals(
                0,
                queryInt(
                        """
                        SELECT sort_order
                        FROM saved_requests
                        WHERE name = 'Get Users'
                        """
                )
        );

        assertEquals(
                1,
                queryInt(
                        """
                        SELECT sort_order
                        FROM saved_requests
                        WHERE name = 'Create User'
                        """
                )
        );
    }

    private int countRows(
            String table
    ) throws Exception {

        return queryInt(
                "SELECT COUNT(*) FROM " + table
        );
    }

    private String queryString(
            String sql
    ) throws Exception {

        try (Connection connection =
                     sqliteDatabase.openConnection();
             var statement =
                     connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery(sql)) {

            if (!resultSet.next()) {
                return null;
            }

            return resultSet.getString(1);
        }
    }

    private int queryInt(
            String sql
    ) throws Exception {

        try (Connection connection =
                     sqliteDatabase.openConnection();
             var statement =
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
    ) throws Exception {

        try (Connection connection =
                     sqliteDatabase.openConnection();
             var statement =
                     connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery(sql)) {

            java.util.ArrayList<String> values =
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