package com.mirakinetix.callsmith.infrastructure.http;

import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.HttpResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaHttpRequestExecutorTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        server.createContext("/get", this::handleGet);
        server.createContext("/query", this::handleQuery);
        server.createContext("/headers", this::handleHeaders);
        server.createContext("/body", this::handleBody);
        server.createContext("/redirect", this::handleRedirect);
        server.createContext("/final", this::handleFinal);
        server.createContext("/error", this::handleError);
        server.createContext("/empty", this::handleEmpty);
        server.createContext("/multi-header", this::handleMultipleResponseHeaders);

        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldExecuteGetRequest() {
        JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

        HttpRequest request = new HttpRequest(
                "GET",
                baseUrl + "/get",
                Map.of(),
                Map.of(),
                null
        );

        HttpResponse response = executor.execute(request);

        assertEquals(200, response.statusCode());
        assertEquals("GET response", response.body());
        assertEquals(baseUrl + "/get", response.url());
        assertTrue(response.elapsedMilliseconds() >= 0);
        assertEquals(
                "GET response".getBytes(StandardCharsets.UTF_8).length,
                response.sizeBytes()
        );
    }

    @Test
    void shouldAppendAndEncodeQueryParameters() {
        JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

        Map<String, String> queryParameters = new LinkedHashMap<>();
        queryParameters.put("page", "2");
        queryParameters.put("search", "hello world");

        HttpRequest request = new HttpRequest(
                "GET",
                baseUrl + "/query",
                Map.of(),
                queryParameters,
                null
        );

        HttpResponse response = executor.execute(request);

        assertEquals(200, response.statusCode());
        assertEquals("page=2&search=hello+world", response.body());
    }

    @Test
    void shouldAppendQueryParametersToExistingQueryString() {
        JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

        HttpRequest request = new HttpRequest(
                "GET",
                baseUrl + "/query?existing=value",
                Map.of(),
                Map.of("page", "2"),
                null
        );

        HttpResponse response = executor.execute(request);

        assertEquals(200, response.statusCode());
        assertEquals("existing=value&page=2", response.body());
    }

    @Test
    void shouldSendRequestHeaders() {
        JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

        HttpRequest request = new HttpRequest(
                "GET",
                baseUrl + "/headers",
                Map.of("X-Callsmith-Test", "test-value"),
                Map.of(),
                null
        );

        HttpResponse response = executor.execute(request);

        assertEquals(200, response.statusCode());
        assertEquals("test-value", response.body());
    }

    @Test
    void shouldSendRequestBody() {
        JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

        String requestBody = "{\"name\":\"Callsmith\"}";

        HttpRequest request = new HttpRequest(
                "POST",
                baseUrl + "/body",
                Map.of("Content-Type", "application/json"),
                Map.of(),
                requestBody
        );

        HttpResponse response = executor.execute(request);

        assertEquals(200, response.statusCode());
        assertEquals(requestBody, response.body());
    }

    @Test
    void shouldReturnResponseHeaders() {
        JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

        HttpRequest request = new HttpRequest(
                "GET",
                baseUrl + "/get",
                Map.of(),
                Map.of(),
                null
        );

        HttpResponse response = executor.execute(request);

        assertEquals(200, response.statusCode());

        String headerValue = response.headers()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("X-Callsmith-Test"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        assertEquals("Callsmith-Test", headerValue);
    }

    @Test
    void shouldCombineMultipleResponseHeaderValues() {
        JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

        HttpRequest request = new HttpRequest(
                "GET",
                baseUrl + "/multi-header",
                Map.of(),
                Map.of(),
                null
        );

        HttpResponse response = executor.execute(request);

        String headerValue = response.headers()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("X-Callsmith-Multi"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        assertEquals("one, two", headerValue);
    }

    @Test
    void shouldFollowRedirects() {
        JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

        HttpRequest request = new HttpRequest(
                "GET",
                baseUrl + "/redirect",
                Map.of(),
                Map.of(),
                null
        );

        HttpResponse response = executor.execute(request);

        assertEquals(200, response.statusCode());
        assertEquals("redirect target", response.body());
        assertEquals(baseUrl + "/final", response.url());
    }

    @Test
    void shouldReturnClientErrorAsNormalResponse() {
        JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

        HttpRequest request = new HttpRequest(
                "GET",
                baseUrl + "/error",
                Map.of(),
                Map.of(),
                null
        );

        HttpResponse response = executor.execute(request);

        assertEquals(404, response.statusCode());
        assertEquals("not found", response.body());
    }

    @Test
    void shouldReturnServerErrorAsNormalResponse() {
        JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

        HttpRequest request = new HttpRequest(
                "GET",
                baseUrl + "/error",
                Map.of("X-Callsmith-Status", "500"),
                Map.of(),
                null
        );

        HttpResponse response = executor.execute(request);

        assertEquals(500, response.statusCode());
        assertEquals("server error", response.body());
    }

    @Test
    void shouldHandleEmptyResponseBody() {
        JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

        HttpRequest request = new HttpRequest(
                "GET",
                baseUrl + "/empty",
                Map.of(),
                Map.of(),
                null
        );

        HttpResponse response = executor.execute(request);

        assertEquals(204, response.statusCode());
        assertEquals("", response.body());
        assertEquals(0, response.sizeBytes());
    }

    @Test
    void shouldRejectInvalidUrl() {
        JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

        HttpRequest request = new HttpRequest(
                "GET",
                "not a valid url",
                Map.of(),
                Map.of(),
                null
        );

        assertThrows(
                IllegalStateException.class,
                () -> executor.execute(request)
        );
    }

    @Test
    void shouldFailForUnavailableServer() {
        JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

        HttpRequest request = new HttpRequest(
                "GET",
                "http://localhost:1/unavailable",
                Map.of(),
                Map.of(),
                null
        );

        assertThrows(
                IllegalStateException.class,
                () -> executor.execute(request)
        );
    }

    @Test
    void shouldHandleUppercaseHttpMethod() {
        JavaHttpRequestExecutor executor = new JavaHttpRequestExecutor();

        HttpRequest request = new HttpRequest(
                "get",
                baseUrl + "/get",
                Map.of(),
                Map.of(),
                null
        );

        HttpResponse response = executor.execute(request);

        assertEquals(200, response.statusCode());
        assertEquals("GET response", response.body());
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("X-Callsmith-Test", "Callsmith-Test");
        sendResponse(exchange, 200, "GET response");
    }

    private void handleQuery(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 200, exchange.getRequestURI().getRawQuery());
    }

    private void handleHeaders(HttpExchange exchange) throws IOException {
        String value = exchange.getRequestHeaders().getFirst("X-Callsmith-Test");
        sendResponse(exchange, 200, value == null ? "" : value);
    }

    private void handleBody(HttpExchange exchange) throws IOException {
        String body = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8
        );

        sendResponse(exchange, 200, body);
    }

    private void handleRedirect(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Location", "/final");
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private void handleFinal(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 200, "redirect target");
    }

    private void handleError(HttpExchange exchange) throws IOException {
        String statusHeader = exchange.getRequestHeaders().getFirst("X-Callsmith-Status");

        if ("500".equals(statusHeader)) {
            sendResponse(exchange, 500, "server error");
            return;
        }

        sendResponse(exchange, 404, "not found");
    }

    private void handleEmpty(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private void handleMultipleResponseHeaders(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("X-Callsmith-Multi", "one");
        exchange.getResponseHeaders().add("X-Callsmith-Multi", "two");
        sendResponse(exchange, 200, "multiple headers");
    }

    private void sendResponse(
            HttpExchange exchange,
            int statusCode,
            String body
    ) throws IOException {
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}