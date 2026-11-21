package com.mirakinetix.callsmith.application.http;

import com.mirakinetix.callsmith.application.environment.VariableResolver;
import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.HttpResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class HttpRequestService {

    private final RequestExecutor requestExecutor;
    private final VariableResolver variableResolver;

    public HttpRequestService(
            RequestExecutor requestExecutor
    ) {
        this(
                requestExecutor,
                null
        );
    }

    public HttpRequestService(
            RequestExecutor requestExecutor,
            VariableResolver variableResolver
    ) {
        this.requestExecutor = requestExecutor;
        this.variableResolver = variableResolver;
    }

    public HttpResponse execute(
            HttpRequest request
    ) {
        return requestExecutor.execute(request);
    }

    public HttpResponse execute(
            HttpRequest request,
            String environmentId
    ) {
        if (variableResolver == null
                || environmentId == null
                || environmentId.isBlank()) {
            return requestExecutor.execute(request);
        }

        HttpRequest resolvedRequest =
                resolveRequest(
                        request,
                        environmentId
                );

        HttpResponse response;
        try {
            response = requestExecutor.execute(resolvedRequest);
        } catch (RuntimeException exception) {
            if (!request.equals(resolvedRequest)) {
                // Transport exceptions can embed an expanded URI or header.
                throw new IllegalStateException("HTTP request failed after environment variable substitution.");
            }
            throw exception;
        }
        if (!Objects.equals(request.url(), resolvedRequest.url())
                || !Objects.equals(request.queryParameters(), resolvedRequest.queryParameters())) {
            return new HttpResponse(response.statusCode(), response.reasonPhrase(), response.headers(),
                    response.body(), response.elapsedMilliseconds(), response.sizeBytes(), null);
        }
        return response;
    }

    private HttpRequest resolveRequest(
            HttpRequest request,
            String environmentId
    ) {
        String resolvedUrl =
                variableResolver.resolve(
                        request.url(),
                        environmentId
                );

        Map<String, String> resolvedHeaders =
                resolveMap(
                        request.headers(),
                        environmentId
                );

        Map<String, String> resolvedQueryParameters =
                resolveMap(
                        request.queryParameters(),
                        environmentId
                );

        String resolvedBody =
                variableResolver.resolve(
                        request.body(),
                        environmentId
                );

        return new HttpRequest(
                request.method(),
                resolvedUrl,
                resolvedHeaders,
                resolvedQueryParameters,
                resolvedBody
        );
    }

    private Map<String, String> resolveMap(
            Map<String, String> values,
            String environmentId
    ) {
        if (values == null || values.isEmpty()) {
            return values;
        }

        Map<String, String> resolvedValues =
                new LinkedHashMap<>();

        values.forEach(
                (key, value) ->
                        resolvedValues.put(
                                variableResolver.resolve(
                                        key,
                                        environmentId
                                ),
                                variableResolver.resolve(
                                        value,
                                        environmentId
                                )
                        )
        );

        return resolvedValues;
    }
}