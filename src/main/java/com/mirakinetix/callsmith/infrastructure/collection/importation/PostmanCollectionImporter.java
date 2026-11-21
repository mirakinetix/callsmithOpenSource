package com.mirakinetix.callsmith.infrastructure.collection.importation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImporter;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedFolder;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PostmanCollectionImporter
        implements CollectionImporter {

    private static final String POSTMAN_SCHEMA_PREFIX =
            "https://schema.getpostman.com/json/collection/v2";

    private final ObjectMapper objectMapper;

    public PostmanCollectionImporter(
            ObjectMapper objectMapper
    ) {
        if (objectMapper == null) {
            throw new IllegalArgumentException(
                    "ObjectMapper is required"
            );
        }

        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(Path file) {
        if (file == null
                || !Files.isRegularFile(file)) {
            return false;
        }

        String fileName =
                file.getFileName()
                        .toString()
                        .toLowerCase();

        if (!fileName.endsWith(".json")) {
            return false;
        }

        try {
            JsonNode root =
                    objectMapper.readTree(file.toFile());

            if (root == null || !root.isObject()) {
                return false;
            }

            JsonNode info =
                    root.get("info");

            if (info == null || !info.isObject()) {
                return false;
            }

            JsonNode schema =
                    info.get("schema");

            if (schema == null
                    || !schema.isTextual()) {
                return false;
            }

            return schema.textValue()
                    .startsWith(
                            POSTMAN_SCHEMA_PREFIX
                    );

        } catch (IOException exception) {
            return false;
        }
    }

    @Override
    public ImportedCollection importCollection(
            Path file
    ) {
        if (file == null) {
            throw new IllegalArgumentException(
                    "Import file is required"
            );
        }

        try {
            JsonNode root =
                    objectMapper.readTree(file.toFile());

            validateRoot(root);

            String collectionName =
                    readText(
                            root.path("info"),
                            "name",
                            "Imported Postman Collection"
                    );

            List<ImportedFolder> folders =
                    new ArrayList<>();

            List<ImportedRequest> rootRequests =
                    new ArrayList<>();

            var warnings = new java.util.LinkedHashSet<String>();
            if (root.hasNonNull("auth")) warnings.add("Collection authentication was omitted.");
            if (root.hasNonNull("event")) warnings.add("Postman scripts were omitted and were not run.");
            if (root.hasNonNull("variable")) warnings.add("Postman variable definitions were omitted. Configure matching Callsmith variables.");
            boolean separateUrlFields = root.path("x-callsmith-url-parts").asBoolean(false);
            JsonNode items = root.get("item");

            if (items != null && items.isArray()) {
                parseItems(
                        items,
                        folders,
                        rootRequests,
                        warnings, separateUrlFields, 0
                );
            }

            return new ImportedCollection(
                    collectionName,
                    folders,
                    rootRequests,
                    List.copyOf(warnings)
            );

        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Unable to read Postman collection: "
                            + file,
                    exception
            );
        }
    }

    private void validateRoot(
            JsonNode root
    ) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException(
                    "Invalid Postman collection"
            );
        }

        JsonNode info =
                root.get("info");

        if (info == null || !info.isObject()) {
            throw new IllegalArgumentException(
                    "Postman collection info is missing"
            );
        }

        JsonNode schema =
                info.get("schema");

        if (schema == null
                || !schema.isTextual()
                || !schema.textValue()
                .startsWith(POSTMAN_SCHEMA_PREFIX)) {
            throw new IllegalArgumentException(
                    "Unsupported Postman collection schema"
            );
        }
    }

    private void parseItems(
            JsonNode items,
            List<ImportedFolder> folders,
            List<ImportedRequest> requests,
            java.util.Set<String> warnings, boolean separateUrlFields, int depth
    ) {
        if (depth > 64) throw new IllegalArgumentException("Postman collection nesting exceeds 64 levels.");
        int index = -1;
        for (JsonNode item : items) {
            index++;
            if (item.hasNonNull("event")) warnings.add("Postman scripts were omitted and were not run.");
            if (item.hasNonNull("auth")) warnings.add("Folder or request authentication was omitted.");
            if (!item.isObject()) {
                continue;
            }

            JsonNode nestedItems =
                    item.get("item");

            if (nestedItems != null
                    && nestedItems.isArray()) {

                List<ImportedFolder> childFolders =
                        new ArrayList<>();

                List<ImportedRequest> childRequests =
                        new ArrayList<>();

                parseItems(
                        nestedItems,
                        childFolders,
                        childRequests,
                        warnings, separateUrlFields, depth + 1
                );

                folders.add(
                        new ImportedFolder(
                                readText(
                                        item,
                                        "name",
                                        "Unnamed Folder"
                                ),
                                childFolders,
                                childRequests,
                                index
                        )
                );

                continue;
            }

            JsonNode requestNode =
                    item.get("request");

            if (requestNode != null
                    && requestNode.isObject()) {

                requests.add(
                        parseRequest(item, index, warnings, separateUrlFields)
                );
            } else {
                warnings.add("At least one unsupported Postman item was omitted.");
            }
        }
    }

    private ImportedRequest parseRequest(JsonNode item, int order, java.util.Set<String> warnings,
                                         boolean separateUrlFields) {
        JsonNode request =
                item.get("request");

        String name =
                readText(
                        item,
                        "name",
                        "Unnamed Request"
                );

        String method =
                readText(
                        request,
                        "method",
                        "GET"
                );

        String url =
                parseUrl(request);

        Map<String, String> headers =
                parseHeaders(request);

        Map<String, String> queryParameters =
                parseQueryParameters(request);

        if (request.hasNonNull("auth")) warnings.add("Folder or request authentication was omitted.");
        JsonNode bodyNode = request.path("body");
        if (bodyNode.hasNonNull("mode") && !bodyNode.path("mode").asText().equalsIgnoreCase("raw")) {
            warnings.add("Non-raw Postman bodies and file references were omitted.");
        }
        warnDuplicateFields(request.path("header"), true, warnings);
        warnDuplicateFields(request.path("url").path("query"), false, warnings);
        if (!separateUrlFields) {
            url = normalizeMirroredQuery(url, request.path("url").path("query"), queryParameters);
        }
        String body = parseBody(request);

        return new ImportedRequest(
                name,
                method,
                url,
                headers,
                queryParameters,
                body,
                order
        );
    }

    private static void warnDuplicateFields(JsonNode fields, boolean headers, java.util.Set<String> warnings) {
        var seen = new java.util.HashSet<String>();
        for (JsonNode field : fields) {
            if (field.path("disabled").asBoolean(false)) continue;
            String key = field.path("key").asText();
            if (headers) key = key.toLowerCase(java.util.Locale.ROOT);
            if (!key.isBlank() && !seen.add(key)) {
                warnings.add(headers ? "Repeated Postman headers cannot all be represented in the header editor."
                        : "Repeated structured query fields may be reduced. Repeated fields already in the URL are preserved.");
            }
        }
    }

    private static String normalizeMirroredQuery(String url, JsonNode structured, Map<String, String> fields) {
        int fragment = url.indexOf('#');
        String beforeFragment = fragment < 0 ? url : url.substring(0, fragment);
        int question = beforeFragment.indexOf('?');
        if (question < 0 || !structured.isArray()) return url;
        var rawPairs = new ArrayList<>(List.of(beforeFragment.substring(question + 1).split("&", -1)));
        int originalSize = rawPairs.size();
        for (JsonNode item : structured) {
            if (item.path("disabled").asBoolean(false)) {
                removePair(rawPairs, item.path("key").asText(), item.path("value").asText());
            }
        }
        fields.forEach((key, value) -> removePair(rawPairs, key, value));
        if (rawPairs.size() == originalSize) return url;
        return beforeFragment.substring(0, question)
                + (rawPairs.isEmpty() ? "" : "?" + String.join("&", rawPairs))
                + (fragment < 0 ? "" : url.substring(fragment));
    }

    private static void removePair(List<String> pairs, String key, String value) {
        for (int index = 0; index < pairs.size(); index++) {
            String[] bits = pairs.get(index).split("=", 2);
            try {
                if (java.net.URLDecoder.decode(bits[0], java.nio.charset.StandardCharsets.UTF_8).equals(key)
                        && java.net.URLDecoder.decode(bits.length == 2 ? bits[1] : "",
                                java.nio.charset.StandardCharsets.UTF_8).equals(value)) {
                    pairs.remove(index);
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // Keep malformed percent escapes visible for review in the editor.
            }
        }
    }

    private String parseUrl(
            JsonNode request
    ) {
        JsonNode urlNode =
                request.get("url");

        if (urlNode == null) {
            return "";
        }

        if (urlNode.isTextual()) {
            return urlNode.textValue();
        }

        if (urlNode.isObject()) {
            JsonNode raw =
                    urlNode.get("raw");

            if (raw != null && raw.isTextual()) {
                return raw.textValue();
            }
        }

        return "";
    }

    private Map<String, String> parseHeaders(
            JsonNode request
    ) {
        Map<String, String> headers =
                new LinkedHashMap<>();

        JsonNode headerNode =
                request.get("header");

        if (headerNode == null
                || !headerNode.isArray()) {
            return headers;
        }

        for (JsonNode header : headerNode) {
            if (!header.isObject()) {
                continue;
            }

            if (header.path("disabled").asBoolean(false)) {
                continue;
            }

            String key =
                    readText(
                            header,
                            "key",
                            ""
                    );

            if (key.isBlank()) {
                continue;
            }

            String value =
                    readText(
                            header,
                            "value",
                            ""
                    );

            headers.put(key, value);
        }

        return headers;
    }

    private Map<String, String> parseQueryParameters(
            JsonNode request
    ) {
        Map<String, String> queryParameters =
                new LinkedHashMap<>();

        JsonNode urlNode =
                request.get("url");

        if (urlNode == null
                || !urlNode.isObject()) {
            return queryParameters;
        }

        JsonNode queryNode =
                urlNode.get("query");

        if (queryNode == null
                || !queryNode.isArray()) {
            return queryParameters;
        }

        for (JsonNode query : queryNode) {
            if (!query.isObject()) {
                continue;
            }

            if (query.path("disabled").asBoolean(false)) {
                continue;
            }

            String key =
                    readText(
                            query,
                            "key",
                            ""
                    );

            if (key.isBlank()) {
                continue;
            }

            String value =
                    readText(
                            query,
                            "value",
                            ""
                    );

            queryParameters.put(key, value);
        }

        return queryParameters;
    }

    private String parseBody(
            JsonNode request
    ) {
        JsonNode bodyNode =
                request.get("body");

        if (bodyNode == null
                || !bodyNode.isObject()) {
            return "";
        }

        String mode =
                readText(
                        bodyNode,
                        "mode",
                        ""
                );

        if (!"raw".equalsIgnoreCase(mode)) {
            return "";
        }

        JsonNode raw =
                bodyNode.get("raw");

        if (raw == null
                || raw.isNull()) {
            return "";
        }

        return raw.isTextual()
                ? raw.textValue()
                : raw.toString();
    }

    private String readText(
            JsonNode parent,
            String field,
            String defaultValue
    ) {
        JsonNode value =
                parent.get(field);

        if (value == null
                || !value.isTextual()) {
            return defaultValue;
        }

        return value.textValue();
    }
}