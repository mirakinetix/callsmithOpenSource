package com.mirakinetix.callsmith.infrastructure.http;

import com.mirakinetix.callsmith.domain.http.HttpFailure;
import com.mirakinetix.callsmith.domain.http.HttpRequest;
import com.mirakinetix.callsmith.domain.http.RequestOptions.Field;
import com.mirakinetix.callsmith.domain.http.RequestOptions.AuthType;
import com.mirakinetix.callsmith.domain.http.RequestOptions.BodyFormat;
import com.mirakinetix.callsmith.domain.http.SelectedUpload;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.mirakinetix.callsmith.domain.http.HttpFailure.Kind.*;

record HttpRequestContent(URI uri, Map<String, String> headers, BodyPublisher body) {
    @Override public String toString() { return "HttpRequestContent"; }

    static HttpRequestContent prepare(HttpRequest request) {
        Map<String, String> headers = new LinkedHashMap<>(request.headers());
        List<Field> query = new ArrayList<>();
        request.queryParameters().forEach((name, value) -> query.add(new Field(name, value)));
        if (header(headers, "Proxy-Authorization") != null) {
            throw new HttpFailure(INVALID);
        }

        var auth = request.options().authentication();
        if (auth.type() != AuthType.NONE) {
            if (header(headers, "Authorization") != null) {
                throw new HttpFailure(AUTH_CONFLICT);
            }
            if ((auth.type() != AuthType.BASIC && auth.credential().isBlank())
                    || auth.name().contains("{{") || auth.credential().contains("{{")) {
                throw new HttpFailure(AUTH_INVALID);
            }
        }
        switch (auth.type()) {
            case BASIC -> {
                if (auth.name().isEmpty() || auth.name().contains(":")
                        || auth.name().chars().anyMatch(Character::isISOControl)
                        || auth.credential().chars().anyMatch(Character::isISOControl)) {
                    throw new HttpFailure(AUTH_INVALID);
                }
                String plain = auth.name() + ":" + auth.credential();
                headers.put("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(plain.getBytes(StandardCharsets.UTF_8)));
            }
            case BEARER -> {
                if (!auth.credential().matches("[A-Za-z0-9._~+/-]+=*")) {
                    throw new HttpFailure(AUTH_INVALID);
                }
                headers.put("Authorization", "Bearer " + auth.credential());
            }
            case API_HEADER -> {
                if (!httpToken(auth.name()) || auth.name().equalsIgnoreCase("Proxy-Authorization")) {
                    throw new HttpFailure(AUTH_INVALID);
                }
                if (header(headers, auth.name()) != null) {
                    throw new HttpFailure(AUTH_CONFLICT);
                }
                headers.put(auth.name(), auth.credential());
            }
            case API_QUERY -> {
                if (auth.name().isBlank()) {
                    throw new HttpFailure(AUTH_INVALID);
                }
                if (request.queryParameters().containsKey(auth.name())
                        || HttpUris.containsQueryName(HttpUris.initial(request.url(), List.of()), auth.name())) {
                    throw new HttpFailure(AUTH_CONFLICT);
                }
                query.add(new Field(auth.name(), auth.credential()));
            }
            case NONE -> {}
        }

        URI uri = HttpUris.initial(request.url(), query);
        if (header(headers, "Accept-Encoding") == null) {
            headers.put("Accept-Encoding", "gzip");
        }
        BodyPublisher publisher = body(request, headers);
        return new HttpRequestContent(uri, headers, publisher);
    }

    private static BodyPublisher body(HttpRequest request, Map<String, String> headers) {
        var options = request.options();
        String raw = request.body();
        boolean hasRaw = raw != null && !raw.isEmpty();
        if (options.format() != BodyFormat.MULTIPART
                && options.upload() != null) {
            throw new HttpFailure(BODY);
        }

        return switch (options.format()) {
            case RAW, JSON, TEXT, XML -> {
                if (!options.fields().isEmpty()) {
                    throw new HttpFailure(BODY);
                }
                if (hasRaw && header(headers, "Content-Type") == null) {
                    switch (options.format()) {
                        case JSON -> headers.put("Content-Type", "application/json");
                        case TEXT -> headers.put("Content-Type", "text/plain; charset=UTF-8");
                        case XML -> headers.put("Content-Type", "application/xml; charset=UTF-8");
                        default -> {}
                    }
                }
                yield hasRaw ? rawBody(raw, headers) : BodyPublishers.noBody();
            }
            case FORM -> {
                if (hasRaw) throw new HttpFailure(BODY);
                setStructuredContentType(headers, "application/x-www-form-urlencoded");
                validateFields(options.fields(), false);
                yield BodyPublishers.ofString(HttpUris.formEncode(options.fields()));
            }
            case MULTIPART -> {
                if (hasRaw) throw new HttpFailure(BODY);
                String boundary = "Callsmith-" + UUID.randomUUID();
                setStructuredContentType(headers, "multipart/form-data; boundary=" + boundary);
                yield multipart(options.fields(), options.upload(), boundary);
            }
        };
    }

    private static BodyPublisher rawBody(String value, Map<String, String> headers) {
        String key = header(headers, "Content-Type");
        String contentType = key == null ? "" : headers.get(key);
        try {
            var charset = HttpMediaType.charset(contentType, StandardCharsets.UTF_8);
            var buffer = charset.newEncoder().encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return BodyPublishers.ofByteArray(bytes);
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            throw new HttpFailure(BODY);
        }
    }

    private static BodyPublisher multipart(List<Field> fields,
            SelectedUpload upload, String boundary) {
        validateFields(fields, true);
        if (fields.isEmpty() && upload == null) {
            throw new HttpFailure(BODY);
        }
        List<BodyPublisher> parts = new ArrayList<>();
        for (Field field : fields) {
            String prefix = "--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                    + field.name() + "\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n";
            parts.add(BodyPublishers.ofString(prefix + field.value() + "\r\n"));
        }
        if (upload != null) {
            if (!validDisposition(upload.field()) || !validDisposition(upload.filename())) {
                throw new HttpFailure(BODY);
            }
            try {
                upload.verify();
            } catch (IOException exception) {
                throw new HttpFailure(UPLOAD);
            }
            parts.add(BodyPublishers.ofString("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                    + upload.field() + "\"; filename=\"" + upload.filename()
                    + "\"\r\nContent-Type: application/octet-stream\r\n\r\n"));
            BodyPublisher stream = BodyPublishers.ofInputStream(() -> {
                try {
                    return upload.open();
                } catch (IOException exception) {
                    throw new HttpFailure(UPLOAD);
                }
            });
            parts.add(upload.size() == 0 ? stream : BodyPublishers.fromPublisher(stream, upload.size()));
            parts.add(BodyPublishers.ofString("\r\n"));
        }
        parts.add(BodyPublishers.ofString("--" + boundary + "--\r\n"));
        return BodyPublishers.concat(parts.toArray(BodyPublisher[]::new));
    }

    private static void validateFields(List<Field> fields, boolean multipart) {
        for (Field field : fields) {
            if (field.name() == null || field.name().isBlank() || field.value() == null
                    || (multipart && !validDisposition(field.name()))) {
                throw new HttpFailure(BODY);
            }
        }
    }

    private static boolean validDisposition(String value) {
        return !value.isBlank() && value.chars().noneMatch(c -> c == '"' || c == '\\' || Character.isISOControl(c));
    }

    private static void setStructuredContentType(Map<String, String> headers, String value) {
        if (header(headers, "Content-Type") != null) {
            throw new HttpFailure(BODY);
        }
        headers.put("Content-Type", value);
    }

    private static boolean httpToken(String value) {
        return value.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    }

    private static String header(Map<String, String> headers, String name) {
        return headers.keySet().stream().filter(key -> name.equalsIgnoreCase(key)).findFirst().orElse(null);
    }
}
