package com.mirakinetix.callsmith.application.http;

import com.mirakinetix.callsmith.application.environment.VariableResolver;
import com.mirakinetix.callsmith.domain.http.HttpFailure;
import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        var request = new HttpRequest("GET", "https://api.example.com/users", Map.of(), Map.of(), null);

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
    void hidesExpandedUrlAndTransportDiagnostics() {
        String fictional = "fixture-credential";
        var request = new HttpRequest("GET", "https://example.test/{{key}}", Map.of(), Map.of(), null);
        var response = new HttpResponse(200, "OK", Map.of(), fictional, 10, 20,
                "https://example.test/" + fictional);
        when(variableResolver.lookup("key", "env-1")).thenReturn(Optional.of(fictional));
        when(requestExecutor.execute(any(HttpRequest.class))).thenReturn(response)
                .thenThrow(new IllegalArgumentException("Failed at " + response.url()));

        var result = service.execute(request, "env-1");
        org.junit.jupiter.api.Assertions.assertNull(result.url());
        assertEquals(fictional, result.body());
        org.junit.jupiter.api.Assertions.assertFalse(response.toString().contains(fictional));
        var expanded = new HttpRequest("GET", response.url(), Map.of("Authorization", fictional),
                Map.of(), fictional);
        org.junit.jupiter.api.Assertions.assertFalse(expanded.toString().contains(fictional));
        var failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.execute(request, "env-1"));
        org.junit.jupiter.api.Assertions.assertNull(failure.getCause());
        org.junit.jupiter.api.Assertions.assertFalse(failure.getMessage().contains(fictional));
    }

    @Test
    void shouldResolveUrlBeforeExecution() {
        HttpRequest request =
                createRequest();

        when(variableResolver.lookup("host", "env-1")).thenReturn(Optional.of("api.example.com"));
        when(variableResolver.lookup("userId", "env-1")).thenReturn(Optional.of("123"));

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

        when(variableResolver.lookup("host", "env-1")).thenReturn(Optional.of("api.example.com"));
        when(variableResolver.lookup("token", "env-1")).thenReturn(Optional.of("secret-token"));
        when(variableResolver.lookup("userId", "env-1")).thenReturn(Optional.of("123"));
        when(variableResolver.lookup("name", "env-1")).thenReturn(Optional.of("Devdutt"));

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
    void rejectsReferencesWhenEnvironmentIdIsBlank() {
        var failure = assertThrows(HttpFailure.class, () -> service.execute(createRequest(), " "));
        assertEquals(HttpFailure.Kind.VARIABLE_ENVIRONMENT, failure.kind());
        verifyNoInteractions(requestExecutor, variableResolver);
    }

    @Test
    void rejectsReferencesWhenVariableResolverIsUnavailable() {
        var serviceWithoutResolver = new HttpRequestService(requestExecutor);
        var failure = assertThrows(HttpFailure.class, () -> serviceWithoutResolver.execute(createRequest(), "env-1"));
        assertEquals(HttpFailure.Kind.VARIABLE_UNAVAILABLE, failure.kind());
        verifyNoInteractions(requestExecutor);
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