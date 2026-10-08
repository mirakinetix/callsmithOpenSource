package com.mirakinetix.callsmith.infrastructure.collection.importation;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostmanCollectionImportPersistenceIntegrationTest {

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

        CollectionImporter postmanImporter =
                new PostmanCollectionImporter(
                        new ObjectMapper()
                );

        importService =
                new CollectionImportService(
                        List.of(postmanImporter)
                );

        persistenceService =
                new SqliteCollectionImportPersistenceService(
                        sqliteDatabase,
                        new JacksonJsonSerializer()
                );
    }

    @Test
    void shouldImportPostmanCollectionAndPersistIt()
            throws Exception {

        Path postmanFile =
                temporaryDirectory.resolve(
                        "users-api.json"
                );

        Files.writeString(
                postmanFile,
                """
                {
                  "info": {
                    "_postman_id": "callsmith-test",
                    "name": "Users API",
                    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                  },
                  "item": [
                    {
                      "name": "Users",
                      "item": [
                        {
                          "name": "Get Users",
                          "request": {
                            "method": "GET",
                            "header": [
                              {
                                "key": "Accept",
                                "value": "application/json"
                              }
                            ],
                            "url": {
                              "raw": "https://example.com/users?page=1",
                              "query": [
                                {
                                  "key": "page",
                                  "value": "1"
                                }
                              ]
                            }
                          }
                        },
                        {
                          "name": "Create User",
                          "request": {
                            "method": "POST",
                            "header": [
                              {
                                "key": "Content-Type",
                                "value": "application/json"
                              }
                            ],
                            "body": {
                              "mode": "raw",
                              "raw": "{\\\"name\\\":\\\"Callsmith\\\"}"
                            },
                            "url": {
                              "raw": "https://example.com/users"
                            }
                          }
                        }
                      ]
                    }
                  ]
                }
                """
        );

        ImportedCollection importedCollection =
                importService.importCollection(
                        postmanFile
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
                "{\"page\":\"1\"}",
                queryString(
                        """
                        SELECT query_parameters_json
                        FROM saved_requests
                        WHERE name = 'Get Users'
                        """
                )
        );
    }

    private int countRows(
            String tableName
    ) throws Exception {

        return queryInt(
                "SELECT COUNT(*) FROM "
                        + tableName
        );
    }

    private String queryString(
            String sql
    ) throws Exception {

        try (var connection =
                     sqliteDatabase.openConnection();
             var statement =
                     connection.createStatement();
             var resultSet =
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
    ) throws Exception {

        try (var connection =
                     sqliteDatabase.openConnection();
             var statement =
                     connection.createStatement();
             var resultSet =
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

        try (var connection =
                     sqliteDatabase.openConnection();
             var statement =
                     connection.createStatement();
             var resultSet =
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