package com.mirakinetix.callsmith.infrastructure.collection.importation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedFolder;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostmanCollectionImporterTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final PostmanCollectionImporter importer =
            new PostmanCollectionImporter(
                    objectMapper
            );

    @Test
    void shouldSupportPostmanV2Collection()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "info": {
                            "name": "Test Collection",
                            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                          },
                          "item": []
                        }
                        """
                );

        assertTrue(
                importer.supports(file)
        );
    }

    @Test
    void shouldRejectNonPostmanJson()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "name": "Not Postman"
                        }
                        """
                );

        assertFalse(
                importer.supports(file)
        );
    }

    @Test
    void shouldRejectNonJsonFile()
            throws Exception {

        Path file =
                Files.createTempFile(
                        "callsmith-postman-test-",
                        ".txt"
                );

        Files.writeString(
                file,
                "{}"
        );

        assertFalse(
                importer.supports(file)
        );
    }

    @Test
    void shouldImportCollectionName()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "info": {
                            "name": "My API",
                            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                          },
                          "item": []
                        }
                        """
                );

        ImportedCollection collection =
                importer.importCollection(file);

        assertEquals(
                "My API",
                collection.name()
        );

        assertTrue(
                collection.folders().isEmpty()
        );

        assertTrue(
                collection.requests().isEmpty()
        );
    }

    @Test
    void shouldImportRequest()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "info": {
                            "name": "API Collection",
                            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                          },
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
                                  "raw": "https://example.com/users"
                                }
                              }
                            }
                          ]
                        }
                        """
                );

        ImportedCollection collection =
                importer.importCollection(file);

        assertEquals(
                1,
                collection.requests().size()
        );

        ImportedRequest request =
                collection.requests().get(0);

        assertEquals(
                "Get Users",
                request.name()
        );

        assertEquals(
                "GET",
                request.method()
        );

        assertEquals(
                "https://example.com/users",
                request.url()
        );

        assertEquals(
                Map.of(
                        "Accept",
                        "application/json"
                ),
                request.headers()
        );

        assertTrue(
                request.queryParameters().isEmpty()
        );

        assertEquals(
                "",
                request.body()
        );
    }

    @Test
    void shouldImportQueryParameters()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "info": {
                            "name": "Query Collection",
                            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                          },
                          "item": [
                            {
                              "name": "Search",
                              "request": {
                                "method": "GET",
                                "url": {
                                  "raw": "https://example.com/search?name=Callsmith",
                                  "query": [
                                    {
                                      "key": "name",
                                      "value": "Callsmith"
                                    },
                                    {
                                      "key": "disabled",
                                      "value": "ignored",
                                      "disabled": true
                                    }
                                  ]
                                }
                              }
                            }
                          ]
                        }
                        """
                );

        ImportedCollection collection =
                importer.importCollection(file);

        ImportedRequest request =
                collection.requests().get(0);

        assertEquals(
                Map.of(
                        "name",
                        "Callsmith"
                ),
                request.queryParameters()
        );
    }

    @Test
    void shouldImportRawBody()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "info": {
                            "name": "Body Collection",
                            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                          },
                          "item": [
                            {
                              "name": "Create User",
                              "request": {
                                "method": "POST",
                                "body": {
                                  "mode": "raw",
                                  "raw": "{\\"name\\":\\"Callsmith\\"}"
                                },
                                "url": "https://example.com/users"
                              }
                            }
                          ]
                        }
                        """
                );

        ImportedCollection collection =
                importer.importCollection(file);

        ImportedRequest request =
                collection.requests().get(0);

        assertEquals(
                "POST",
                request.method()
        );

        assertEquals(
                "https://example.com/users",
                request.url()
        );

        assertEquals(
                "{\"name\":\"Callsmith\"}",
                request.body()
        );
    }

    @Test
    void shouldIgnoreDisabledHeaders()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "info": {
                            "name": "Header Collection",
                            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                          },
                          "item": [
                            {
                              "name": "Test",
                              "request": {
                                "method": "GET",
                                "header": [
                                  {
                                    "key": "Enabled",
                                    "value": "yes"
                                  },
                                  {
                                    "key": "Disabled",
                                    "value": "no",
                                    "disabled": true
                                  }
                                ],
                                "url": "https://example.com"
                              }
                            }
                          ]
                        }
                        """
                );

        ImportedCollection collection =
                importer.importCollection(file);

        ImportedRequest request =
                collection.requests().get(0);

        assertEquals(
                Map.of(
                        "Enabled",
                        "yes"
                ),
                request.headers()
        );
    }

    @Test
    void shouldImportNestedFolders()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "info": {
                            "name": "Nested Collection",
                            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                          },
                          "item": [
                            {
                              "name": "Users",
                              "item": [
                                {
                                  "name": "Admin",
                                  "item": [
                                    {
                                      "name": "Get Admin",
                                      "request": {
                                        "method": "GET",
                                        "url": "https://example.com/admin"
                                      }
                                    }
                                  ]
                                }
                              ]
                            }
                          ]
                        }
                        """
                );

        ImportedCollection collection =
                importer.importCollection(file);

        assertEquals(
                1,
                collection.folders().size()
        );

        ImportedFolder usersFolder =
                collection.folders().get(0);

        assertEquals(
                "Users",
                usersFolder.name()
        );

        assertEquals(
                1,
                usersFolder.folders().size()
        );

        ImportedFolder adminFolder =
                usersFolder.folders().get(0);

        assertEquals(
                "Admin",
                adminFolder.name()
        );

        assertEquals(
                1,
                adminFolder.requests().size()
        );

        assertEquals(
                "Get Admin",
                adminFolder.requests().get(0).name()
        );
    }

    @Test
    void shouldImportRootAndFolderRequests()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "info": {
                            "name": "Mixed Collection",
                            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                          },
                          "item": [
                            {
                              "name": "Root Request",
                              "request": {
                                "method": "GET",
                                "url": "https://example.com/root"
                              }
                            },
                            {
                              "name": "Users",
                              "item": [
                                {
                                  "name": "Folder Request",
                                  "request": {
                                    "method": "GET",
                                    "url": "https://example.com/users"
                                  }
                                }
                              ]
                            }
                          ]
                        }
                        """
                );

        ImportedCollection collection =
                importer.importCollection(file);

        assertEquals(
                1,
                collection.requests().size()
        );

        assertEquals(
                "Root Request",
                collection.requests().get(0).name()
        );

        assertEquals(
                1,
                collection.folders().size()
        );

        assertEquals(
                1,
                collection.folders().get(0)
                        .requests()
                        .size()
        );
    }

    @Test
    void shouldRejectUnsupportedSchema()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "info": {
                            "name": "Unsupported",
                            "schema": "https://example.com/unsupported.json"
                          },
                          "item": []
                        }
                        """
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> importer.importCollection(file)
                );

        assertEquals(
                "Unsupported Postman collection schema",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMalformedJson()
            throws Exception {

        Path file =
                Files.createTempFile(
                        "callsmith-postman-test-",
                        ".json"
                );

        Files.writeString(
                file,
                "{ invalid json"
        );

        assertFalse(
                importer.supports(file)
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> importer.importCollection(file)
                );

        assertTrue(
                exception.getMessage()
                        .startsWith(
                                "Unable to read Postman collection:"
                        )
        );
    }

    @Test
    void shouldRejectNullObjectMapper() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new PostmanCollectionImporter(
                                null
                        )
                );

        assertEquals(
                "ObjectMapper is required",
                exception.getMessage()
        );
    }

    private Path createTempFile(
            String content
    ) throws Exception {

        Path file =
                Files.createTempFile(
                        "callsmith-postman-test-",
                        ".json"
                );

        Files.writeString(
                file,
                content
        );

        return file;
    }
}