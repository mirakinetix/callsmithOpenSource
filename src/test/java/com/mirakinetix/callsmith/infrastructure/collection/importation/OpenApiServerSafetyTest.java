package com.mirakinetix.callsmith.infrastructure.collection.importation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedRequest;
import com.mirakinetix.callsmith.domain.http.HttpFailure;
import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.infrastructure.http.JavaHttpRequestExecutor;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiServerSafetyTest {
    @TempDir Path temporary;
    private Endpoint root;
    private Endpoint path;
    private Endpoint operation;
    private final OpenApiImporter importer = new OpenApiImporter(new ObjectMapper());
    private final HttpRequestService http = new HttpRequestService(new JavaHttpRequestExecutor());

    @BeforeEach
    void startEndpoints() throws IOException {
        root = new Endpoint();
        path = new Endpoint();
        operation = new Endpoint();
    }

    @AfterEach
    void stopEndpoints() {
        for (Endpoint endpoint : new Endpoint[]{root, path, operation}) {
            if (endpoint != null) endpoint.server.stop(0);
        }
    }

    @Test
    void sendsOperationsOnlyToTheirEffectiveServers() throws IOException {
        var fixture = """
                {
                  "openapi": "3.1.0",
                  "info": {"title": "Loopback fixture"},
                  "servers": [{"url": "%s/root/"}],
                  "paths": {
                    "/root-only": {"get": {}},
                    "/override": {
                      "servers": [{"url": "%s/path"}],
                      "get": {},
                      "post": {"servers": [{"url": "%s/operation"}]}
                    }
                  }
                }
                """.formatted(root.origin(), path.origin(), operation.origin());
        var requests = importer.importCollection(write(fixture)).requests();
        assertEquals(3, requests.size());
        requests.forEach(this::send);
        assertAll(
                () -> assertEquals(List.of("GET /root/root-only"), root.requests),
                () -> assertEquals(List.of("GET /path/override"), path.requests),
                () -> assertEquals(List.of("POST /operation/override"), operation.requests));
    }

    @Test
    void expandsServerDefaultsAndKeepsTheQueryAfterTheOperationPath() throws IOException {
        String fixture = """
                {
                  "openapi": "3.1.0",
                  "info": {"title": "Server variables"},
                  "servers": [{"url": "%s/root"}],
                  "paths": {"/queued": {"patch": {"servers": [
                    {"url": "http://{host}:{port}/{branch}/?label=a%%2Bb",
                     "variables": {
                       "host": {"default": "127.0.0.1"},
                       "port": {"default": "%d"},
                       "branch": {"default": "sandbox", "enum": ["sandbox", "preview"]}
                     }}
                  ]}}}
                }
                """.formatted(root.origin(), operation.server.getAddress().getPort());
        send(importer.importCollection(write(fixture)).requests().getFirst());
        assertEquals(List.of("PATCH /sandbox/queued?label=a%2Bb"), operation.requests);
        assertEquals(List.of(), root.requests);
        assertEquals(List.of(), path.requests);
    }

    @Test
    void leavesRelativeAndDefaultServersUnroutableForLocalFiles() throws IOException {
        String fixture = """
                {
                  "openapi": "3.1.0",
                  "info": {"title": "Relative servers"},
                  "servers": [{"url": "%s/root"}],
                  "paths": {
                    "/relative": {"servers": [{"url": "../sandbox/?q=1"}], "post": {}},
                    "/default": {"post": {"servers": []}}
                  }
                }
                """.formatted(root.origin());
        var collection = importer.importCollection(write(fixture));
        assertEquals(List.of("../sandbox/relative?q=1", "/default"),
                collection.requests().stream().map(ImportedRequest::url).toList());
        assertTrue(collection.warnings().stream().anyMatch(warning -> warning.contains("no HTTP origin")));
        for (var request : collection.requests()) assertThrows(HttpFailure.class, () -> send(request));

        var noServers = importer.importCollection(write("""
                {"openapi":"3.1.0","info":{"title":"Default"},"paths":{"/default":{"post":{}}}}
                """));
        var request = noServers.requests().getFirst();
        assertEquals("/default", request.url());
        assertThrows(HttpFailure.class, () -> send(request));
        assertEquals(List.of(), root.requests);
        assertEquals(List.of(), path.requests);
        assertEquals(List.of(), operation.requests);
    }

    @Test
    void rejectsInvalidOverridesWithoutFallingBackToAnAncestor() throws IOException {
        var invalid = List.of("null", "{}", "[{}]", "[{\"url\":false}]", "[{\"url\":\" \"}]",
                "[{\"url\":\"ftp://127.0.0.1/sandbox\"}]", "[{\"url\":\"//127.0.0.1/sandbox\"}]",
                "[{\"url\":\"http://127.0.0.1:0/sandbox\"}]", "[{\"url\":\"http://127.0.0.1:70000/sandbox\"}]",
                "[{\"url\":\"http://127.0.0.1:/sandbox\"}]", "[{\"url\":\"http://127.0.0.1/sandbox#fragment\"}]",
                "[{\"url\":\"http://name@127.0.0.1/sandbox\"}]", "[{\"url\":\"http://{host}/sandbox\"}]",
                "[{\"url\":\"http://{host}/sandbox\",\"variables\":{\"host\":{\"default\":\"localhost\",\"enum\":[\"127.0.0.1\"]}}}]",
                "[{\"url\":\"http://127.0.0.1/%ZZ\"}]",
                "[{\"url\":\"http://127.0.0.1:0\"},{\"url\":\"" + operation.origin() + "\"}]");
        String template = """
                {
                  "openapi": "3.1.0",
                  "info": {"title": "Invalid operation override"},
                  "servers": [{"url": "%s"}],
                  "paths": {"/queued": {
                    "servers": [{"url": "%s"}],
                    "post": {"servers": %s}
                  }}
                }
                """;
        for (String declaration : invalid) {
            var file = write(template.formatted(root.origin(), path.origin(), declaration));
            var failure = assertThrows(IllegalArgumentException.class, () -> importer.importCollection(file));
            assertTrue(failure.getMessage().startsWith("Invalid OpenAPI operation server:"), failure::getMessage);
        }

        String badPath = """
                {"openapi":"3.1.0","info":{"title":"Invalid path override"},"servers":[{"url":"%s"}],
                 "paths":{"/queued":{"servers":[null],"post":{}}}}
                """.formatted(root.origin());
        var file = write(badPath);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> importer.importCollection(file))
                .getMessage().startsWith("Invalid OpenAPI path server:"));
        assertEquals(List.of(), root.requests);
        assertEquals(List.of(), path.requests);
        assertEquals(List.of(), operation.requests);
    }

    private Path write(String source) throws IOException {
        return Files.writeString(temporary.resolve("openapi.json"), source);
    }

    private void send(ImportedRequest request) {
        assertEquals(204, http.execute(new HttpRequest(request.method(), request.url(),
                request.headers(), request.queryParameters(), request.body())).statusCode());
    }

    private static final class Endpoint {
        private final HttpServer server;
        private final List<String> requests = new CopyOnWriteArrayList<>();

        Endpoint() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try (exchange) {
                    requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
                    exchange.sendResponseHeaders(204, -1);
                }
            });
            server.start();
        }

        String origin() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }
    }
}
