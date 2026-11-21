package com.mirakinetix.callsmith.application.testing;

import com.mirakinetix.callsmith.domain.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.mirakinetix.callsmith.application.testing.ResponseAssertion.Type.*;
import static org.junit.jupiter.api.Assertions.*;

class AssertionEvaluatorTest {
    private final AssertionEvaluator simple = new AssertionEvaluator(null);

    @Test
    void evaluatesStatusRangeAndStrictDuration() {
        var results = simple.evaluate(response(204, "", 48), List.of(
                spec(STATUS_EQUALS, "", "204"), spec(STATUS_EQUALS, "", "200"),
                spec(STATUS_RANGE, "200", "204"), spec(DURATION_BELOW, "", "49"),
                spec(DURATION_BELOW, "", "48")), null);
        assertEquals(List.of(true, false, true, true, false), results.stream().map(AssertionEvaluator.Result::passed).toList());
        assertTrue(results.get(1).detail().contains("received 204"));
        assertTrue(results.getLast().detail().contains("received 48 ms"));
    }

    @Test
    void matchesHeadersCaseInsensitivelyAndReportsMissingAndTextMismatch() {
        var value = new HttpResponse(200, null, Map.of("x-mode", "demo"), "a demo body", 5, 11, null);
        var results = simple.evaluate(value, List.of(
                spec(HEADER_EXISTS, "X-Mode", ""), spec(HEADER_EQUALS, "X-MODE", "demo"),
                spec(HEADER_EXISTS, "X-Missing", ""), spec(HEADER_EQUALS, "X-Mode", "other"),
                spec(BODY_CONTAINS, "", "demo"), spec(BODY_EXCLUDES, "", "absent"),
                spec(BODY_EXCLUDES, "", "body")), null);
        assertEquals(List.of(true, true, false, false, true, true, false),
                results.stream().map(AssertionEvaluator.Result::passed).toList());
        assertTrue(results.get(2).detail().contains("missing"));
        assertTrue(results.getLast().detail().contains("was present"));
    }

    @Test
    void traversesJsonPointersAndDistinguishesMissingNullAndTypes() {
        var value = response(200, "{\"items\":[{\"a/b\":null,\"quantity\":3}]}", 1);
        var results = simple.evaluate(value, List.of(
                spec(JSON_EXISTS, "/items/0/a~1b", ""), spec(JSON_EQUALS, "/items/0/a~1b", "null"),
                spec(JSON_EXISTS, "/items/1", ""), spec(JSON_EQUALS, "/items/0/quantity", "3"),
                spec(JSON_EQUALS, "/items/0/quantity", "\"3\"")), null);
        assertEquals(List.of(true, true, false, true, false), results.stream().map(AssertionEvaluator.Result::passed).toList());
        assertTrue(results.get(2).detail().contains("missing"));
    }

    @Test
    void rejectsMalformedRulesJsonAndBinaryWithoutDisclosingInput() {
        var invalid = simple.evaluate(response(200, "{\"fixture_token\":", 2), List.of(
                spec(JSON_EXISTS, "/field", ""), spec(JSON_EQUALS, "/field", "invalid_fixture_secret"),
                spec(STATUS_RANGE, "299", "200"), spec(DURATION_BELOW, "", "0")), null);
        assertTrue(invalid.stream().noneMatch(AssertionEvaluator.Result::passed));
        assertTrue(invalid.getFirst().detail().contains("invalid"));
        assertTrue(invalid.get(1).detail().contains("Invalid configuration"));
        assertFalse(invalid.toString().contains("fixture"));
        var binary = new HttpResponse(200, null, Map.of(), null, 0, 1, null, new byte[]{0});
        var results = simple.evaluate(binary, List.of(spec(BODY_EXCLUDES, "", "nothing"),
                spec(JSON_EXISTS, "/field", "")), null);
        assertTrue(results.stream().noneMatch(AssertionEvaluator.Result::passed));
        assertTrue(results.getLast().detail().contains("binary"));
    }

    @Test
    void reportsProtectedComparisonWithoutLeakingExpectedActualOrSelector() {
        var evaluator = new AssertionEvaluator((value, environment) -> "fictional-expected-secret");
        var response = new HttpResponse(200, null, Map.of("x-test", "fictional-actual-secret"),
                "{\"private_field\":\"fictional-expected-secret\"}", 1, 41, "https://example.test?secret=fixture");
        var checks = List.of(spec(HEADER_EQUALS, "x-test", "{{protected_value}}"),
                spec(JSON_EQUALS, "/private_field", "{{protected_value}}"));
        var result = evaluator.evaluate(response, checks, "fixture-env");
        assertFalse(result.getFirst().passed());
        assertTrue(result.getLast().passed());
        var text = result.toString();
        for (String hidden : List.of("x-test", "protected_value", "private_field", "fictional", "example.test")) {
            assertFalse(text.contains(hidden));
        }
        assertTrue(simple.evaluate(response, checks, null).stream().noneMatch(AssertionEvaluator.Result::passed));
        assertTrue(evaluator.noResponse(checks).stream().noneMatch(AssertionEvaluator.Result::passed));
        assertThrows(IllegalArgumentException.class,
                () -> spec(HEADER_EQUALS, "Set-Cookie", "fictional-literal").validate());
        assertDoesNotThrow(() -> spec(HEADER_EQUALS, "Set-Cookie", "{{protected_value}}").validate());
    }

    private static ResponseAssertion spec(ResponseAssertion.Type type, String target, String expected) {
        return new ResponseAssertion(type, target, expected);
    }

    private static HttpResponse response(int code, String body, long duration) {
        return new HttpResponse(code, null, Map.of(), body, duration, body.length(), null);
    }
}
