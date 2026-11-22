package com.mirakinetix.callsmith.infrastructure.http;

import com.mirakinetix.callsmith.application.environment.EnvironmentVariableResolver;
import com.mirakinetix.callsmith.application.http.HttpRequestService;
import com.mirakinetix.callsmith.application.testing.AssertionEvaluator;
import com.mirakinetix.callsmith.application.testing.ResponseAssertion;
import com.mirakinetix.callsmith.domain.environment.Environment;
import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;
import com.mirakinetix.callsmith.domain.http.HttpFailure;
import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.RequestOptions;
import com.mirakinetix.callsmith.domain.http.RequestOptions.Authentication;
import com.mirakinetix.callsmith.domain.http.RequestOptions.Field;
import com.mirakinetix.callsmith.domain.http.SelectedUpload;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteEnvironmentRepository;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteEnvironmentVariableRepository;
import com.mirakinetix.callsmith.infrastructure.persistence.sqlite.SqliteSecretStorage;
import com.mirakinetix.callsmith.ui.response.ResponsePreview;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import static com.mirakinetix.callsmith.domain.http.HttpFailure.Kind.*;
import static com.mirakinetix.callsmith.domain.http.RequestOptions.AuthType.*;
import static com.mirakinetix.callsmith.domain.http.RequestOptions.BodyFormat.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpRecoveryTest {
    @TempDir Path temporary;
    private HttpServer server;
    private HttpServer destination;
    private final AtomicInteger destinationHits = new AtomicInteger();
    private final AtomicInteger echoHits = new AtomicInteger();
    private final JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

    @BeforeEach
    void setup() throws IOException {
        server = localServer();
        destination = localServer();
        destination.createContext("/destination", exchange -> {
            destinationHits.incrementAndGet();
            reply(exchange, "text/plain", "unexpected request".getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/echo", exchange -> {
            echoHits.incrementAndGet();
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("X-Method", exchange.getRequestMethod());
            exchange.getResponseHeaders().set("X-Request-Bytes", Integer.toString(bytes.length));
            echoHeader(exchange, "Content-Type", "X-Request-Type");
            echoHeader(exchange, "Authorization", "X-Request-Auth");
            echoHeader(exchange, "X-Api-Key", "X-Request-Key");
            echoHeader(exchange, "X-Metadata", "X-Request-Metadata");
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null) exchange.getResponseHeaders().set("X-Request-Query", query);
            if (exchange.getRequestMethod().equals("HEAD")) {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            } else {
                reply(exchange, "text/plain; charset=UTF-8", bytes);
            }
        });
        server.start();
        destination.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        destination.stop(0);
    }

    @Test
    void allSupportedMethodsPreserveExpectedMethodAndUnicodeBytes() {
        for (String method : List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")) {
            String body = List.of("POST", "PUT", "PATCH").contains(method) ? "snowman " + (char) 9731 : null;
            var response = executor.execute(new HttpRequest(method, url("/echo"), Map.of(), Map.of(), body));
            assertEquals(method, response.headers().get("x-method"));
            int length = body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length;
            assertEquals(Integer.toString(length), response.headers().get("x-request-bytes"));
            assertEquals(length, response.sizeBytes());
            assertEquals(body == null ? "" : body, response.body());
        }
    }

    @Test
    void blocksForeignOriginBeforeAnyRequestEvenWithHeadersCookiesAndQueryCredentials() {
        server.createContext("/cross", exchange -> redirect(exchange, 307,
                origin(destination) + "/destination?copy=credential-in-location"));
        var request = new HttpRequest("POST", url("/cross?source=fictional-query"),
                Map.of("Authorization", "Bearer fictional-auth", "Cookie", "token=fictional-cookie",
                        "X-Api-Key", "fictional-header"), Map.of(), "fictional-body");
        HttpFailure failure = assertThrows(HttpFailure.class, () -> executor.execute(request));
        assertEquals(ORIGIN, failure.kind());
        assertEquals(0, destinationHits.get());
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("credential-in-location"));
        assertEquals(INVALID, assertThrows(HttpFailure.class, () -> executor.execute(
                new HttpRequest("GET", url("/echo"), Map.of("Proxy-Authorization", "fixture"), Map.of(), null))).kind());
    }

    @Test
    void blocksDowngradeFromRealLocalTlsServer() throws Exception {
        Path keyStoreFile = temporary.resolve("test-tls.p12");
        char[] password = "test-fixture-password".toCharArray();
        String executable = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
        Path keytool = Path.of(System.getProperty("java.home"), "bin", executable);
        Process keygen = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "loopback",
                "-keyalg", "EC", "-dname", "CN=localhost", "-ext", "SAN=DNS:localhost",
                "-validity", "2", "-storetype", "PKCS12", "-keystore", keyStoreFile.toString(),
                "-storepass", new String(password), "-noprompt")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            assertTrue(keygen.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, keygen.exitValue());
        } finally {
            if (keygen.isAlive()) keygen.destroyForcibly();
        }
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keyStoreFile)) {
            keys.load(input, password);
        }
        var keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keys, password);
        var trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(keys);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
        var secure = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        secure.setHttpsConfigurator(new HttpsConfigurator(tls));
        secure.createContext("/", exchange -> redirect(exchange, 302, origin(destination) + "/destination"));
        secure.start();
        try {
            var request = new HttpRequest("GET", "https://localhost:" + secure.getAddress().getPort() + "/",
                    Map.of("Authorization", "Bearer mock-key"), Map.of(), null);
            var failure = assertThrows(HttpFailure.class, () -> new JavaHttpRequestExecutor(tls).execute(request));
            assertEquals(DOWNGRADE, failure.kind());
            assertEquals(0, destinationHits.get());
        } finally {
            secure.stop(0);
        }
    }

    @Test
    void comparesRealLocationHeadersWithoutExposingThemInReports() {
        String location = "/fixture-private-location";
        String contentLocation = "/fixture-private-content";
        server.createContext("/header-rules", exchange -> {
            exchange.getResponseHeaders().set("Location", location);
            exchange.getResponseHeaders().set("Content-Location", contentLocation);
            reply(exchange, "text/plain", new byte[0]);
        });
        var request = new HttpRequest("GET", url("/header-rules?fixture=1"), Map.of(), Map.of(), null);
        var response = new HttpRequestService(executor).execute(request).withoutUrl();
        var equals = ResponseAssertion.Type.HEADER_EQUALS;
        var results = new AssertionEvaluator((value, environment) -> location).evaluate(response, List.of(
                new ResponseAssertion(equals, "lOcAtIoN", "{{fixture}}"),
                new ResponseAssertion(equals, "Location", "/incorrect"),
                new ResponseAssertion(equals, "Content-Location", contentLocation),
                new ResponseAssertion(equals, "Content-Location", "[hidden]"),
                new ResponseAssertion(equals, "Missing", "expected"),
                new ResponseAssertion(equals, "Malformed Header", "expected")), "fixture");
        assertAll(
                () -> assertEquals(List.of(true, false, true, false, false, false),
                        results.stream().map(AssertionEvaluator.Result::passed).toList()),
                () -> assertEquals("[hidden]", response.headers().get("location")),
                () -> assertEquals("[hidden]", response.headers().get("content-location")),
                () -> assertFalse(results.toString().contains("fixture-private")),
                () -> assertFalse(response.toString().contains("fixture-private")));
    }

    @Test
    void redirectsPreserveOrRewriteMethodsAndHideResponseLocation() {
        for (int status : new int[]{301, 302, 303, 307, 308}) {
            server.createContext("/redirect" + status, exchange -> redirect(exchange, status, "/echo?hidden=fixture"));
            var response = executor.execute(new HttpRequest("POST", url("/redirect" + status),
                    Map.of("Content-Type", "text/plain", "Authorization", "Bearer local-fixture"), Map.of(), "body"));
            boolean preserves = status == 307 || status == 308;
            assertEquals(preserves ? "POST" : "GET", response.headers().get("x-method"));
            assertEquals("Bearer local-fixture", response.headers().get("x-request-auth"));
            assertEquals(preserves ? "text/plain" : null, response.headers().get("x-request-type"));
            assertEquals(preserves ? "body" : "", response.body());
            assertEquals(origin(server), response.url());
        }
    }

    @Test
    void detectsLoopsHopLimitAndAmbiguousLocation() {
        server.createContext("/loop", exchange -> redirect(exchange, 302, "/loop#ignored"));
        assertEquals(LOOP, failure("/loop"));
        var hops = new AtomicInteger();
        server.createContext("/hops", exchange -> redirect(exchange, 307, "/hops?n=" + hops.incrementAndGet()));
        assertEquals(REDIRECT_LIMIT, failure("/hops"));
        assertEquals(6, hops.get());
        server.createContext("/ambiguous", exchange -> {
            exchange.getResponseHeaders().add("Location", "/echo");
            redirect(exchange, 302, "/loop");
        });
        assertEquals(LOCATION, failure("/ambiguous"));
    }

    @Test
    void preservesExistingRepeatedQueryAndQueryOnlyRedirectPath() {
        server.createContext("/query", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if ("redirect=1".equals(query)) {
                redirect(exchange, 302, "?dup=1&dup=2");
            } else {
                reply(exchange, "text/plain", query.getBytes(StandardCharsets.UTF_8));
            }
        });
        var response = executor.execute(new HttpRequest("GET", url("/query?dup=first&#fragment"),
                Map.of(), Map.of("dup", "second value"), null));
        assertEquals("dup=first&dup=second+value", response.body());
        assertEquals(url("/query"), response.url());
        assertEquals("dup=1&dup=2", executor.execute(request("/query?redirect=1")).body());
    }

    @Test
    void authenticationHelpersConstructHeadersAndQueryWithoutLoggingCredentials() {
        var basic = auth(BASIC, "fixture-user", "fictional-password");
        assertEquals("Basic " + Base64.getEncoder().encodeToString(
                "fixture-user:fictional-password".getBytes(StandardCharsets.UTF_8)),
                executor.execute(withAuth("/echo", basic)).headers().get("x-request-auth"));
        assertEquals("Bearer fixture-token", executor.execute(withAuth("/echo",
                auth(BEARER, "", "fixture-token"))).headers().get("x-request-auth"));
        assertEquals("fixture-api-key", executor.execute(withAuth("/echo",
                auth(API_HEADER, "X-Api-Key", "fixture-api-key"))).headers().get("x-request-key"));
        server.createContext("/query-auth", exchange -> reply(exchange, "text/plain",
                exchange.getRequestURI().getRawQuery().getBytes(StandardCharsets.UTF_8)));
        var query = withAuth("/query-auth", auth(API_QUERY, "apiKey", "fake + token"));
        var result = new HttpRequestService(executor).execute(query);
        assertEquals("apiKey=fake+%2B+token", result.body());
        assertNull(result.url());
        assertFalse(basic.toString().contains("fictional-password"));

        var conflict = new HttpRequest("GET", url("/echo"), Map.of("authorization", "manual"),
                Map.of(), null, options(RAW, basic, List.of(), null));
        assertEquals(AUTH_CONFLICT, assertThrows(HttpFailure.class, () -> executor.execute(conflict)).kind());
        var duplicateQuery = withAuth("/echo?%61piKey=first", auth(API_QUERY, "apiKey", "second"));
        assertEquals(AUTH_CONFLICT, assertThrows(HttpFailure.class, () -> executor.execute(duplicateQuery)).kind());
        assertEquals(AUTH_INVALID, assertThrows(HttpFailure.class, () -> executor.execute(
                withAuth("/echo", auth(BASIC, "fixture-user", "{{locked_or_missing}}")))).kind());
    }

    @Test
    void rejectsMissingVariablesBeforeSendingPost() {
        var service = new HttpRequestService(executor, (value, environment) -> value);
        var request = new HttpRequest("POST", url("/echo"), Map.of("X-Fixture", "{{missing}}"), Map.of(), "sample");
        assertAll(
                () -> assertEquals(VARIABLE_MISSING, assertThrows(HttpFailure.class,
                        () -> service.execute(request, "fixture")).kind()),
                () -> assertEquals(VARIABLE_ENVIRONMENT, assertThrows(HttpFailure.class,
                        () -> service.execute(request)).kind()),
                () -> assertEquals(0, echoHits.get()));
    }

    @Test
    void rejectsMissingVariablesInEverySupportedRequestField() {
        var service = new HttpRequestService(executor, (value, environment) -> value);
        String absent = "{{missing}}";
        String target = url("/echo");
        var cases = List.of(
                new HttpRequest("POST", target + "?raw=" + absent, Map.of(), Map.of(), "body"),
                new HttpRequest("POST", target, Map.of(absent, "value"), Map.of(), null),
                new HttpRequest("POST", target, Map.of(), Map.of(absent, "value"), null),
                new HttpRequest("POST", target, Map.of(), Map.of("name", absent), null),
                new HttpRequest("DELETE", target, Map.of(), Map.of(), absent),
                withAuth("/echo", auth(API_HEADER, absent, "credential")),
                withAuth("/echo", auth(API_QUERY, "key", absent)),
                post(null, options(FORM, Authentication.NONE, List.of(new Field(absent, "value")), null)),
                post(null, options(MULTIPART, Authentication.NONE, List.of(new Field("name", absent)), null)));
        for (var request : cases) {
            assertEquals(VARIABLE_MISSING,
                    assertThrows(HttpFailure.class, () -> service.execute(request, "fixture")).kind());
        }
        assertEquals(0, echoHits.get());
        assertEquals(0, destinationHits.get());
    }

    @Test
    void sendsResolvedFieldsAndIntentionalLiteralOnlyToSelectedLoopback() throws IOException {
        Map<String, String> values = Map.of("{{target}}", origin(server), "{{header}}", "X-Metadata",
                "{{query}}", "kind", "{{value}}", "sample", "{{auth}}", "X-Api-Key",
                "{{token}}", "fixture-token", "{{field}}", "attachment");
        var service = new HttpRequestService(executor, (value, environment) -> values.getOrDefault(value, value));
        var request = new HttpRequest("POST", "{{target}}/echo?base={{value}}",
                Map.of("{{header}}", "{{value}}"), Map.of("{{query}}", "{{value}}"),
                "{\"value\":\"{{value}}\",\"literal\":\"{!{example}}\"}",
                options(JSON, auth(API_HEADER, "{{auth}}", "{{token}}"), List.of(), null));
        var response = service.execute(request, "fixture");
        assertEquals("POST", response.headers().get("x-method"));
        assertEquals("sample", response.headers().get("x-request-metadata"));
        assertEquals("fixture-token", response.headers().get("x-request-key"));
        assertEquals("base=sample&kind=sample", response.headers().get("x-request-query"));
        assertEquals("{\"value\":\"sample\",\"literal\":\"{{example}}\"}", response.body());
        assertNull(response.url());

        var literal = new HttpRequest("POST", url("/echo"), Map.of(), Map.of(), "<item>{!{example}}</item>");
        assertEquals("<item>{{example}}</item>", service.execute(literal).body());
        var encoded = post(null, options(FORM, Authentication.NONE,
                List.of(new Field("{{query}}", "{{value}}")), null));
        assertEquals("kind=sample", service.execute(encoded, "fixture").body());

        Path payload = Files.writeString(temporary.resolve("fixture.txt"), "upload fixture");
        var upload = SelectedUpload.select(payload, "{{field}}");
        var multipart = post(null, options(MULTIPART, Authentication.NONE, List.of(), upload));
        assertTrue(new String(service.execute(multipart, "fixture").bytes(), StandardCharsets.UTF_8)
                .contains("name=\"attachment\""));
        assertEquals(VARIABLE_MISSING, assertThrows(HttpFailure.class, () -> service.execute(post(null,
                options(MULTIPART, Authentication.NONE, List.of(), upload.withField("{{missing}}"))), "fixture")).kind());
        assertEquals(4, echoHits.get());
        assertEquals(0, destinationHits.get());
    }

    @Test
    void protectedEnvironmentCredentialResolvesOnlyWhileUnlocked() {
        var db = new SqliteDatabase(temporary.resolve("workspace.db"));
        new CallsmithDatabase(db, new MigrationRunner()).initialize();
        Instant now = Instant.now();
        new SqliteEnvironmentRepository(db).save(new Environment("env", "Fixture", true, now, now));
        var storage = new SqliteSecretStorage(db);
        storage.initialize("fixture-only passphrase".toCharArray());
        var variables = new SqliteEnvironmentVariableRepository(db, storage);
        variables.save(new EnvironmentVariable("secret", "env", "token", "private-fixture", true, now, now));
        var service = new HttpRequestService(executor, new EnvironmentVariableResolver(variables));
        var request = withAuth("/echo", auth(BEARER, "", "{{token}}"));
        assertEquals("Bearer private-fixture", service.execute(request, "env").headers().get("x-request-auth"));
        storage.lock();
        var failure = assertThrows(HttpFailure.class, () -> service.execute(request, "env"));
        assertEquals(VARIABLE_UNAVAILABLE, failure.kind());
        assertFalse(failure.getMessage().contains("private-fixture"));
        assertEquals(1, echoHits.get());
        assertNull(variables.findSummariesByEnvironmentId("env").getFirst().value());
        storage.unlock("fixture-only passphrase".toCharArray());
        assertEquals("Bearer private-fixture", service.execute(request, "env").headers().get("x-request-auth"));
        assertEquals(2, echoHits.get());
    }

    @Test
    void appliesRawMediaTypesAndUrlEncodedForms() {
        Map<RequestOptions.BodyFormat, String> mediaTypes = Map.of(JSON, "application/json",
                TEXT, "text/plain; charset=UTF-8", XML, "application/xml; charset=UTF-8");
        for (var entry : mediaTypes.entrySet()) {
            String raw = switch (entry.getKey()) {
                case JSON -> "{\"message\":\"cafe" + (char) 233 + "\"}";
                case XML -> "<data>cafe" + (char) 233 + "</data>";
                default -> "cafe" + (char) 233;
            };
            var response = executor.execute(post(raw, options(entry.getKey(), Authentication.NONE, List.of(), null)));
            assertEquals(raw, response.body());
            assertEquals(entry.getValue(), response.headers().get("x-request-type"));
        }
        var form = options(FORM, Authentication.NONE, List.of(new Field("name", "a+b"), new Field("name", "c d")), null);
        var encoded = executor.execute(post(null, form));
        assertEquals("name=a%2Bb&name=c+d", encoded.body());
        assertEquals("application/x-www-form-urlencoded", encoded.headers().get("x-request-type"));
        assertEquals(BODY, assertThrows(HttpFailure.class, () -> executor.execute(post("raw", form))).kind());
    }

    @Test
    void encodesExplicitRequestCharsetAndRejectsUnrepresentableText() {
        String latin = "caf" + (char) 233;
        var headers = Map.of("Content-Type", "text/plain; charset=ISO-8859-1");
        var response = executor.execute(new HttpRequest("POST", url("/echo"), headers, Map.of(), latin));
        assertArrayEquals(latin.getBytes(StandardCharsets.ISO_8859_1), response.bytes());
        assertEquals(BODY, assertThrows(HttpFailure.class, () -> executor.execute(
                new HttpRequest("POST", url("/echo"), headers, Map.of(), "snowman " + (char) 9731))).kind());
    }

    @Test
    void malformedJsonKeepsRawPreviewAndDiagnosticOutputOmitted() {
        String malformed = "{\"key\":1,\"key\":2}";
        var bytes = malformed.getBytes(StandardCharsets.UTF_8);
        var response = new com.mirakinetix.callsmith.domain.http.HttpResponse(200, null,
                Map.of("content-type", "application/json"), malformed, 2, bytes.length, null, bytes);
        var preview = ResponsePreview.prepare(response);
        assertEquals(malformed, preview.raw());
        assertNull(preview.formatted());
        assertFalse(preview.toString().contains("key"));
        assertArrayEquals(bytes, response.bytes());
    }

    @Test
    void streamsMultipartFileWithBinaryBytesAndRejectsChangedOrRemovedSelection() throws IOException {
        Path path = temporary.resolve("payload.bin");
        byte[] bytes = {0, 1, (byte) 255, 13, 10};
        Files.write(path, bytes);
        server.createContext("/upload", exchange -> {
            String type = exchange.getRequestHeaders().getFirst("Content-Type");
            exchange.getResponseHeaders().set("X-Request-Type", type);
            reply(exchange, "application/octet-stream", exchange.getRequestBody().readAllBytes());
        });
        var selected = SelectedUpload.select(path, "file");
        var multipart = options(MULTIPART, Authentication.NONE, List.of(new Field("meta", "fixture")), selected);
        var request = new HttpRequest("POST", url("/upload"), Map.of(), Map.of(), null, multipart);
        var response = executor.execute(request);
        String boundary = response.headers().get("x-request-type").split("boundary=", 2)[1];
        String assembled = new String(response.bytes(), StandardCharsets.ISO_8859_1);
        assertTrue(assembled.startsWith("--" + boundary + "\r\n"));
        assertTrue(assembled.contains("name=\"meta\""));
        assertTrue(assembled.contains("name=\"file\"; filename=\"payload.bin\""));
        assertTrue(assembled.contains("\r\n\r\n" + new String(bytes, StandardCharsets.ISO_8859_1) + "\r\n"));
        assertTrue(assembled.endsWith("--" + boundary + "--\r\n"));
        Files.write(path, new byte[]{9});
        assertEquals(UPLOAD, assertThrows(HttpFailure.class, () -> executor.execute(request)).kind());
        Files.delete(path);
        assertEquals(UPLOAD, assertThrows(HttpFailure.class, () -> executor.execute(request)).kind());
    }

    @Test
    void decodesTextAndCompressedJsonAndRetainsOriginalBytes() throws IOException {
        byte[] latin = "caf\u00e9".getBytes(StandardCharsets.ISO_8859_1);
        byte[] compressed = gzip("{\"inner\":[1,2]}".getBytes(StandardCharsets.UTF_8));
        server.createContext("/latin", exchange -> reply(exchange, "text/plain; charset=\"ISO-8859-1\"", latin));
        server.createContext("/json", exchange -> {
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            reply(exchange, "application/problem+json", compressed);
        });
        byte[] xml = "<?xml version=\"1.0\"?><data>caf\u00e9</data>".getBytes(StandardCharsets.UTF_16);
        server.createContext("/xml", exchange -> reply(exchange, "application/xml", xml));
        var text = executor.execute(request("/latin"));
        assertEquals("caf\u00e9", text.body());
        assertEquals(latin.length, text.sizeBytes());
        assertArrayEquals(latin, text.bytes());
        var json = executor.execute(request("/json"));
        assertEquals("{\"inner\":[1,2]}", json.body());
        assertArrayEquals(compressed, json.bytes());
        assertEquals(compressed.length, json.sizeBytes());
        assertTrue(ResponsePreview.prepare(json).formatted().contains("\n"));
        assertArrayEquals(compressed, json.bytes());
        assertTrue(executor.execute(request("/xml")).body().contains("caf\u00e9"));
    }

    @Test
    void preservesBinaryAndRejectsOversizedWireAndInflatedResponses() throws IOException {
        byte[] data = {0, (byte) 255, (byte) 128, 1};
        server.createContext("/binary", exchange -> reply(exchange, "application/octet-stream", data));
        var binary = executor.execute(request("/binary"));
        assertTrue(binary.binary());
        assertNull(binary.body());
        assertEquals(data.length, binary.sizeBytes());
        byte[] editableCopy = binary.bytes();
        editableCopy[0] = 77;
        assertArrayEquals(data, binary.bytes());

        byte[] oversized = new byte[ResponseContent.LIMIT_BYTES + 1];
        byte[] compressed = gzip(oversized);
        server.createContext("/oversized", exchange -> reply(exchange, "application/octet-stream", oversized));
        server.createContext("/inflated", exchange -> {
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            reply(exchange, "text/plain", compressed);
        });
        assertEquals(LIMIT, failure("/oversized"));
        assertEquals(LIMIT, failure("/inflated"));
    }

    @Test
    void timeoutCoversBodyAndCancellationDoesNotAffectOtherRequests() throws Exception {
        var firstByte = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            // Separate contexts share a worker pool in this fixture.
            var slow = localServer();
            slow.setExecutor(workers);
            slow.createContext("/slow", exchange -> {
                try (exchange) {
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().write('a');
                    exchange.getResponseBody().flush();
                    firstByte.countDown();
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignored) {
                    // Expected when the client cancels the connection.
                }
            });
            slow.start();
            try {
                var timed = new HttpRequest("GET", origin(slow) + "/slow", Map.of(), Map.of(), null,
                        new RequestOptions(Duration.ofMillis(500), Authentication.NONE, RAW, List.of(), null));
                assertEquals(TIMEOUT, assertThrows(HttpFailure.class, () -> executor.execute(timed)).kind());
                assertTrue(firstByte.await(2, TimeUnit.SECONDS));
                var started = new CountDownLatch(1);
                slow.createContext("/cancel", exchange -> {
                    try (exchange) {
                        started.countDown();
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
                var error = new java.util.concurrent.CompletableFuture<HttpFailure.Kind>();
                var job = workers.submit(() -> {
                    try {
                        executor.execute(new HttpRequest("GET", origin(slow) + "/cancel", Map.of(), Map.of(), null));
                    } catch (HttpFailure failure) {
                        error.complete(failure.kind());
                    }
                });
                assertTrue(started.await(3, TimeUnit.SECONDS));
                assertTrue(job.cancel(true));
                assertEquals(INTERRUPTED, error.get(3, TimeUnit.SECONDS));
                assertEquals(200, executor.execute(request("/echo")).statusCode());
            } finally {
                release.countDown();
                slow.stop(0);
            }
        }
    }

    private HttpFailure.Kind failure(String path) {
        return assertThrows(HttpFailure.class, () -> executor.execute(request(path))).kind();
    }
    private String url(String path) { return origin(server) + path; }
    private HttpRequest request(String path) { return new HttpRequest("GET", url(path), Map.of(), Map.of(), null); }
    private HttpRequest post(String body, RequestOptions options) {
        return new HttpRequest("POST", url("/echo"), Map.of(), Map.of(), body, options);
    }
    private HttpRequest withAuth(String path, Authentication auth) {
        return new HttpRequest("GET", url(path), Map.of(), Map.of(), null, options(RAW, auth, List.of(), null));
    }
    private static Authentication auth(RequestOptions.AuthType type, String name, String value) {
        return new Authentication(type, name, value);
    }
    private static RequestOptions options(RequestOptions.BodyFormat format, Authentication auth,
                                          List<Field> fields, SelectedUpload upload) {
        return new RequestOptions(RequestOptions.DEFAULT_TIMEOUT, auth, format, fields, upload);
    }
    private static HttpServer localServer() throws IOException {
        return HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    }
    private static String origin(HttpServer server) { return "http://localhost:" + server.getAddress().getPort(); }
    private static void echoHeader(HttpExchange exchange, String from, String to) {
        var header = exchange.getRequestHeaders().getFirst(from);
        if (header != null) exchange.getResponseHeaders().set(to, header);
    }
    private static void reply(HttpExchange exchange, String type, byte[] bytes) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length != 0) exchange.getResponseBody().write(bytes);
        }
    }
    private static void redirect(HttpExchange exchange, int status, String location) throws IOException {
        try (exchange) {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(status, -1);
        }
    }
    private static byte[] gzip(byte[] bytes) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new GZIPOutputStream(output)) {
            zip.write(bytes);
        }
        return output.toByteArray();
    }
}
