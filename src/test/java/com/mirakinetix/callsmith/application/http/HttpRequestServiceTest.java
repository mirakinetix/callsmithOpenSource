package com.mirakinetix.callsmith.application.http;

import com.mirakinetix.callsmith.application.environment.VariableResolver;
import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpRequestServiceTest {

    private final RequestExecutor requestExecutor =
            mock(RequestExecutor.class);

    private final VariableResolver variableResolver =
            mock(VariableResolver.class);

    private final HttpRequestService service =
            new HttpRequestService(
                    requestExecutor,
                    variableResolver
            );

    @Test
    void shouldExecuteRequestWithoutEnvironment() {
        HttpRequest request =
                createRequest();

        HttpResponse response =
                createResponse();

        when(requestExecutor.execute(request))
                .thenReturn(response);

        HttpResponse result =
                service.execute(request);

        assertEquals(
                response,
                result
        );

        verify(requestExecutor)
                .execute(request);
    }

    @Test
    void shouldResolveUrlBeforeExecution() {
        HttpRequest request =
                createRequest();

        when(variableResolver.resolve(
                eq("https://{{host}}/users/{{userId}}"),
                eq("env-1")
        )).thenReturn(
                "https://api.example.com/users/123"
        );

        when(variableResolver.resolve(
                eq("X-Test"),
                eq("env-1")
        )).thenReturn(
                "X-Test"
        );

        when(variableResolver.resolve(
                eq("request-value"),
                eq("env-1")
        )).thenReturn(
                "request-value"
        );

        when(requestExecutor.execute(
                any(HttpRequest.class)
        )).thenReturn(
                createResponse()
        );

        service.execute(
                request,
                "env-1"
        );

        verify(requestExecutor).execute(
                new HttpRequest(
                        "GET",
                        "https://api.example.com/users/123",
                        request.headers(),
                        request.queryParameters(),
                        request.body()
                )
        );
    }

    @Test
    void shouldResolveHeadersQueryParametersAndBody() {
        Map<String, String> headers =
                new LinkedHashMap<>();

        headers.put(
                "Authorization",
                "Bearer {{token}}"
        );

        Map<String, String> queryParameters =
                new LinkedHashMap<>();

        queryParameters.put(
                "user",
                "{{userId}}"
        );

        HttpRequest request =
                new HttpRequest(
                        "POST",
                        "https://{{host}}/users",
                        headers,
                        queryParameters,
                        "{\"name\":\"{{name}}\"}"
                );

        when(variableResolver.resolve(
                eq("https://{{host}}/users"),
                eq("env-1")
        )).thenReturn(
                "https://api.example.com/users"
        );

        when(variableResolver.resolve(
                eq("Authorization"),
                eq("env-1")
        )).thenReturn(
                "Authorization"
        );

        when(variableResolver.resolve(
                eq("Bearer {{token}}"),
                eq("env-1")
        )).thenReturn(
                "Bearer secret-token"
        );

        when(variableResolver.resolve(
                eq("user"),
                eq("env-1")
        )).thenReturn(
                "user"
        );

        when(variableResolver.resolve(
                eq("{{userId}}"),
                eq("env-1")
        )).thenReturn(
                "123"
        );

        when(variableResolver.resolve(
                eq("{\"name\":\"{{name}}\"}"),
                eq("env-1")
        )).thenReturn(
                "{\"name\":\"Devdutt\"}"
        );

        when(requestExecutor.execute(
                any(HttpRequest.class)
        )).thenReturn(
                createResponse()
        );

        service.execute(
                request,
                "env-1"
        );

        Map<String, String> expectedHeaders =
                new LinkedHashMap<>();

        expectedHeaders.put(
                "Authorization",
                "Bearer secret-token"
        );

        Map<String, String> expectedQueryParameters =
                new LinkedHashMap<>();

        expectedQueryParameters.put(
                "user",
                "123"
        );

        verify(requestExecutor).execute(
                new HttpRequest(
                        "POST",
                        "https://api.example.com/users",
                        expectedHeaders,
                        expectedQueryParameters,
                        "{\"name\":\"Devdutt\"}"
                )
        );
    }

    @Test
    void shouldExecuteOriginalRequestWhenEnvironmentIdIsBlank() {
        HttpRequest request =
                createRequest();

        when(requestExecutor.execute(request))
                .thenReturn(createResponse());

        service.execute(
                request,
                " "
        );

        verify(requestExecutor)
                .execute(request);
    }

    @Test
    void shouldExecuteOriginalRequestWhenVariableResolverIsUnavailable() {
        HttpRequest request =
                createRequest();

        HttpRequestService serviceWithoutResolver =
                new HttpRequestService(
                        requestExecutor
                );

        when(requestExecutor.execute(request))
                .thenReturn(createResponse());

        serviceWithoutResolver.execute(
                request,
                "env-1"
        );

        verify(requestExecutor)
                .execute(request);
    }

    private HttpRequest createRequest() {
        return new HttpRequest(
                "GET",
                "https://{{host}}/users/{{userId}}",
                Map.of(
                        "X-Test",
                        "request-value"
                ),
                Map.of(),
                null
        );
    }

    private HttpResponse createResponse() {
        return new HttpResponse(
                200,
                null,
                Map.of(),
                "{}",
                10,
                2,
                "https://api.example.com"
        );
    }
}