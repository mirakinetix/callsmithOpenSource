package com.mirakinetix.callsmith.application.http;

import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.HttpResponse;
import com.mirakinetix.callsmith.infrastructure.http.JavaHttpRequestExecutor;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestServiceIntegrationTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress("localhost", 0),
                0
        );

        baseUrl = "http://localhost:" + server.getAddress().getPort();

        server.createContext("/integration", this::handleIntegrationRequest);

        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldExecuteRequestThroughApplicationService() {
        JavaHttpRequestExecutor requestExecutor =
                new JavaHttpRequestExecutor();

        HttpRequestService service =
                new HttpRequestService(requestExecutor);

        HttpRequest request = new HttpRequest(
                "GET",
                baseUrl + "/integration",
                Map.of(),
                Map.of(),
                null
        );

        HttpResponse response = service.execute(request);

        assertEquals(200, response.statusCode());
        assertEquals("Callsmith integration response", response.body());
        assertEquals(baseUrl + "/integration", response.url());
    }

    private void handleIntegrationRequest(HttpExchange exchange)
            throws IOException {

        sendResponse(
                exchange,
                200,
                "Callsmith integration response"
        );
    }

    private void sendResponse(
            HttpExchange exchange,
            int statusCode,
            String body
    ) throws IOException {

        byte[] responseBytes =
                body.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(
                statusCode,
                responseBytes.length
        );

        try (OutputStream outputStream =
                     exchange.getResponseBody()) {

            outputStream.write(responseBytes);
        }
    }
}