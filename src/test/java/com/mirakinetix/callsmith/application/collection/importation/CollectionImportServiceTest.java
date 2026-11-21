package com.mirakinetix.callsmith.application.collection.importation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import com.mirakinetix.callsmith.infrastructure.collection.importation.InsomniaCollectionImporter;
import com.mirakinetix.callsmith.infrastructure.collection.importation.OpenApiImporter;
import com.mirakinetix.callsmith.infrastructure.collection.importation.PostmanCollectionImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CollectionImportServiceTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsOversizedInputBeforeProbingImporter() throws IOException {
        Path file = directory.resolve("large.json");
        try (var output = new RandomAccessFile(file.toFile(), "rw")) {
            output.setLength(9L * 1024 * 1024);
        }
        var importer = mock(CollectionImporter.class);
        var service = new CollectionImportService(List.of(importer));
        var failure = assertThrows(IllegalArgumentException.class, () -> service.importCollection(file));
        assertTrue(failure.getMessage().contains("8 MiB"));
        verifyNoInteractions(importer);
    }

    @Test
    void shouldRejectEmptyImporterList() {
        assertEquals("At least one collection importer is required",
                assertThrows(IllegalArgumentException.class, () -> new CollectionImportService(List.of())).getMessage());
    }

    @Test
    void shouldRejectNullImporterList() {
        assertEquals("At least one collection importer is required",
                assertThrows(IllegalArgumentException.class, () -> new CollectionImportService(null)).getMessage());
    }

    @Test
    void shouldRejectNullFile() {
        var service = new CollectionImportService(List.of(mock(CollectionImporter.class)));
        assertEquals("Import file is required",
                assertThrows(IllegalArgumentException.class, () -> service.importCollection(null)).getMessage());
    }

    @Test
    void shouldRejectUnsupportedFormat() throws IOException {
        var importer = mock(CollectionImporter.class);
        var file = write("{}");
        assertEquals("Unsupported collection format: collection.json", assertThrows(IllegalArgumentException.class,
                () -> new CollectionImportService(List.of(importer)).importCollection(file)).getMessage());
        verify(importer).supportsDocument(mapper.createObjectNode());
    }

    @Test
    void shouldUseTheFirstSupportingImporter() throws IOException {
        var unsupported = mock(CollectionImporter.class);
        var supported = mock(CollectionImporter.class);
        var unused = mock(CollectionImporter.class);
        var document = mapper.createObjectNode().put("fixture", true);
        var collection = new ImportedCollection("Fixture", List.of(), List.of());
        when(supported.supportsDocument(document)).thenReturn(true);
        when(supported.importDocument(document)).thenReturn(collection);

        var service = new CollectionImportService(List.of(unsupported, supported, unused));
        assertSame(collection, service.importCollection(write(document.toString())));
        verify(unsupported).supportsDocument(document);
        verify(unsupported, never()).importDocument(any(JsonNode.class));
        verifyNoInteractions(unused);
    }

    @Test
    void shouldStopSearchingAfterFirstMatch() throws IOException {
        var first = mock(CollectionImporter.class);
        var second = mock(CollectionImporter.class);
        var result = new ImportedCollection("First", List.of(), List.of());
        when(first.supportsDocument(any())).thenReturn(true);
        when(first.importDocument(any())).thenReturn(result);
        assertSame(result, new CollectionImportService(List.of(first, second)).importCollection(write("{}")));
        verifyNoInteractions(second);
    }

    @Test
    void acceptsLargeValidCollectionAndRejectsTooManyResources() throws IOException {
        var service = service();
        String item = "{\"name\":\"Fixture\",\"request\":{\"method\":\"POST\",\"url\":\"http://127.0.0.1/demo\"}}";
        String description = "a".repeat(4 * 1024 * 1024);
        var large = service.importCollection(write(postman(item, description)));
        assertEquals(1, large.requests().size());
        String oversized = IntStream.rangeClosed(0, CollectionImportDocument.MAX_RESOURCES)
                .mapToObj(index -> item).collect(Collectors.joining(","));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.importCollection(write(postman(oversized, "")))).getMessage().contains("5,000"));
    }

    @Test
    void rejectsMalformedJsonAndExcessiveJsonOrFolderDepth() throws IOException {
        var service = service();
        for (String broken : List.of("{", "{}{}", "{\"resources\":[],\"resources\":[]}")) {
            assertTrue(assertThrows(IllegalArgumentException.class, () -> service.importCollection(write(broken)))
                    .getMessage().contains("Malformed"));
        }
        String nested = "{\"object\":".repeat(220) + "{}" + "}".repeat(220);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.importCollection(write(nested)))
                .getMessage().contains("nesting"));
        String folders = "{\"name\":\"Folder\",\"item\":[".repeat(65) + "] }".repeat(65);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.importCollection(write(postman(folders, "")))).getMessage().contains("64"));
    }

    @Test
    void rejectsRecursiveOpenApiGeneratedExamplesWithoutOverflow() throws IOException {
        var file = write("""
                {"openapi":"3.1.0","info":{"title":"Recursive fixture"},
                 "paths":{"/demo":{"post":{"requestBody":{"content":{"application/json":
                    {"schema":{"$ref":"#/components/schemas/Node"}}
                 }}}}},
                 "components":{"schemas":{"Node":{"type":"object","properties":
                    {"next":{"$ref":"#/components/schemas/Node"}}
                 }}}}
                """);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service().importCollection(file))
                .getMessage().contains("generated examples"));
    }

    @Test
    void rejectsOpenApiExpansionAcrossOperationsBeyondTheTextBudget() throws IOException {
        var document = mapper.createObjectNode().put("openapi", "3.1.0");
        document.putObject("info").put("title", "Expansion fixture");
        document.putObject("components").putObject("parameters").putObject("Demo")
                .put("in", "query").put("name", "fixture").put("example", "x".repeat(1024 * 1024));
        var paths = document.putObject("paths");
        for (int index = 0; index < 17; index++) {
            paths.putObject("/fixture-" + index).putObject("get").putArray("parameters")
                    .addObject().put("$ref", "#/components/parameters/Demo");
        }
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service().importCollection(write(document.toString()))).getMessage().contains("data exceeds"));
    }

    private CollectionImportService service() {
        return new CollectionImportService(List.of(new PostmanCollectionImporter(mapper),
                new OpenApiImporter(mapper), new InsomniaCollectionImporter(mapper)));
    }

    private String postman(String items, String description) {
        return """
                {"info":{"name":"Fixture","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
                         "description":"%s"},"item":[%s]}
                """.formatted(description, items);
    }

    private Path write(String json) throws IOException {
        return Files.writeString(directory.resolve("collection.json"), json);
    }
}
