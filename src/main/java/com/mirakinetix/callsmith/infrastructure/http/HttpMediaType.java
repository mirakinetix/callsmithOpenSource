package com.mirakinetix.callsmith.infrastructure.http;

import java.nio.charset.Charset;
import java.util.regex.Pattern;

final class HttpMediaType {
    private static final Pattern CHARSET = Pattern.compile(
            "(?i)(?:^|;)\\s*charset\\s*=\\s*(?:\"([^\"]+)\"|([^;\\s]+))");

    private HttpMediaType() {}

    static Charset charset(String contentType, Charset fallback) {
        var parameter = CHARSET.matcher(contentType);
        return parameter.find()
                ? Charset.forName(parameter.group(1) == null ? parameter.group(2) : parameter.group(1))
                : fallback;
    }
}
