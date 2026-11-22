package com.mirakinetix.callsmith.ui.response;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirakinetix.callsmith.domain.http.HttpResponse;

import java.util.Locale;

public record ResponsePreview(String raw, String formatted) {
    private static final int MAX_PREVIEW = 256 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Override public String toString() { return "ResponsePreview"; }

    public static ResponsePreview prepare(HttpResponse response) {
        String raw = response.body();
        if (response.binary()) {
            return new ResponsePreview("Binary or unsupported encoding. Use Save Response Bytes to keep the original.", null);
        }
        if (raw == null || raw.isEmpty()) {
            return new ResponsePreview("No response body.", null);
        }
        if (raw.length() > MAX_PREVIEW) {
            int end = MAX_PREVIEW;
            if (Character.isHighSurrogate(raw.charAt(end - 1))) end--;
            return new ResponsePreview(raw.substring(0, end)
                    + "\n\n[Preview truncated. Save Response Bytes for the full payload.]", null);
        }

        String mime = response.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("Content-Type"))
                .map(entry -> entry.getValue().split(";", 2)[0].strip().toLowerCase(Locale.ROOT))
                .findFirst().orElse("");
        if (mime.equals("application/json") || mime.endsWith("+json")) {
            try {
                String pretty = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(raw));
                if (pretty.length() <= MAX_PREVIEW) {
                    return new ResponsePreview(raw, pretty);
                }
            } catch (JsonProcessingException ignored) {
                // Malformed JSON and duplicate keys can still be inspected as raw text.
            }
        }
        return new ResponsePreview(raw, null);
    }
}
