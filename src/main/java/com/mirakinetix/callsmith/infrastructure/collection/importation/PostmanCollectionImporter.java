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

            JsonNode items =
                    root.get("item");

            if (items != null && items.isArray()) {
                parseItems(
                        items,
                        folders,
                        rootRequests
                );
            }

            return new ImportedCollection(
                    collectionName,
                    folders,
                    rootRequests
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
            List<ImportedRequest> requests
    ) {
        for (JsonNode item : items) {
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
                        childRequests
                );

                folders.add(
                        new ImportedFolder(
                                readText(
                                        item,
                                        "name",
                                        "Unnamed Folder"
                                ),
                                childFolders,
                                childRequests
                        )
                );

                continue;
            }

            JsonNode requestNode =
                    item.get("request");

            if (requestNode != null
                    && requestNode.isObject()) {

                requests.add(
                        parseRequest(item)
                );
            }
        }
    }

    private ImportedRequest parseRequest(
            JsonNode item
    ) {
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

        String body =
                parseBody(request);

        return new ImportedRequest(
                name,
                method,
                url,
                headers,
                queryParameters,
                body
        );
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