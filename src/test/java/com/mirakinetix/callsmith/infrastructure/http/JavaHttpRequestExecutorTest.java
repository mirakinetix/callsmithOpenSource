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