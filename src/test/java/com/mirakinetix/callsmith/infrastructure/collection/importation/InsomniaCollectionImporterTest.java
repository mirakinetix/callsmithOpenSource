package com.mirakinetix.callsmith.infrastructure.collection.importation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedFolder;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsomniaCollectionImporterTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldSupportInsomniaV4Json() throws IOException {
        Path file =
                writeFile(
                        "insomnia.json",
                        """
                        {
                          "__export_format": 4,
                          "resources": [
                            {
                              "_id": "wrk_1",
                              "_type": "workspace",
                              "name": "Callsmith API"
                            }
                          ]
                        }
                        """
                );

        InsomniaCollectionImporter importer =
                new InsomniaCollectionImporter();

        assertTrue(
                importer.supports(file)
        );
    }

    @Test
    void shouldRejectNonJsonFile() throws IOException {
        Path file =
                writeFile(
                        "insomnia.txt",
                        "not json"
                );

        InsomniaCollectionImporter importer =
                new InsomniaCollectionImporter();

        assertFalse(
                importer.supports(file)
        );
    }

    @Test
    void shouldRejectNonInsomniaJson() throws IOException {
        Path file =
                writeFile(
                        "postman.json",
                        """
                        {
                          "info": {
                            "name": "Postman Collection",
                            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
                          },
                          "item": []
                        }
                        """
                );

        InsomniaCollectionImporter importer =
                new InsomniaCollectionImporter();

        assertFalse(
                importer.supports(file)
        );
    }

    @Test
    void shouldImportWorkspaceName() throws IOException {
        Path file =
                writeFile(
                        "insomnia.json",
                        """
                        {
                          "__export_format": 4,
                          "resources": [
                            {
                              "_id": "wrk_1",
                              "_type": "workspace",
                              "name": "Callsmith API"
                            }
                          ]
                        }
                        """
                );

        ImportedCollection collection =
                new InsomniaCollectionImporter()
                        .importCollection(file);

        assertEquals(
                "Callsmith API",
                collection.name()
        );
    }

    @Test
    void shouldImportRootRequest() throws IOException {
        Path file =
                writeFile(
                        "insomnia.json",
                        """
                        {
                          "__export_format": 4,
                          "resources": [
                            {
                              "_id": "wrk_1",
                              "_type": "workspace",
                              "name": "Callsmith API"
                            },
                            {
                              "_id": "req_1",
                              "_type": "request",
                              "parentId": "wrk_1",
                              "name": "Get Users",
                              "method": "GET",
                              "url": "https://example.com/users"
                            }
                          ]
                        }
                        """
                );

        ImportedCollection collection =
                new InsomniaCollectionImporter()
                        .importCollection(file);

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
    }

    @Test
    void shouldImportHeaders() throws IOException {
        Path file =
                writeFile(
                        "insomnia.json",
                        """
                        {
                          "__export_format": 4,
                          "resources": [
                            {
                              "_id": "wrk_1",
                              "_type": "workspace",
                              "name": "Callsmith API"
                            },
                            {
                              "_id": "req_1",
                              "_type": "request",
                              "parentId": "wrk_1",
                              "name": "Get Users",
                              "method": "GET",
                              "url": "https://example.com/users",
                              "headers": [
                                {
                                  "name": "Accept",
                                  "value": "application/json"
                                },
                                {
                                  "name": "X-Disabled",
                                  "value": "ignored",
                                  "disabled": true
                                }
                              ]
                            }
                          ]
                        }
                        """
                );

        ImportedRequest request =
                new InsomniaCollectionImporter()
                        .importCollection(file)
                        .requests()
                        .get(0);

        assertEquals(
                1,
                request.headers().size()
        );

        assertEquals(
                "application/json",
                request.headers().get("Accept")
        );

        assertFalse(
                request.headers().containsKey("X-Disabled")
        );
    }

    @Test
    void shouldImportQueryParameters() throws IOException {
        Path file =
                writeFile(
                        "insomnia.json",
                        """
                        {
                          "__export_format": 4,
                          "resources": [
                            {
                              "_id": "wrk_1",
                              "_type": "workspace",
                              "name": "Callsmith API"
                            },
                            {
                              "_id": "req_1",
                              "_type": "request",
                              "parentId": "wrk_1",
                              "name": "Get Users",
                              "method": "GET",
                              "url": "https://example.com/users",
                              "parameters": [
                                {
                                  "name": "limit",
                                  "value": "10"
                                },
                                {
                                  "name": "disabled",
                                  "value": "ignored",
                                  "disabled": true
                                }
                              ]
                            }
                          ]
                        }
                        """
                );

        ImportedRequest request =
                new InsomniaCollectionImporter()
                        .importCollection(file)
                        .requests()
                        .get(0);

        assertEquals(
                1,
                request.queryParameters().size()
        );

        assertEquals(
                "10",
                request.queryParameters().get("limit")
        );

        assertFalse(
                request.queryParameters()
                        .containsKey("disabled")
        );
    }

    @Test
    void shouldImportRawBody() throws IOException {
        Path file =
                writeFile(
                        "insomnia.json",
                        """
                        {
                          "__export_format": 4,
                          "resources": [
                            {
                              "_id": "wrk_1",
                              "_type": "workspace",
                              "name": "Callsmith API"
                            },
                            {
                              "_id": "req_1",
                              "_type": "request",
                              "parentId": "wrk_1",
                              "name": "Create User",
                              "method": "POST",
                              "url": "https://example.com/users",
                              "body": {
                                "mimeType": "application/json",
                                "text": "{\\"name\\":\\"Callsmith\\"}"
                              }
                            }
                          ]
                        }
                        """
                );

        ImportedRequest request =
                new InsomniaCollectionImporter()
                        .importCollection(file)
                        .requests()
                        .get(0);

        assertEquals(
                "{" +
                        "\"name\":\"Callsmith\"" +
                        "}",
                request.body()
        );
    }

    @Test
    void shouldImportNestedFolders() throws IOException {
        Path file =
                writeFile(
                        "insomnia.json",
                        """
                        {
                          "__export_format": 4,
                          "resources": [
                            {
                              "_id": "wrk_1",
                              "_type": "workspace",
                              "name": "Callsmith API"
                            },
                            {
                              "_id": "fld_1",
                              "_type": "request_group",
                              "parentId": "wrk_1",
                              "name": "Users"
                            },
                            {
                              "_id": "fld_2",
                              "_type": "request_group",
                              "parentId": "fld_1",
                              "name": "Admin"
                            },
                            {
                              "_id": "req_1",
                              "_type": "request",
                              "parentId": "fld_2",
                              "name": "Get Admin",
                              "method": "GET",
                              "url": "https://example.com/admin"
                            }
                          ]
                        }
                        """
                );

        ImportedCollection collection =
                new InsomniaCollectionImporter()
                        .importCollection(file);

        assertEquals(
                1,
                collection.folders().size()
        );

        ImportedFolder users =
                collection.folders().get(0);

        assertEquals(
                "Users",
                users.name()
        );

        assertEquals(
                1,
                users.folders().size()
        );

        ImportedFolder admin =
                users.folders().get(0);

        assertEquals(
                "Admin",
                admin.name()
        );

        assertEquals(
                1,
                admin.requests().size()
        );

        assertEquals(
                "Get Admin",
                admin.requests().get(0).name()
        );
    }

    @Test
    void shouldImportRootAndFolderRequests()
            throws IOException {

        Path file =
                writeFile(
                        "insomnia.json",
                        """
                        {
                          "__export_format": 4,
                          "resources": [
                            {
                              "_id": "wrk_1",
                              "_type": "workspace",
                              "name": "Callsmith API"
                            },
                            {
                              "_id": "fld_1",
                              "_type": "request_group",
                              "parentId": "wrk_1",
                              "name": "Users"
                            },
                            {
                              "_id": "req_1",
                              "_type": "request",
                              "parentId": "wrk_1",
                              "name": "Health",
                              "method": "GET",
                              "url": "https://example.com/health"
                            },
                            {
                              "_id": "req_2",
                              "_type": "request",
                              "parentId": "fld_1",
                              "name": "Get Users",
                              "method": "GET",
                              "url": "https://example.com/users"
                            }
                          ]
                        }
                        """
                );

        ImportedCollection collection =
                new InsomniaCollectionImporter()
                        .importCollection(file);

        assertEquals(
                1,
                collection.requests().size()
        );

        assertEquals(
                "Health",
                collection.requests().get(0).name()
        );

        assertEquals(
                1,
                collection.folders().size()
        );

        assertEquals(
                1,
                collection.folders()
                        .get(0)
                        .requests()
                        .size()
        );

        assertEquals(
                "Get Users",
                collection.folders()
                        .get(0)
                        .requests()
                        .get(0)
                        .name()
        );
    }

    @Test
    void shouldDefaultMissingMethodToGet()
            throws IOException {

        Path file =
                writeFile(
                        "insomnia.json",
                        """
                        {
                          "__export_format": 4,
                          "resources": [
                            {
                              "_id": "wrk_1",
                              "_type": "workspace",
                              "name": "Callsmith API"
                            },
                            {
                              "_id": "req_1",
                              "_type": "request",
                              "parentId": "wrk_1",
                              "name": "Health",
                              "url": "https://example.com/health"
                            }
                          ]
                        }
                        """
                );

        ImportedRequest request =
                new InsomniaCollectionImporter()
                        .importCollection(file)
                        .requests()
                        .get(0);

        assertEquals(
                "GET",
                request.method()
        );
    }

    @Test
    void shouldHandleMalformedJson()
            throws IOException {

        Path file =
                writeFile(
                        "insomnia.json",
                        "{ invalid json"
                );

        InsomniaCollectionImporter importer =
                new InsomniaCollectionImporter();

        assertFalse(
                importer.supports(file)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> importer.importCollection(file)
        );
    }

    @Test
    void shouldRejectUnsupportedDocument()
            throws IOException {

        Path file =
                writeFile(
                        "insomnia.json",
                        """
                        {
                          "__export_format": 3,
                          "resources": []
                        }
                        """
                );

        InsomniaCollectionImporter importer =
                new InsomniaCollectionImporter();

        assertFalse(
                importer.supports(file)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> importer.importCollection(file)
        );
    }

    @Test
    void shouldRejectNullObjectMapper() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new InsomniaCollectionImporter(
                        null
                )
        );
    }

    @Test
    void shouldCreateImporterWithDefaultObjectMapper() {
        InsomniaCollectionImporter importer =
                new InsomniaCollectionImporter();

        assertNotNull(importer);
    }

    @Test
    void shouldPreserveRequestOrder()
            throws IOException {

        Path file =
                writeFile(
                        "insomnia.json",
                        """
                        {
                          "__export_format": 4,
                          "resources": [
                            {
                              "_id": "wrk_1",
                              "_type": "workspace",
                              "name": "Callsmith API"
                            },
                            {
                              "_id": "req_1",
                              "_type": "request",
                              "parentId": "wrk_1",
                              "name": "First",
                              "method": "GET",
                              "url": "https://example.com/first"
                            },
                            {
                              "_id": "req_2",
                              "_type": "request",
                              "parentId": "wrk_1",
                              "name": "Second",
                              "method": "POST",
                              "url": "https://example.com/second"
                            }
                          ]
                        }
                        """
                );

        List<ImportedRequest> requests =
                new InsomniaCollectionImporter()
                        .importCollection(file)
                        .requests();

        assertEquals(
                2,
                requests.size()
        );

        assertEquals(
                "First",
                requests.get(0).name()
        );

        assertEquals(
                "Second",
                requests.get(1).name()
        );
    }

    private Path writeFile(
            String fileName,
            String content
    ) throws IOException {

        Path file =
                tempDirectory.resolve(fileName);

        Files.writeString(
                file,
                content
        );

        return file;
    }
}