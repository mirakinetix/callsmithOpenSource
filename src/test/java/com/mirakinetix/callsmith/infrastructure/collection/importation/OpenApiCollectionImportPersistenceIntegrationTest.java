package com.mirakinetix.callsmith.infrastructure.collection.importation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImportService;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.json.JacksonJsonSerializer;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiCollectionImportPersistenceIntegrationTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    @Test
    void shouldImportOpenApiCollectionIntoSqlite(
            @TempDir Path tempDirectory
    ) throws Exception {

        Path openApiFile =
                tempDirectory.resolve(
                        "users-api.json"
                );

        Files.writeString(
                openApiFile,
                """
                {
                  "openapi": "3.0.3",
                  "info": {
                    "title": "Users API",
                    "version": "1.0.0"
                  },
                  "servers": [
                    {
                      "url": "https://example.com/api"
                    }
                  ],
                  "paths": {
                    "/users": {
                      "get": {
                        "summary": "Get Users",
                        "tags": ["Users"],
                        "parameters": [
                          {
                            "name": "limit",
                            "in": "query",
                            "example": "10"
                          }
                        ]
                      },
                      "post": {
                        "summary": "Create User",
                        "tags": ["Users"],
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "example": {
                                "name": "Callsmith"
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """
        );

        Path databasePath =
                tempDirectory.resolve(
                        "callsmith.db"
                );

        SqliteDatabase database =
                new SqliteDatabase(
                        databasePath
                );

        CallsmithDatabase callsmithDatabase =
                new CallsmithDatabase(
                        database,
                        new MigrationRunner()
                );

        callsmithDatabase.initialize();

        OpenApiImporter importer =
                new OpenApiImporter(
                        objectMapper
                );

        CollectionImportService importService =
                new CollectionImportService(
                        List.of(importer)
                );

        ImportedCollection importedCollection =
                importService.importCollection(
                        openApiFile
                );

        SqliteCollectionImportPersistenceService
                persistenceService =
                new SqliteCollectionImportPersistenceService(
                        database,
                        new JacksonJsonSerializer()
                );

        persistenceService.persist(
                importedCollection
        );

        try (Connection connection =
                     database.openConnection()) {

            try (var statement =
                         connection.prepareStatement(
                                 """
                                 SELECT id, name
                                 FROM collections
                                 ORDER BY name
                                 """
                         );
                 var resultSet =
                         statement.executeQuery()) {

                assertTrue(
                        resultSet.next()
                );

                String collectionId =
                        resultSet.getString("id");

                assertEquals(
                        "Users API",
                        resultSet.getString("name")
                );

                try (var folderStatement =
                             connection.prepareStatement(
                                     """
                                     SELECT id, name
                                     FROM collection_folders
                                     WHERE collection_id = ?
                                     ORDER BY sort_order, id
                                     """
                             )) {

                    folderStatement.setString(
                            1,
                            collectionId
                    );

                    try (var folderResult =
                                 folderStatement.executeQuery()) {

                        assertTrue(
                                folderResult.next()
                        );

                        String folderId =
                                folderResult.getString("id");

                        assertEquals(
                                "Users",
                                folderResult.getString("name")
                        );

                        try (var requestStatement =
                                     connection.prepareStatement(
                                             """
                                             SELECT
                                                 name,
                                                 method,
                                                 url,
                                                 headers_json,
                                                 query_parameters_json,
                                                 body,
                                                 sort_order
                                             FROM saved_requests
                                             WHERE folder_id = ?
                                             ORDER BY sort_order, id
                                             """
                                     )) {

                            requestStatement.setString(
                                    1,
                                    folderId
                            );

                            try (var requestResult =
                                         requestStatement
                                                 .executeQuery()) {

                                assertTrue(
                                        requestResult.next()
                                );

                                assertEquals(
                                        "Get Users",
                                        requestResult.getString(
                                                "name"
                                        )
                                );

                                assertEquals(
                                        "GET",
                                        requestResult.getString(
                                                "method"
                                        )
                                );

                                assertEquals(
                                        "https://example.com/api/users",
                                        requestResult.getString(
                                                "url"
                                        )
                                );

                                assertEquals(
                                        "{}",
                                        requestResult.getString(
                                                "headers_json"
                                        )
                                );

                                assertEquals(
                                        "{\"limit\":\"10\"}",
                                        requestResult.getString(
                                                "query_parameters_json"
                                        )
                                );

                                assertEquals(
                                        "",
                                        requestResult.getString(
                                                "body"
                                        )
                                );

                                assertEquals(
                                        0,
                                        requestResult.getInt(
                                                "sort_order"
                                        )
                                );

                                assertTrue(
                                        requestResult.next()
                                );

                                assertEquals(
                                        "Create User",
                                        requestResult.getString(
                                                "name"
                                        )
                                );

                                assertEquals(
                                        "POST",
                                        requestResult.getString(
                                                "method"
                                        )
                                );

                                assertEquals(
                                        "https://example.com/api/users",
                                        requestResult.getString(
                                                "url"
                                        )
                                );

                                assertEquals(
                                        "{}",
                                        requestResult.getString(
                                                "headers_json"
                                        )
                                );

                                assertEquals(
                                        "{}",
                                        requestResult.getString(
                                                "query_parameters_json"
                                        )
                                );

                                assertEquals(
                                        "{\"name\":\"Callsmith\"}",
                                        requestResult.getString(
                                                "body"
                                        )
                                );

                                assertEquals(
                                        1,
                                        requestResult.getInt(
                                                "sort_order"
                                        )
                                );

                                assertTrue(
                                        !requestResult.next()
                                );
                            }
                        }

                        assertTrue(
                                !folderResult.next()
                        );
                    }
                }

                assertTrue(
                        !resultSet.next()
                );
            }
        }
    }
}