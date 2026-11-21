package com.mirakinetix.callsmith.infrastructure.http;

import com.mirakinetix.callsmith.application.http.RequestExecutor;
import com.mirakinetix.callsmith.domain.http.HttpFailure;
import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.HttpResponse;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.mirakinetix.callsmith.domain.http.HttpFailure.Kind.*;

public final class JavaHttpRequestExecutor implements RequestExecutor {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_REDIRECTS = 5;

    private final HttpClient httpClient;

    public JavaHttpRequestExecutor() {
        this(HttpClient.newBuilder());
    }

    JavaHttpRequestExecutor(SSLContext sslContext) {
        this(HttpClient.newBuilder().sslContext(sslContext));
    }

    private JavaHttpRequestExecutor(HttpClient.Builder builder) {
        httpClient = builder.connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public HttpResponse execute(HttpRequest request) {
        long start = System.nanoTime();
        long deadline = start + request.options().timeout().toNanos();
        try {
            return exchange(request, start, deadline);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new HttpFailure(INVALID);
        }
    }

    private HttpResponse exchange(HttpRequest request, long start, long deadline) {
        var content = HttpRequestContent.prepare(request);
        var uri = content.uri();
        String method = request.method().toUpperCase(Locale.ROOT);
        var headers = content.headers();
        var body = content.body();
        Set<Hop> visited = new HashSet<>();
        for (int hops = 0; ; hops++) {
            if (!visited.add(new Hop(method, uri.normalize()))) {
                throw new HttpFailure(LOOP);
            }
            var builder = java.net.http.HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofNanos(remaining(deadline))).method(method, body);
            headers.forEach(builder::header);
            var response = send(builder.build(), deadline);
            int status = response.statusCode();
            var locations = response.headers().allValues("Location");
            if (!(status == 301 || status == 302 || status == 303 || status == 307 || status == 308)
                    || locations.isEmpty()) {
                return result(response, start, hops != 0);
            }

            if (hops == MAX_REDIRECTS) {
                throw new HttpFailure(REDIRECT_LIMIT);
            }
            uri = HttpUris.redirect(uri, locations);
            if (status == 303 || ((status == 301 || status == 302) && method.equals("POST"))) {
                if (!method.equals("HEAD")) {
                    method = "GET";
                }
                body = BodyPublishers.noBody();
                headers.keySet().removeIf(name -> name.toLowerCase(Locale.ROOT).startsWith("content-")
                        || name.equalsIgnoreCase("Digest") || name.equalsIgnoreCase("Repr-Digest"));
            }
        }
    }

    private java.net.http.HttpResponse<byte[]> send(java.net.http.HttpRequest request, long deadline) {
        if (Thread.currentThread().isInterrupted()) {
            throw new HttpFailure(INTERRUPTED);
        }
        var exchange = httpClient.sendAsync(request, info -> ResponseContent.boundedSubscriber());
        try {
            return exchange.get(remaining(deadline), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            exchange.cancel(true);
            Thread.currentThread().interrupt();
            throw new HttpFailure(INTERRUPTED);
        } catch (TimeoutException | HttpFailure exception) {
            exchange.cancel(true);
            throw new HttpFailure(TIMEOUT);
        } catch (ExecutionException exception) {
            throw safeFailure(exception);
        }
    }

    private static HttpFailure safeFailure(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpFailure failure) return new HttpFailure(failure.kind());
            if (cause instanceof HttpTimeoutException) return new HttpFailure(TIMEOUT);
            if (cause instanceof SSLException) return new HttpFailure(TLS);
            if (cause instanceof ConnectException || cause instanceof UnknownHostException
                    || cause instanceof UnresolvedAddressException) return new HttpFailure(CONNECT);
        }
        return new HttpFailure(NETWORK);
    }

    private static long remaining(long deadline) {
        long duration = deadline - System.nanoTime();
        if (duration <= 0) {
            throw new HttpFailure(TIMEOUT);
        }
        return duration;
    }

    private static HttpResponse result(java.net.http.HttpResponse<byte[]> response, long start, boolean redirected) {
        byte[] payload = response.body();
        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().map().forEach((key, values) -> headers.put(key,
                key.equalsIgnoreCase("location") || key.equalsIgnoreCase("content-location")
                        ? "[hidden]" : String.join(", ", values)));
        String text = ResponseContent.text(response.headers(), payload);
        return new HttpResponse(response.statusCode(), null, headers, text,
                Duration.ofNanos(System.nanoTime() - start).toMillis(), payload.length,
                HttpUris.displayUrl(response.uri(), redirected), payload);
    }

    private record Hop(String method, URI uri) {}
}
