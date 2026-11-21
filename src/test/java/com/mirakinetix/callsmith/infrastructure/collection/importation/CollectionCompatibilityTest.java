package com.mirakinetix.callsmith.infrastructure.collection.importation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirakinetix.callsmith.application.collection.CollectionExportService;
import com.mirakinetix.callsmith.application.collection.CollectionWorkspaceService;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImportService;
import com.mirakinetix.callsmith.infrastructure.collection.PostmanCollectionExporter;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.json.JacksonJsonSerializer;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CollectionCompatibilityTest {
    @TempDir Path temporary;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void postmanWarnsAndRoundTripsMixedSiblingOrderAndVariableTemplates() throws Exception {
        var postman = new PostmanCollectionImporter(mapper);
        var input = Files.writeString(temporary.resolve("input.json"), """
                {
                  "info":{"name":"Fixture","schema":"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
                  "auth":{"type":"bearer","bearer":[{"key":"token","value":"omitted-fixture-token"}]},
                  "event":[{"listen":"test","script":{"exec":["throw new Error('fixture script')"]}}],
                  "item":[
                    {"name":"First","request":{"method":"POST","url":{"raw":"{{base}}/api?page=2&off=3#local","query":[
                        {"key":"page","value":"2"},{"key":"off","value":"3","disabled":true}]},
                        "header":[{"key":"Authorization","value":"Bearer {{token}}"}],
                        "body":{"mode":"raw","raw":"{\\\"source\\\":\\\"{{demo}}\\\"}"}}},
                    {"name":"Folder","item":[
                        {"name":"Inner","item":[{"name":"Child","request":{"method":"PATCH","url":"{{base}}/patch"}}]},
                        {"name":"After child","request":{"method":"GET","url":"{{base}}/next"}}
                    ]},
                    {"name":"Last","request":{"method":"DELETE","url":"{{base}}/last","body":{"mode":"formdata","formdata":[]}}}
                  ]
                }
                """);
        var imported = postman.importCollection(input);
        var warning = String.join(" ", imported.warnings());
        assertTrue(warning.contains("authentication"));
        assertTrue(warning.contains("scripts"));
        assertTrue(warning.contains("Non-raw"));
        assertFalse(warning.contains("omitted-fixture-token"));

        var database = new SqliteDatabase(temporary.resolve("collection.db"));
        new CallsmithDatabase(database, new MigrationRunner()).initialize();
        var serializer = new JacksonJsonSerializer();
        var workspace = new CollectionWorkspaceService(new SqliteCollectionRepository(database),
                new SqliteCollectionFolderRepository(database), new SqliteSavedRequestRepository(database, serializer));
        var persistence = new SqliteCollectionImportPersistenceService(database, serializer);
        persistence.persist(imported);
        var mount = workspace.loadCollection(workspace.findCollections().getFirst());
        var ordered = mount.orderedChildren("");
        assertEquals(List.of("First", "Folder", "Last"), ordered.stream().map(child ->
                child.folder() == null ? child.request().name() : child.folder().name()).toList());
        var branch = ordered.get(1).folder();
        assertEquals("Inner", mount.orderedChildren(branch.id()).getFirst().folder().name());
        assertEquals("After child", mount.orderedChildren(branch.id()).getLast().request().name());

        Path out = temporary.resolve("output.json");
        new CollectionExportService(workspace, List.of(new PostmanCollectionExporter(mapper)))
                .exportCollection(mount.collection(), out);
        assertFalse(Files.readString(out).contains("omitted-fixture-token"));
        assertFalse(Files.readString(out).contains("fixture script"));
        var again = postman.importCollection(out);
        assertTrue(again.warnings().isEmpty());
        assertEquals(imported.requests().getFirst(), again.requests().getFirst());
        var saved = again.requests().getFirst();
        assertEquals("POST", saved.method());
        assertEquals("{{base}}/api#local", saved.url());
        assertEquals(Map.of("page", "2"), saved.queryParameters());
        assertEquals(Map.of("Authorization", "Bearer {{token}}"), saved.headers());
        assertEquals("{\"source\":\"{{demo}}\"}", saved.body());
        assertEquals("PATCH", again.folders().getFirst().folders().getFirst().requests().getFirst().method());

        Path bad = Files.writeString(temporary.resolve("malformed.json"), "{\n\"request\":");
        var service = new CollectionImportService(List.of(postman, new OpenApiImporter(mapper),
                new InsomniaCollectionImporter(mapper)));
        assertThrows(IllegalArgumentException.class, () -> service.importCollection(bad));
        assertEquals(1, workspace.findCollections().size());
        assertEquals(3, workspace.loadCollection(mount.collection()).orderedChildren("").size());
    }

    @Test
    void reportsInsomniaUnsupportedStructuredBodiesAndKeepsResourceOrder() throws Exception {
        Path input = Files.writeString(temporary.resolve("insomnia.json"), """
                {"__export_format":4,"resources":[
                    {"_id":"work","_type":"workspace","name":"Fixture"},
                    {"_id":"r1","parentId":"work","_type":"request","name":"First","method":"PUT","url":"{{base}}/a",
                     "body":{"mimeType":"multipart/form-data","params":[{"name":"file","fileName":"fictional-path"}]}},
                    {"_id":"folder","parentId":"work","_type":"request_group","name":"Mid"},
                    {"_id":"r2","parentId":"work","_type":"request","name":"Last","method":"GET","url":"{{base}}/b"}
                ]}
                """);
        var imported = new InsomniaCollectionImporter(mapper).importCollection(input);
        assertTrue(imported.warnings().getFirst().contains("structured body"));
        assertFalse(imported.warnings().getFirst().contains("fictional-path"));
        assertNull(imported.requests().getFirst().body());
        assertEquals(0, imported.requests().getFirst().sortOrder());
        assertEquals(1, imported.folders().getFirst().sortOrder());
        assertEquals(2, imported.requests().getLast().sortOrder());
        assertEquals("PUT", imported.requests().getFirst().method());
    }
}
