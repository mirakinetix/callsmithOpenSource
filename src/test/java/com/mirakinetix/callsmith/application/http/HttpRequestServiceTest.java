package com.mirakinetix.callsmith.application.http;

import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpRequestServiceTest {

    @Test
    void shouldDelegateRequestToExecutor() {
        RequestExecutor requestExecutor = mock(RequestExecutor.class);
        HttpRequestService service = new HttpRequestService(requestExecutor);

        HttpRequest request = new HttpRequest(
                "GET",
                "https://example.com/api",
                Map.of(),
                Map.of(),
                null
        );

        HttpResponse response = new HttpResponse(
                200,
                null,
                Map.of(),
                "OK",
                120,
                2,
                "https://example.com/api"
        );

        when(requestExecutor.execute(request)).thenReturn(response);

        service.execute(request);

        verify(requestExecutor).execute(request);
    }

    @Test
    void shouldReturnExecutorResponse() {
        RequestExecutor requestExecutor = mock(RequestExecutor.class);
        HttpRequestService service = new HttpRequestService(requestExecutor);

        HttpRequest request = new HttpRequest(
                "POST",
                "https://example.com/api",
                Map.of("Content-Type", "application/json"),
                Map.of(),
                "{\"name\":\"Callsmith\"}"
        );

        HttpResponse response = new HttpResponse(
                201,
                null,
                Map.of("Content-Type", "application/json"),
                "{\"id\":1}",
                150,
                8,
                "https://example.com/api"
        );

        when(requestExecutor.execute(request)).thenReturn(response);

        HttpResponse actualResponse = service.execute(request);

        assertSame(response, actualResponse);
    }

    @Test
    void shouldPropagateExecutorFailure() {
        RequestExecutor requestExecutor = mock(RequestExecutor.class);
        HttpRequestService service = new HttpRequestService(requestExecutor);

        HttpRequest request = new HttpRequest(
                "GET",
                "https://example.com/api",
                Map.of(),
                Map.of(),
                null
        );

        IllegalStateException failure =
                new IllegalStateException("HTTP request failed");

        when(requestExecutor.execute(request)).thenThrow(failure);

        IllegalStateException actualException =
                assertThrows(
                        IllegalStateException.class,
                        () -> service.execute(request)
                );

        assertSame(failure, actualException);
    }
}