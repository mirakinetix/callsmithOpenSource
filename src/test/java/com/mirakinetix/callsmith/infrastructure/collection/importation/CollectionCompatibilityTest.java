package com.mirakinetix.callsmith.infrastructure.collection.importation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirakinetix.callsmith.application.collection.CollectionExportService;
import com.mirakinetix.callsmith.application.collection.CollectionWorkspaceService;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImportService;
import com.mirakinetix.callsmith.domain.collection.Collection;
import com.mirakinetix.callsmith.infrastructure.collection.PostmanCollectionExporter;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.json.JacksonJsonSerializer;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
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
    void rejectsInsomniaOrphansBeforePersisting() throws Exception {
        var input = Files.writeString(temporary.resolve("orphan.json"), """
                {"__export_format":4,"resources":[
                    {"_id":"work","_type":"workspace","name":"Fixture"},
                    {"_id":"valid","_type":"request","parentId":"work","url":"http://127.0.0.1/valid"},
                    {"_id":"orphan","_type":"request","parentId":"missing","url":"http://127.0.0.1/orphan"}
                ]}
                """);
        var database = new SqliteDatabase(temporary.resolve("orphan.db"));
        new CallsmithDatabase(database, new MigrationRunner()).initialize();
        var serializer = new JacksonJsonSerializer();
        var workspace = new CollectionWorkspaceService(new SqliteCollectionRepository(database),
                new SqliteCollectionFolderRepository(database), new SqliteSavedRequestRepository(database, serializer));
        workspace.saveCollection(new Collection("existing", "Existing", Instant.EPOCH, Instant.EPOCH));
        var service = new CollectionImportService(List.of(new InsomniaCollectionImporter(mapper)));
        var persistence = new SqliteCollectionImportPersistenceService(database, serializer);
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> persistence.persist(service.importCollection(input))),
                () -> assertEquals(List.of("Existing"), workspace.findCollections().stream().map(c -> c.name()).toList()));
    }

    @Test
    void rejectsMalformedInsomniaParentGraphsAndResourceRecords() throws Exception {
        String workspace = """
                {"_id":"wrk","_type":"workspace"}
                """;
        var cases = List.of(
                new String[]{"has no parent", """
                    {"_id":"req","_type":"request","url":"http://127.0.0.1/read"}
                    """},
                new String[]{"unknown parent", """
                    {"_id":"folder","_type":"request_group","parentId":"unknown"}
                    """},
                new String[]{"folder cycle", """
                    {"_id":"a","_type":"request_group","parentId":"b"},
                    {"_id":"b","_type":"request_group","parentId":"a"},
                    {"_id":"r","_type":"request","parentId":"a","url":"http://127.0.0.1/read"}
                    """},
                new String[]{"duplicate", """
                    {"_id":"wrk","_type":"request_group","parentId":"wrk"}
                    """},
                new String[]{"record type", """
                    {"_id":"item","_type":false}
                    """},
                new String[]{"cannot have a parent", """
                    {"_id":"child-workspace","_type":"workspace","parentId":"wrk"}
                    """},
                new String[]{"must belong to a workspace or folder", """
                    {"_id":"env","_type":"environment","parentId":"wrk"},
                    {"_id":"req","_type":"request","parentId":"env","url":"http://127.0.0.1/read"}
                    """});
        var importer = new InsomniaCollectionImporter(mapper);
        for (var fixture : cases) {
            var input = Files.writeString(temporary.resolve("insomnia-graph.json"),
                    "{\"__export_format\":4,\"resources\":[" + workspace + "," + fixture[1] + "]}");
            var error = assertThrows(IllegalArgumentException.class, () -> importer.importCollection(input));
            assertTrue(error.getMessage().contains(fixture[0]), error::getMessage);
        }
    }

    @Test
    void preservesNestedAndRootInsomniaRequestsAndWarnsAboutUnsupportedResources() throws Exception {
        var input = Files.writeString(temporary.resolve("valid-graph.json"), """
                {"__export_format":4,"resources":[
                    {"_id":"wrk","_type":"workspace","name":"Ordinal"},
                    {"_id":"a","_type":"request","parentId":"wrk","name":"First","url":"http://127.0.0.1/a"},
                    {"_id":"f1","_type":"request_group","parentId":"wrk","name":"Outer"},
                    {"_id":"env","_type":"environment","parentId":"wrk"},
                    {"_id":"b","_type":"request","parentId":"wrk","name":"Last","url":"http://127.0.0.1/b"},
                    {"_id":"f2","_type":"request_group","parentId":"f1","name":"Inner"},
                    {"_id":"c","_type":"request","parentId":"f2","name":"Nested","url":"http://127.0.0.1/c"}
                ]}
                """);
        var result = new InsomniaCollectionImporter(mapper).importCollection(input);
        var outer = result.folders().getFirst();
        var nested = outer.folders().getFirst().requests();
        assertEquals(3, Stream.concat(result.requests().stream(), nested.stream()).count());
        assertEquals(List.of("First", "Last"), result.requests().stream().map(request -> request.name()).toList());
        assertEquals(List.of(0, 2), result.requests().stream().map(request -> request.sortOrder()).toList());
        assertEquals(1, outer.sortOrder());
        assertEquals("Nested", nested.getFirst().name());
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("1 unsupported")));
    }

    @Test
    void doesNotReplaceExistingNormalizedExportWithoutApproval() throws Exception {
        var database = new SqliteDatabase(temporary.resolve("export.db"));
        new CallsmithDatabase(database, new MigrationRunner()).initialize();
        var workspace = new CollectionWorkspaceService(new SqliteCollectionRepository(database),
                new SqliteCollectionFolderRepository(database),
                new SqliteSavedRequestRepository(database, new JacksonJsonSerializer()));
        var collection = workspace.saveCollection(new Collection("fixture", "Fixture", Instant.EPOCH, Instant.EPOCH));
        var export = new CollectionExportService(workspace, List.of(new PostmanCollectionExporter(mapper)));
        var target = Files.writeString(temporary.resolve("report.json"), "existing bytes");
        assertAll(
                () -> assertThrows(RuntimeException.class, () -> export.exportCollection(collection, target)),
                () -> assertEquals("existing bytes", Files.readString(target)));
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
