package com.mirakinetix.callsmith.application.http;

import com.mirakinetix.callsmith.application.environment.VariableResolver;
import com.mirakinetix.callsmith.domain.http.HttpFailure;
import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.HttpResponse;
import com.mirakinetix.callsmith.domain.http.RequestOptions;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HttpRequestService {
    private static final Pattern VARIABLE = Pattern.compile("\\{!\\{([^{}]+)}}|\\{\\{([^{}]+)}}");
    private final RequestExecutor requestExecutor;
    private final VariableResolver variableResolver;

    public HttpRequestService(RequestExecutor requestExecutor) {
        this(requestExecutor, null);
    }

    public HttpRequestService(RequestExecutor requestExecutor, VariableResolver variableResolver) {
        this.requestExecutor = requestExecutor;
        this.variableResolver = variableResolver;
    }

    public HttpResponse execute(HttpRequest request) {
        return execute(request, null);
    }

    public HttpResponse execute(HttpRequest request, String environmentId) {
        Map<String, String> variables = new HashMap<>();
        var resolved = resolveRequest(request, value -> expand(value, environmentId, variables));
        HttpResponse response;
        try {
            response = requestExecutor.execute(resolved);
        } catch (HttpFailure failure) {
            throw failure;
        } catch (RuntimeException exception) {
            if (!request.equals(resolved)) {
                throw new IllegalStateException("HTTP request failed after environment variable substitution.");
            }
            throw exception;
        }
        boolean queryAuth = resolved.options().authentication().type() == RequestOptions.AuthType.API_QUERY;
        return queryAuth || !Objects.equals(request.url(), resolved.url())
                || !request.queryParameters().equals(resolved.queryParameters())
                ? response.withoutUrl() : response;
    }

    private String expand(String value, String environmentId, Map<String, String> variables) {
        if (value == null) return null;
        Matcher matcher = VARIABLE.matcher(value);
        if (!matcher.find()) return value;
        StringBuilder expanded = new StringBuilder();
        do {
            String literal = matcher.group(1);
            String replacement;
            if (literal != null) {
                replacement = "{{" + literal + "}}";
            } else {
                String name = matcher.group(2).trim();
                replacement = variables.computeIfAbsent(name, key -> lookup(key, environmentId));
            }
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
        } while (matcher.find());
        return matcher.appendTail(expanded).toString();
    }

    private String lookup(String name, String environmentId) {
        if (environmentId == null || environmentId.isBlank()) {
            throw new HttpFailure(HttpFailure.Kind.VARIABLE_ENVIRONMENT);
        }
        if (variableResolver == null) throw new HttpFailure(HttpFailure.Kind.VARIABLE_UNAVAILABLE);
        Optional<String> value;
        try {
            value = variableResolver.lookup(name, environmentId);
        } catch (RuntimeException exception) {
            throw new HttpFailure(HttpFailure.Kind.VARIABLE_UNAVAILABLE);
        }
        return value.orElseThrow(() -> new HttpFailure(HttpFailure.Kind.VARIABLE_MISSING));
    }

    private static HttpRequest resolveRequest(HttpRequest request, UnaryOperator<String> resolve) {
        var options = request.options();
        var auth = options.authentication();
        if (auth.type() != RequestOptions.AuthType.NONE) {
            auth = new RequestOptions.Authentication(auth.type(), resolve.apply(auth.name()),
                    resolve.apply(auth.credential()));
        }
        var fields = options.fields().stream()
                .map(field -> new RequestOptions.Field(resolve.apply(field.name()), resolve.apply(field.value())))
                .toList();
        var upload = options.upload();
        if (upload != null) {
            String field = resolve.apply(upload.field());
            if (!Objects.equals(field, upload.field())) upload = upload.withField(field);
        }
        var resolvedOptions = new RequestOptions(options.timeout(), auth, options.format(), fields, upload);
        return new HttpRequest(request.method(), resolve.apply(request.url()), resolveMap(request.headers(), resolve),
                resolveMap(request.queryParameters(), resolve), resolve.apply(request.body()), resolvedOptions);
    }

    private static Map<String, String> resolveMap(Map<String, String> values, UnaryOperator<String> resolve) {
        Map<String, String> resolved = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String name = resolve.apply(key);
            if (resolved.containsKey(name)) {
                throw new HttpFailure(HttpFailure.Kind.INVALID);
            }
            resolved.put(name, resolve.apply(value));
        });
        return resolved;
    }
}
