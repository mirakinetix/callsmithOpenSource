package com.mirakinetix.callsmith.application.http;

import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.HttpResponse;

public final class HttpRequestService {

    private final RequestExecutor requestExecutor;

    public HttpRequestService(RequestExecutor requestExecutor) {
        this.requestExecutor = requestExecutor;
    }

    public HttpResponse execute(HttpRequest request) {
        return requestExecutor.execute(request);
    }
}