package com.mirakinetix.callsmith.application.testing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mirakinetix.callsmith.application.environment.VariableResolver;
import com.mirakinetix.callsmith.domain.http.HttpResponse;

import java.util.ArrayList;
import java.util.List;

public final class AssertionEvaluator {
    private final VariableResolver resolver;

    public AssertionEvaluator(VariableResolver resolver) {
        this.resolver = resolver;
    }

    public List<Result> evaluate(HttpResponse response, List<ResponseAssertion> definitions, String environmentId) {
        if (definitions.size() > ResponseAssertion.MAX_PER_REQUEST) {
            throw new IllegalArgumentException("A request can have at most 32 assertions.");
        }
        JsonNode json = definitions.stream().anyMatch(spec -> spec.type() == ResponseAssertion.Type.JSON_EXISTS
                || spec.type() == ResponseAssertion.Type.JSON_EQUALS) ? parseBody(response) : null;
        var results = new ArrayList<Result>();
        for (var spec : definitions) {
            try {
                spec.validate();
            } catch (IllegalArgumentException exception) {
                results.add(new Result(spec.type(), false, "Invalid configuration. " + exception.getMessage()));
                continue;
            }
            results.add(check(response, json, spec, environmentId));
        }
        return List.copyOf(results);
    }

    public List<Result> noResponse(List<ResponseAssertion> definitions) {
        return definitions.stream().limit(ResponseAssertion.MAX_PER_REQUEST)
                .map(spec -> result(spec, false, "Expected an HTTP response; none was available.")).toList();
    }

    private Result check(HttpResponse response, JsonNode json, ResponseAssertion spec, String environmentId) {
        var type = spec.type();
        int status = response.statusCode();
        return switch (type) {
            case STATUS_EQUALS -> result(spec, status == ResponseAssertion.status(spec.expected()),
                    "Expected status " + ResponseAssertion.status(spec.expected()) + "; received " + status + ".");
            case STATUS_RANGE -> {
                int low = ResponseAssertion.status(spec.target());
                int high = ResponseAssertion.status(spec.expected());
                yield result(spec, status >= low && status <= high,
                        "Expected status " + low + "–" + high + "; received " + status + ".");
            }
            case DURATION_BELOW -> {
                int limit = ResponseAssertion.milliseconds(spec.expected());
                yield result(spec, response.elapsedMilliseconds() < limit,
                        "Expected under " + limit + " ms; received " + response.elapsedMilliseconds() + " ms.");
            }
            case HEADER_EXISTS, HEADER_EQUALS -> {
                String actual = response.headers().entrySet().stream()
                        .filter(entry -> entry.getKey().equalsIgnoreCase(spec.target()))
                        .map(java.util.Map.Entry::getValue).findFirst().orElse(null);
                if (actual == null) yield result(spec, false, "Expected header; it was missing.");
                if (type == ResponseAssertion.Type.HEADER_EXISTS) yield result(spec, true, "Expected header was present.");
                String wanted = expected(spec, environmentId);
                yield wanted == null ? unresolved(spec) : result(spec, actual.equals(wanted),
                        actual.equals(wanted) ? "Header matched the configured value."
                                : "Expected header value differed from the received value (values hidden).");
            }
            case BODY_CONTAINS, BODY_EXCLUDES -> {
                if (response.binary() || response.body() == null) {
                    yield result(spec, false, "Expected a text body; no decoded text was available.");
                }
                String wanted = expected(spec, environmentId);
                if (wanted == null || wanted.isEmpty()) yield unresolved(spec);
                boolean present = response.body().contains(wanted);
                boolean shouldContain = type == ResponseAssertion.Type.BODY_CONTAINS;
                yield result(spec, present == shouldContain,
                        "Expected configured text to be " + (shouldContain ? "present" : "absent")
                                + "; it was " + (present ? "present." : "absent."));
            }
            case JSON_EXISTS, JSON_EQUALS -> {
                if (response.binary()) yield result(spec, false, "Expected JSON; received binary data.");
                if (json == null) yield result(spec, false, "Expected JSON; the body was missing or invalid.");
                JsonNode actual = json.at(spec.target());
                if (actual.isMissingNode()) yield result(spec, false, "Expected JSON property; it was missing.");
                if (type == ResponseAssertion.Type.JSON_EXISTS) yield result(spec, true, "Expected JSON property was present.");
                JsonNode wanted;
                if (ResponseAssertion.isReference(spec.expected())) {
                    String resolved = expected(spec, environmentId);
                    if (resolved == null) yield unresolved(spec);
                    wanted = TextNode.valueOf(resolved);
                } else {
                    wanted = ResponseAssertion.parseExpected(spec.expected());
                }
                boolean matches = actual.equals(wanted);
                yield result(spec, matches, matches ? "JSON value matched."
                        : "Expected JSON value differed from the received value (values hidden).");
            }
        };
    }

    private String expected(ResponseAssertion spec, String environmentId) {
        if (!ResponseAssertion.hasReference(spec.expected())) return spec.expected();
        if (resolver == null || environmentId == null || environmentId.isBlank()) return null;
        try {
            String value = resolver.resolve(spec.expected(), environmentId);
            return value == null || ResponseAssertion.hasReference(value) ? null : value;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static JsonNode parseBody(HttpResponse response) {
        if (response.binary() || response.body() == null) return null;
        try {
            return ResponseAssertion.JSON.readTree(response.body());
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static Result unresolved(ResponseAssertion spec) {
        return result(spec, false, "The configured comparison could not be resolved. Check the selected environment and Secret Storage.");
    }

    private static Result result(ResponseAssertion spec, boolean passed, String detail) {
        return new Result(spec.type(), passed, detail);
    }

    public record Result(ResponseAssertion.Type type, boolean passed, String detail) {
        public String reportLine() { return (passed ? "PASS " : "FAIL ") + type + " — " + detail; }
    }
}
