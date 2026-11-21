package com.mirakinetix.callsmith.infrastructure.http;

import com.mirakinetix.callsmith.domain.http.HttpFailure;
import com.mirakinetix.callsmith.domain.http.RequestOptions.Field;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static com.mirakinetix.callsmith.domain.http.HttpFailure.Kind.*;

final class HttpUris {
    private HttpUris() {}

    static URI initial(String url, List<Field> query) {
        URI uri = validate(stripFragment(URI.create(url)));
        if (query.isEmpty()) {
            return uri;
        }
        String current = uri.getRawQuery();
        String delimiter = current == null ? "?" : current.isEmpty() || current.endsWith("&") ? "" : "&";
        return URI.create(uri + delimiter + formEncode(query));
    }

    static String formEncode(List<Field> fields) {
        return fields.stream().map(field -> encode(field.name()) + "=" + encode(field.value()))
                .collect(Collectors.joining("&"));
    }

    static boolean containsQueryName(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null) {
            return false;
        }
        for (String entry : query.split("&")) {
            int equals = entry.indexOf('=');
            String key = equals < 0 ? entry : entry.substring(0, equals);
            if (URLDecoder.decode(key, StandardCharsets.UTF_8).equals(name)) {
                return true;
            }
        }
        return false;
    }

    static URI redirect(URI current, List<String> locations) {
        if (locations.size() != 1 || locations.getFirst().isBlank()) {
            throw new HttpFailure(LOCATION);
        }
        URI next;
        try {
            URI reference = URI.create(locations.getFirst());
            // URI.resolve treats a query-only reference as relative to the parent path.
            if (reference.getScheme() == null && reference.getRawAuthority() == null
                    && "".equals(reference.getRawPath())) {
                String base = withoutQuery(current);
                next = reference.getRawQuery() == null ? current : URI.create(base + "?" + reference.getRawQuery());
            } else {
                next = current.resolve(reference);
            }
            next = validate(stripFragment(next));
        } catch (IllegalArgumentException | HttpFailure exception) {
            throw new HttpFailure(LOCATION);
        }
        if ("https".equalsIgnoreCase(current.getScheme()) && "http".equalsIgnoreCase(next.getScheme())) {
            throw new HttpFailure(DOWNGRADE);
        }
        if (!current.getScheme().equalsIgnoreCase(next.getScheme())
                || !current.getHost().equalsIgnoreCase(next.getHost()) || port(current) != port(next)) {
            throw new HttpFailure(ORIGIN);
        }
        return next;
    }

    static String displayUrl(URI uri, boolean redirected) {
        return redirected ? uri.getScheme() + "://" + uri.getRawAuthority() : withoutQuery(uri);
    }

    private static URI stripFragment(URI uri) {
        String text = uri.toString();
        int index = text.indexOf('#');
        return index < 0 ? uri : URI.create(text.substring(0, index));
    }

    private static String withoutQuery(URI uri) {
        String raw = uri.toString();
        int separator = raw.indexOf('?');
        return separator < 0 ? raw : raw.substring(0, separator);
    }

    private static int port(URI uri) {
        return uri.getPort() != -1 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static URI validate(URI uri) {
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getPort() == 0 || uri.getPort() > 65535
                || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new HttpFailure(INVALID);
        }
        return uri;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
