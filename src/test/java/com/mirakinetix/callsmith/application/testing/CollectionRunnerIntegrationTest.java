package com.mirakinetix.callsmith.application.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirakinetix.callsmith.application.collection.CollectionWorkspaceService;
import com.mirakinetix.callsmith.application.environment.EnvironmentVariableResolver;
import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.domain.collection.Collection;
import com.mirakinetix.callsmith.domain.collection.CollectionFolder;
import com.mirakinetix.callsmith.domain.environment.Environment;
import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;
import com.mirakinetix.callsmith.domain.request.SavedRequest;
import com.mirakinetix.callsmith.infrastructure.http.JavaHttpRequestExecutor;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.json.JacksonJsonSerializer;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mirakinetix.callsmith.application.testing.ResponseAssertion.Type.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionRunnerIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    @TempDir Path temporary;

    @Test
    void runsInOrderUsesProtectedEnvironmentAndContinuesAfterFailures() throws Exception {
        var fixture = new Fixture();
        var seen = new CopyOnWriteArrayList<String>();
        var hit = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            seen.add(path);
            if (path.equals("/timeout")) {
                try (exchange) {
                    Thread.sleep(3_000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            if ("Bearer private-fixture-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                hit.incrementAndGet();
            }
            respond(exchange, path.equals("/http-error") ? 503 : 200);
        });
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(workers);
            server.start();
            String origin = "http://localhost:" + server.getAddress().getPort();
            fixture.save("first", null, origin + "/first", 0);
            fixture.save("nested", "folder", origin + "/nested", 0);
            fixture.save("http-error", null, origin + "/http-error", 2);
            fixture.save("assertion-error", null, origin + "/assertion-error", 3);
            fixture.save("timeout", null, origin + "/timeout", 4);
            int unused;
            try (var socket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
                unused = socket.getLocalPort();
            }
            fixture.save("unreachable", null, "http://localhost:" + unused, 5);
            fixture.assertions.replace("first", List.of(new ResponseAssertion(STATUS_EQUALS, "", "200")));
            fixture.assertions.replace("assertion-error", List.of(new ResponseAssertion(STATUS_EQUALS, "", "201")));
            fixture.assertions.replace("timeout", List.of(new ResponseAssertion(STATUS_EQUALS, "", "200")));

            var total = new AtomicInteger();
            var steps = new CopyOnWriteArrayList<CollectionRunner.Step>();
            var result = fixture.runner(Duration.ofMillis(1250)).run(fixture.collection, "env", () -> false,
                    total::set, steps::add);
            assertEquals(6, total.get());
            assertEquals(List.of("/first", "/nested", "/http-error", "/assertion-error", "/timeout"), seen);
            assertEquals(4, hit.get());
            assertEquals(2, result.passed());
            assertEquals(4, result.failed());
            assertEquals(Integer.valueOf(503), steps.get(2).status());
            assertFalse(steps.get(3).assertions().getFirst().passed());
            assertTrue(steps.get(4).detail().contains("timed out"));
            assertFalse(steps.get(4).assertions().getFirst().passed());
            assertNull(steps.get(5).status());
            assertTrue(steps.get(5).detail().contains("connect"));
            assertEquals(steps, result.completed());
            assertFalse(result.toString().contains("private-fixture-token"));
            fixture.storage.lock();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cancellationKeepsCompletedResultsAndConcurrentStartCannotDuplicateRequests() throws Exception {
        var fixture = new Fixture();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var hits = new CopyOnWriteArrayList<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            hits.add(path);
            if (path.equals("/wait")) {
                entered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            respond(exchange, 200);
        });
        server.start();
        try {
            String origin = "http://localhost:" + server.getAddress().getPort();
            fixture.save("first", null, origin + "/first", 0);
            fixture.save("wait", null, origin + "/wait", 2);
            fixture.save("last", null, origin + "/last", 3);
            var runner = fixture.runner(Duration.ofSeconds(10));
            var cancelled = new AtomicBoolean();
            var summary = new CompletableFuture<CollectionRunner.Summary>();
            Thread worker = Thread.startVirtualThread(() -> {
                try {
                    summary.complete(runner.run(fixture.collection, "env", cancelled::get, count -> {}, result -> {}));
                } catch (Throwable error) {
                    summary.completeExceptionally(error);
                }
            });
            assertTrue(entered.await(4, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () ->
                    runner.run(fixture.collection, "env", () -> false, count -> {}, step -> {}));
            cancelled.set(true);
            worker.interrupt();
            var outcome = summary.get(4, TimeUnit.SECONDS);
            assertEquals(CollectionRunner.Completion.CANCELLED, outcome.completion());
            assertEquals(1, outcome.completed().size());
            assertEquals(1, outcome.passed());
            assertEquals(List.of("/first", "/wait"), hits);
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @Test
    void preservesAssertionsByRequestIdentityOnReopenAndMoveAndCascadesDeletion() throws Exception {
        var fixture = new Fixture();
        var saved = fixture.save("fixture", null, "http://localhost/fixture", 1);
        var protectedRule = new ResponseAssertion(HEADER_EQUALS, "X-Mode", "{{token}}");
        fixture.assertions.replace(saved.id(), List.of(protectedRule));
        var reopened = new SqliteAssertionRepository(new SqliteDatabase(fixture.database.databasePath()), new ObjectMapper());
        assertEquals(List.of(protectedRule), reopened.find(saved.id()));
        var moved = new SavedRequest(saved.id(), "collection", "folder", saved.name(), saved.method(), saved.url(),
                saved.headers(), saved.queryParameters(), saved.body(), saved.sortOrder(), NOW, NOW);
        fixture.workspace.saveRequest(moved);
        assertEquals(List.of(protectedRule), reopened.find(moved.id()));
        try (var connection = fixture.database.openConnection();
             var row = connection.createStatement().executeQuery("SELECT definitions FROM request_assertions")) {
            assertTrue(row.next());
            assertTrue(row.getString(1).contains("{{token}}"));
            assertFalse(row.getString(1).contains("private-fixture-token"));
        }
        assertThrows(IllegalArgumentException.class, () ->
                reopened.replace(saved.id(), List.of(new ResponseAssertion(STATUS_EQUALS, "", "invalid"))));
        assertEquals(List.of(protectedRule), reopened.find(saved.id()));
        fixture.workspace.deleteRequest(saved.id());
        assertTrue(reopened.find(saved.id()).isEmpty());
    }

    private class Fixture {
        final SqliteDatabase database = new SqliteDatabase(temporary.resolve("test.db"));
        final SqliteSecretStorage storage = new SqliteSecretStorage(database);
        final SqliteAssertionRepository assertions = new SqliteAssertionRepository(database, new ObjectMapper());
        final SqliteCollectionFolderRepository folders = new SqliteCollectionFolderRepository(database);
        final CollectionWorkspaceService workspace = new CollectionWorkspaceService(new SqliteCollectionRepository(database),
                folders, new SqliteSavedRequestRepository(database, new JacksonJsonSerializer()));
        final Collection collection = new Collection("collection", "Fixture collection", NOW, NOW);
        final EnvironmentVariableResolver resolver;

        Fixture() {
            new CallsmithDatabase(database, new MigrationRunner()).initialize();
            workspace.saveCollection(collection);
            folders.save(new CollectionFolder("folder", "collection", null, "Folder", 1, NOW, NOW));
            new SqliteEnvironmentRepository(database).save(new Environment("env", "Fixture environment", true, NOW, NOW));
            storage.initialize("fictional workspace phrase".toCharArray());
            var variables = new SqliteEnvironmentVariableRepository(database, storage);
            variables.save(new EnvironmentVariable("token", "env", "token", "private-fixture-token", true, NOW, NOW));
            resolver = new EnvironmentVariableResolver(variables);
        }

        CollectionRunner runner(Duration deadline) {
            return new CollectionRunner(workspace, assertions, new HttpRequestService(new JavaHttpRequestExecutor(), resolver),
                    new AssertionEvaluator(resolver), deadline);
        }

        SavedRequest save(String name, String folder, String url, int order) {
            return workspace.saveRequest(new SavedRequest(name, "collection", folder, name, "GET", url,
                    Map.of("Authorization", "Bearer {{token}}"), Map.of(), null, order, NOW, NOW));
        }
    }

    private static void respond(HttpExchange exchange, int status) throws IOException {
        try (exchange) {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
    }
}
