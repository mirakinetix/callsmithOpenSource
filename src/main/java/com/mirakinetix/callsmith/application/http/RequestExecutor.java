package com.mirakinetix.callsmith.application.http;

import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.HttpResponse;

public interface RequestExecutor {

    HttpResponse execute(HttpRequest request);
}