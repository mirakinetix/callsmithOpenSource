package com.mirakinetix.callsmith.infrastructure.collection.importation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedFolder;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiImporterTest {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final OpenApiImporter importer =
            new OpenApiImporter(
                    objectMapper
            );

    @Test
    void shouldSupportOpenApi31Document()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "openapi": "3.1.0",
                          "info": {
                            "title": "Users API",
                            "version": "1.0.0"
                          },
                          "paths": {}
                        }
                        """
                );

        assertTrue(
                importer.supports(file)
        );
    }

    @Test
    void shouldSupportOpenApi30Document()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "openapi": "3.0.3",
                          "info": {
                            "title": "Users API",
                            "version": "1.0.0"
                          },
                          "paths": {}
                        }
                        """
                );

        assertTrue(
                importer.supports(file)
        );
    }

    @Test
    void shouldRejectNonOpenApiJson()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "info": {
                            "name": "Not OpenAPI"
                          }
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
                        "callsmith-openapi-test-",
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
                          "openapi": "3.0.3",
                          "info": {
                            "title": "Users API",
                            "version": "1.0.0"
                          },
                          "paths": {}
                        }
                        """
                );

        ImportedCollection collection =
                importer.importCollection(file);

        assertEquals(
                "Users API",
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
    void shouldImportServerAndPath()
            throws Exception {

        Path file =
                createTempFile(
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
                                "summary": "Get Users"
                              }
                            }
                          }
                        }
                        """
                );

        ImportedCollection collection =
                importer.importCollection(file);

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
                "https://example.com/api/users",
                request.url()
        );
    }

    @Test
    void shouldImportQueryAndHeaderParameters()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "openapi": "3.0.3",
                          "info": {
                            "title": "Search API",
                            "version": "1.0.0"
                          },
                          "paths": {
                            "/users": {
                              "get": {
                                "parameters": [
                                  {
                                    "name": "name",
                                    "in": "query",
                                    "example": "Callsmith"
                                  },
                                  {
                                    "name": "X-Request-ID",
                                    "in": "header",
                                    "example": "12345"
                                  }
                                ]
                              }
                            }
                          }
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

        assertEquals(
                Map.of(
                        "X-Request-ID",
                        "12345"
                ),
                request.headers()
        );
    }

    @Test
    void shouldImportPathParameterAsEnvironmentVariable()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "openapi": "3.0.3",
                          "info": {
                            "title": "Users API",
                            "version": "1.0.0"
                          },
                          "paths": {
                            "/users/{id}": {
                              "get": {
                                "summary": "Get User"
                              }
                            }
                          }
                        }
                        """
                );

        ImportedCollection collection =
                importer.importCollection(file);

        ImportedRequest request =
                collection.requests().get(0);

        assertEquals(
                "/users/{id}",
                request.url()
        );
    }

    @Test
    void shouldGroupRequestsByTags()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "openapi": "3.0.3",
                          "info": {
                            "title": "Users API",
                            "version": "1.0.0"
                          },
                          "paths": {
                            "/users": {
                              "get": {
                                "summary": "Get Users",
                                "tags": ["Users"]
                              },
                              "post": {
                                "summary": "Create User",
                                "tags": ["Users"]
                              }
                            }
                          }
                        }
                        """
                );

        ImportedCollection collection =
                importer.importCollection(file);

        assertEquals(
                1,
                collection.folders().size()
        );

        ImportedFolder folder =
                collection.folders().get(0);

        assertEquals(
                "Users",
                folder.name()
        );

        assertEquals(
                2,
                folder.requests().size()
        );

        assertEquals(
                "Get Users",
                folder.requests().get(0).name()
        );

        assertEquals(
                "Create User",
                folder.requests().get(1).name()
        );
    }

    @Test
    void shouldImportJsonRequestBodyExample()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "openapi": "3.0.3",
                          "info": {
                            "title": "Users API",
                            "version": "1.0.0"
                          },
                          "paths": {
                            "/users": {
                              "post": {
                                "summary": "Create User",
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

        ImportedCollection collection =
                importer.importCollection(file);

        ImportedRequest request =
                collection.requests().get(0);

        assertEquals(
                "{\"name\":\"Callsmith\"}",
                request.body()
        );
    }

    @Test
    void shouldGenerateJsonBodyFromSchemaExample()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "openapi": "3.0.3",
                          "info": {
                            "title": "Users API",
                            "version": "1.0.0"
                          },
                          "paths": {
                            "/users": {
                              "post": {
                                "requestBody": {
                                  "content": {
                                    "application/json": {
                                      "schema": {
                                        "type": "object",
                                        "properties": {
                                          "name": {
                                            "type": "string",
                                            "example": "Callsmith"
                                          },
                                          "active": {
                                            "type": "boolean",
                                            "default": true
                                          }
                                        }
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

        ImportedCollection collection =
                importer.importCollection(file);

        ImportedRequest request =
                collection.requests().get(0);

        assertEquals(
                "{\"name\":\"Callsmith\",\"active\":true}",
                request.body()
        );
    }

    @Test
    void shouldUseOperationIdWhenSummaryMissing()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "openapi": "3.0.3",
                          "info": {
                            "title": "Users API",
                            "version": "1.0.0"
                          },
                          "paths": {
                            "/users": {
                              "get": {
                                "operationId": "listUsers"
                              }
                            }
                          }
                        }
                        """
                );

        ImportedCollection collection =
                importer.importCollection(file);

        assertEquals(
                "listUsers",
                collection.requests()
                        .get(0)
                        .name()
        );
    }

    @Test
    void shouldRejectUnsupportedDocument()
            throws Exception {

        Path file =
                createTempFile(
                        """
                        {
                          "swagger": "2.0",
                          "info": {
                            "title": "Legacy API"
                          },
                          "paths": {}
                        }
                        """
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                importer.importCollection(file)
                );

        assertEquals(
                "Unsupported OpenAPI document",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectMalformedJson()
            throws Exception {

        Path file =
                Files.createTempFile(
                        "callsmith-openapi-test-",
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
                        () ->
                                importer.importCollection(file)
                );

        assertTrue(
                exception.getMessage()
                        .startsWith(
                                "Unable to read OpenAPI document:"
                        )
        );
    }

    @Test
    void shouldRejectNullObjectMapper() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new OpenApiImporter(
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
                        "callsmith-openapi-test-",
                        ".json"
                );

        Files.writeString(
                file,
                content
        );

        return file;
    }
}