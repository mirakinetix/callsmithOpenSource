package com.mirakinetix.callsmith.infrastructure.http;

import com.mirakinetix.callsmith.domain.http.HttpFailure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.zip.GZIPInputStream;

import static com.mirakinetix.callsmith.domain.http.HttpFailure.Kind.COMPRESSION;
import static com.mirakinetix.callsmith.domain.http.HttpFailure.Kind.LIMIT;

final class ResponseContent {
    static final int LIMIT_BYTES = 8 * 1024 * 1024;


    private ResponseContent() {}

    static String text(HttpHeaders headers, byte[] wire) {
        if (wire.length == 0) {
            return "";
        }
        String contentType = headers.firstValue("Content-Type").orElse("");
        String mime = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        boolean text = mime.startsWith("text/") || mime.equals("application/json")
                || mime.equals("application/xml") || mime.endsWith("+json") || mime.endsWith("+xml")
                || mime.equals("application/javascript") || mime.equals("application/x-www-form-urlencoded");
        if (!text && !mime.isEmpty()) {
            return null;
        }

        String encoding = String.join(",", headers.allValues("Content-Encoding")).strip();
        byte[] bytes = wire;
        if (encoding.equalsIgnoreCase("gzip") || encoding.equalsIgnoreCase("x-gzip")) {
            bytes = inflate(wire);
        } else if (!encoding.isEmpty() && !encoding.equalsIgnoreCase("identity")) {
            return null;
        }

        try {
            var charset = HttpMediaType.charset(contentType,
                    hasUtf16Bom(bytes) ? StandardCharsets.UTF_16 : StandardCharsets.UTF_8);
            String decoded = charset.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            if (mime.isEmpty() && decoded.chars()
                    .anyMatch(c -> Character.isISOControl(c) && c != '\r' && c != '\n' && c != '\t')) {
                return null;
            }
            return decoded;
        } catch (IllegalArgumentException | CharacterCodingException exception) {
            return null;
        }
    }

    private static boolean hasUtf16Bom(byte[] bytes) {
        return bytes.length >= 2 && ((bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff)
                || (bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe));
    }

    private static byte[] inflate(byte[] wire) {
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(wire))) {
            byte[] uncompressed = gzip.readNBytes(LIMIT_BYTES + 1);
            if (uncompressed.length > LIMIT_BYTES) {
                throw new HttpFailure(LIMIT);
            }
            return uncompressed;
        } catch (IOException exception) {
            throw new HttpFailure(COMPRESSION);
        }
    }

    static BodySubscriber<byte[]> boundedSubscriber() {
        return new BodySubscriber<>() {
            private final BodySubscriber<byte[]> downstream = BodySubscribers.ofByteArray();
            private Flow.Subscription subscription;
            private long accepted;
            private boolean limited;

            @Override public CompletionStage<byte[]> getBody() { return downstream.getBody(); }

            @Override public void onSubscribe(Flow.Subscription upstream) {
                subscription = upstream;
                downstream.onSubscribe(upstream);
            }

            @Override public void onNext(List<ByteBuffer> list) {
                if (limited) return;
                for (ByteBuffer buffer : list) {
                    accepted += buffer.remaining();
                    if (accepted > LIMIT_BYTES) {
                        limited = true;
                        downstream.onError(new HttpFailure(LIMIT));
                        subscription.cancel();
                        return;
                    }
                }
                downstream.onNext(list);
            }

            @Override public void onError(Throwable error) {
                if (!limited) downstream.onError(error);
            }

            @Override public void onComplete() {
                if (!limited) downstream.onComplete();
            }
        };
    }
}
