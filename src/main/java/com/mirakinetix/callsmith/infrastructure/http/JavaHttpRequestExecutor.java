package com.mirakinetix.callsmith.infrastructure.http;

import com.mirakinetix.callsmith.application.http.RequestExecutor;
import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.HttpResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public final class JavaHttpRequestExecutor implements RequestExecutor {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;

    public JavaHttpRequestExecutor() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public HttpResponse execute(HttpRequest request) {
        long startTime = System.nanoTime();

        try {
            java.net.http.HttpRequest httpRequest = buildHttpRequest(request);

            java.net.http.HttpResponse<String> response =
                    httpClient.send(httpRequest, BodyHandlers.ofString());

            long elapsedMilliseconds =
                    Duration.ofNanos(System.nanoTime() - startTime).toMillis();

            String responseBody = response.body();

            Map<String, String> responseHeaders = response.headers()
                    .map()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> String.join(", ", entry.getValue())
                    ));

            long sizeBytes = responseBody == null
                    ? 0
                    : responseBody.getBytes(StandardCharsets.UTF_8).length;

            return new HttpResponse(
                    response.statusCode(),
                    null,
                    responseHeaders,
                    responseBody,
                    elapsedMilliseconds,
                    sizeBytes,
                    response.uri().toString()
            );

        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid HTTP request", e);
        } catch (IOException e) {
            throw new IllegalStateException("HTTP request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request was interrupted", e);
        }
    }

    private java.net.http.HttpRequest buildHttpRequest(HttpRequest request) {
        URI uri = buildUri(request);

        java.net.http.HttpRequest.Builder builder =
                java.net.http.HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(REQUEST_TIMEOUT);

        request.headers().forEach(builder::header);

        String method = request.method().toUpperCase();
        String body = request.body();

        if (body == null || body.isEmpty()) {
            builder.method(method, BodyPublishers.noBody());
        } else {
            builder.method(method, BodyPublishers.ofString(body));
        }

        return builder.build();
    }

    private URI buildUri(HttpRequest request) {
        URI baseUri = URI.create(request.url());

        if (request.queryParameters() == null || request.queryParameters().isEmpty()) {
            return baseUri;
        }

        String queryString = request.queryParameters()
                .entrySet()
                .stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));

        String separator = baseUri.getQuery() == null ? "?" : "&";

        return URI.create(baseUri + separator + queryString);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}