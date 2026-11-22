package com.mirakinetix.callsmith.infrastructure.collection.importation;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OpenApiServer {
    private static final URI DEFAULT_SERVER = URI.create("/");
    private static final Pattern VARIABLE = Pattern.compile("\\{([^{}]+)}");

    private OpenApiServer() {}

    static URI effective(JsonNode root, JsonNode path, JsonNode operation) {
        if (operation.has("servers")) return first(operation.get("servers"), "operation");
        if (path.has("servers")) return first(path.get("servers"), "path");
        if (root.has("servers")) return first(root.get("servers"), "root");
        return DEFAULT_SERVER;
    }

    private static URI first(JsonNode servers, String scope) {
        if (!servers.isArray()) throw invalid(scope, "servers must be an array");
        if (servers.isEmpty()) return DEFAULT_SERVER;

        JsonNode server = servers.get(0);
        var url = server.path("url");
        if (!server.isObject() || !url.isTextual() || url.asText().isBlank()) {
            throw invalid(scope, "the first server must specify a URL");
        }

        String expanded = expand(url.asText(), server.path("variables"), scope);
        URI uri;
        try {
            uri = URI.create(expanded);
        } catch (IllegalArgumentException exception) {
            throw invalid(scope, "the URL is malformed");
        }
        if (uri.getRawFragment() != null || uri.getRawUserInfo() != null) {
            throw invalid(scope, "URLs cannot contain fragments or user information");
        }
        if (uri.isAbsolute()) {
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawAuthority().endsWith(":")
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw invalid(scope, "use an HTTP(S) URL with a valid host and port");
            }
        } else if (expanded.startsWith("//") || uri.getRawPath().isEmpty()) {
            throw invalid(scope, "use an absolute HTTP(S) URL or a relative path");
        }
        return uri;
    }

    private static String expand(String url, JsonNode variables, String scope) {
        if (!variables.isMissingNode() && !variables.isObject()) {
            throw invalid(scope, "variables must be an object");
        }
        Matcher matcher = VARIABLE.matcher(url);
        StringBuilder expanded = new StringBuilder();
        while (matcher.find()) {
            JsonNode variable = variables.path(matcher.group(1));
            JsonNode value = variable.path("default");
            if (!value.isTextual()) throw invalid(scope, "a URL variable has no string default");
            JsonNode allowed = variable.path("enum");
            if (!allowed.isMissingNode()) {
                if (!allowed.isArray() || allowed.valueStream().anyMatch(entry -> !entry.isTextual())) {
                    throw invalid(scope, "a URL variable enum must be an array of strings");
                }
                if (allowed.valueStream().noneMatch(value::equals)) {
                    throw invalid(scope, "a URL variable default is outside its allowed values");
                }
            }
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(value.asText()));
        }
        return matcher.appendTail(expanded).toString();
    }

    static String requestUrl(URI server, String path) {
        if (!path.startsWith("/") || path.contains("?") || path.contains("#")) {
            throw new IllegalArgumentException("Invalid OpenAPI operation path. Use a path starting with /.");
        }
        String base = server.toString();
        String query = "";
        if (server.getRawQuery() != null) {
            int separator = base.indexOf('?');
            query = base.substring(separator);
            base = base.substring(0, separator);
        }
        // The endpoint is appended to the server's path without changing its authority or query.
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + path + query;
    }

    private static IllegalArgumentException invalid(String scope, String reason) {
        return new IllegalArgumentException("Invalid OpenAPI " + scope + " server: " + reason + ".");
    }
}
