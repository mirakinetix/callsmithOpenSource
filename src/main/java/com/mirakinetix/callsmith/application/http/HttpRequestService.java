package com.mirakinetix.callsmith.application.http;

import com.mirakinetix.callsmith.application.environment.VariableResolver;
import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.HttpResponse;

import java.util.LinkedHashMap;
import java.util.Map;

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

        return requestExecutor.execute(
                resolvedRequest
        );
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