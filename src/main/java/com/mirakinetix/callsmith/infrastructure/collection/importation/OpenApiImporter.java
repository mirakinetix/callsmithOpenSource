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

public final class OpenApiImporter
        implements CollectionImporter {

    private static final String OPENAPI_PREFIX =
            "3.";

    private static final List<String> HTTP_METHODS =
            List.of(
                    "get",
                    "post",
                    "put",
                    "patch",
                    "delete",
                    "head",
                    "options",
                    "trace"
            );

    private final ObjectMapper objectMapper;

    public OpenApiImporter(
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

            return isSupportedDocument(root);

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

            validateDocument(root);

            String collectionName =
                    readText(
                            root.path("info"),
                            "title",
                            "Imported OpenAPI"
                    );

            JsonNode servers =
                    root.get("servers");

            String serverUrl =
                    parseServerUrl(servers);

            Map<String, List<ImportedRequest>>
                    taggedRequests =
                    new LinkedHashMap<>();

            List<ImportedRequest> rootRequests =
                    new ArrayList<>();

            JsonNode paths =
                    root.get("paths");

            if (paths != null && paths.isObject()) {
                parsePaths(
                        paths,
                        root,
                        serverUrl,
                        taggedRequests,
                        rootRequests
                );
            }

            List<ImportedFolder> folders =
                    new ArrayList<>();

            taggedRequests.forEach(
                    (tag, requests) ->
                            folders.add(
                                    new ImportedFolder(
                                            tag,
                                            List.of(),
                                            requests
                                    )
                            )
            );

            return new ImportedCollection(
                    collectionName,
                    folders,
                    rootRequests
            );

        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Unable to read OpenAPI document: "
                            + file,
                    exception
            );
        }
    }

    private boolean isSupportedDocument(
            JsonNode root
    ) {
        if (root == null
                || !root.isObject()) {
            return false;
        }

        JsonNode openApi =
                root.get("openapi");

        return openApi != null
                && openApi.isTextual()
                && openApi.textValue()
                .startsWith(OPENAPI_PREFIX);
    }

    private void validateDocument(
            JsonNode root
    ) {
        if (!isSupportedDocument(root)) {
            throw new IllegalArgumentException(
                    "Unsupported OpenAPI document"
            );
        }

        JsonNode info =
                root.get("info");

        if (info == null
                || !info.isObject()) {
            throw new IllegalArgumentException(
                    "OpenAPI document info is missing"
            );
        }

        JsonNode paths =
                root.get("paths");

        if (paths == null
                || !paths.isObject()) {
            throw new IllegalArgumentException(
                    "OpenAPI document paths are missing"
            );
        }
    }

    private void parsePaths(
            JsonNode paths,
            JsonNode root,
            String serverUrl,
            Map<String, List<ImportedRequest>>
                    taggedRequests,
            List<ImportedRequest> rootRequests
    ) {
        for (Map.Entry<String, JsonNode> pathEntry :
                paths.properties()) {

            String path =
                    pathEntry.getKey();

            JsonNode pathItem =
                    pathEntry.getValue();

            if (!pathItem.isObject()) {
                continue;
            }

            for (String method :
                    HTTP_METHODS) {

                JsonNode operation =
                        pathItem.get(method);

                if (operation == null
                        || !operation.isObject()) {
                    continue;
                }

                ImportedRequest request =
                        parseOperation(
                                path,
                                method,
                                operation,
                                pathItem,
                                root,
                                serverUrl
                        );

                List<String> tags =
                        parseTags(operation);

                if (tags.isEmpty()) {
                    rootRequests.add(request);
                } else {
                    for (String tag : tags) {
                        taggedRequests
                                .computeIfAbsent(
                                        tag,
                                        ignored ->
                                                new ArrayList<>()
                                )
                                .add(request);
                    }
                }
            }
        }
    }

    private ImportedRequest parseOperation(
            String path,
            String method,
            JsonNode operation,
            JsonNode pathItem,
            JsonNode root,
            String serverUrl
    ) {
        String name =
                readOperationName(
                        operation,
                        method,
                        path
                );

        String url =
                buildUrl(
                        serverUrl,
                        path
                );

        Map<String, String> queryParameters =
                parseParameters(
                        root,
                        pathItem,
                        operation,
                        "query"
                );

        Map<String, String> headers =
                parseParameters(
                        root,
                        pathItem,
                        operation,
                        "header"
                );

        String body =
                parseRequestBody(
                        operation,
                        root
                );

        return new ImportedRequest(
                name,
                method.toUpperCase(),
                url,
                headers,
                queryParameters,
                body
        );
    }

    private String readOperationName(
            JsonNode operation,
            String method,
            String path
    ) {
        String summary =
                readText(
                        operation,
                        "summary",
                        ""
                );

        if (!summary.isBlank()) {
            return summary;
        }

        String operationId =
                readText(
                        operation,
                        "operationId",
                        ""
                );

        if (!operationId.isBlank()) {
            return operationId;
        }

        return method.toUpperCase()
                + " "
                + path;
    }

    private List<String> parseTags(
            JsonNode operation
    ) {
        List<String> tags =
                new ArrayList<>();

        JsonNode tagsNode =
                operation.get("tags");

        if (tagsNode == null
                || !tagsNode.isArray()) {
            return tags;
        }

        for (JsonNode tag : tagsNode) {
            if (!tag.isTextual()) {
                continue;
            }

            String value =
                    tag.textValue().trim();

            if (!value.isEmpty()
                    && !tags.contains(value)) {
                tags.add(value);
            }
        }

        return tags;
    }

    private Map<String, String> parseParameters(
            JsonNode root,
            JsonNode pathItem,
            JsonNode operation,
            String wantedLocation
    ) {
        Map<String, String> parameters =
                new LinkedHashMap<>();

        parseParameterArray(
                root,
                pathItem.get("parameters"),
                wantedLocation,
                parameters
        );

        parseParameterArray(
                root,
                operation.get("parameters"),
                wantedLocation,
                parameters
        );

        return parameters;
    }

    private void parseParameterArray(
            JsonNode root,
            JsonNode parameterNode,
            String wantedLocation,
            Map<String, String> parameters
    ) {
        if (parameterNode == null
                || !parameterNode.isArray()) {
            return;
        }

        for (JsonNode parameter :
                parameterNode) {

            JsonNode resolved =
                    resolveReference(
                            root,
                            parameter
                    );

            if (resolved == null
                    || !resolved.isObject()) {
                continue;
            }

            String location =
                    readText(
                            resolved,
                            "in",
                            ""
                    );

            if (!wantedLocation.equalsIgnoreCase(
                    location
            )) {
                continue;
            }

            String name =
                    readText(
                            resolved,
                            "name",
                            ""
                    );

            if (name.isBlank()) {
                continue;
            }

            String value =
                    parseParameterValue(
                            resolved
                    );

            parameters.put(
                    name,
                    value
            );
        }
    }

    private String parseParameterValue(
            JsonNode parameter
    ) {
        JsonNode example =
                parameter.get("example");

        if (example != null
                && !example.isNull()) {
            return scalarOrJsonText(example);
        }

        JsonNode examples =
                parameter.get("examples");

        if (examples != null
                && examples.isObject()) {

            for (Map.Entry<String, JsonNode> entry :
                    examples.properties()) {

                JsonNode exampleNode =
                        entry.getValue();

                if (exampleNode == null
                        || !exampleNode.isObject()) {
                    continue;
                }

                JsonNode value =
                        exampleNode.get("value");

                if (value != null
                        && !value.isNull()) {
                    return scalarOrJsonText(value);
                }
            }
        }

        JsonNode schema =
                parameter.get("schema");

        return parseSchemaExample(
                schema
        );
    }

    private String parseRequestBody(
            JsonNode operation,
            JsonNode root
    ) {
        JsonNode requestBody =
                operation.get("requestBody");

        if (requestBody == null
                || !requestBody.isObject()) {
            return "";
        }

        JsonNode content =
                requestBody.get("content");

        if (content == null
                || !content.isObject()) {
            return "";
        }

        JsonNode mediaType =
                selectMediaType(content);

        if (mediaType == null
                || !mediaType.isObject()) {
            return "";
        }

        JsonNode example =
                mediaType.get("example");

        if (example != null
                && !example.isNull()) {
            return scalarOrJsonText(example);
        }

        JsonNode examples =
                mediaType.get("examples");

        if (examples != null
                && examples.isObject()) {

            for (Map.Entry<String, JsonNode> entry :
                    examples.properties()) {

                JsonNode exampleNode =
                        entry.getValue();

                if (exampleNode == null
                        || !exampleNode.isObject()) {
                    continue;
                }

                JsonNode value =
                        exampleNode.get("value");

                if (value != null
                        && !value.isNull()) {
                    return scalarOrJsonText(value);
                }
            }
        }

        JsonNode schema =
                mediaType.get("schema");

        JsonNode resolvedSchema =
                resolveReference(
                        root,
                        schema
                );

        return generateExampleFromSchema(
                root,
                resolvedSchema
        );
    }

    private JsonNode selectMediaType(
            JsonNode content
    ) {
        JsonNode json =
                content.get("application/json");

        if (json != null) {
            return json;
        }

        for (Map.Entry<String, JsonNode> entry :
                content.properties()) {

            JsonNode value =
                    entry.getValue();

            if (value != null
                    && value.isObject()) {
                return value;
            }
        }

        return null;
    }

    private String generateExampleFromSchema(
            JsonNode root,
            JsonNode schema
    ) {
        if (schema == null
                || !schema.isObject()) {
            return "";
        }

        JsonNode example =
                schema.get("example");

        if (example != null
                && !example.isNull()) {
            return scalarOrJsonText(example);
        }

        JsonNode type =
                schema.get("type");

        if (type == null
                || !type.isTextual()) {
            return "";
        }

        return switch (type.textValue()) {
            case "object" ->
                    generateObjectExample(
                            root,
                            schema
                    );
            case "array" ->
                    generateArrayExample(
                            root,
                            schema
                    );
            case "string" ->
                    parseSchemaScalarDefault(
                            schema,
                            ""
                    );
            case "integer", "number" ->
                    parseSchemaScalarDefault(
                            schema,
                            "0"
                    );
            case "boolean" ->
                    parseSchemaScalarDefault(
                            schema,
                            "false"
                    );
            default -> "";
        };
    }

    private String generateObjectExample(
            JsonNode root,
            JsonNode schema
    ) {
        JsonNode properties =
                schema.get("properties");

        if (properties == null
                || !properties.isObject()) {
            return "{}";
        }

        Map<String, Object> values =
                new LinkedHashMap<>();

        for (Map.Entry<String, JsonNode> entry :
                properties.properties()) {

            String name =
                    entry.getKey();

            JsonNode propertySchema =
                    entry.getValue();

            JsonNode resolved =
                    resolveReference(
                            root,
                            propertySchema
                    );

            String example =
                    generateExampleFromSchema(
                            root,
                            resolved
                    );

            if (!example.isEmpty()) {
                values.put(
                        name,
                        parseJsonValue(
                                example
                        )
                );
            }
        }

        try {
            return objectMapper.writeValueAsString(
                    values
            );
        } catch (IOException exception) {
            return "";
        }
    }

    private String generateArrayExample(
            JsonNode root,
            JsonNode schema
    ) {
        JsonNode items =
                resolveReference(
                        root,
                        schema.get("items")
                );

        String example =
                generateExampleFromSchema(
                        root,
                        items
                );

        if (example.isEmpty()) {
            return "[]";
        }

        return "[" + example + "]";
    }

    private Object parseJsonValue(
            String value
    ) {
        try {
            return objectMapper.readTree(value);
        } catch (IOException exception) {
            return value;
        }
    }

    private String parseSchemaExample(
            JsonNode schema
    ) {
        if (schema == null
                || !schema.isObject()) {
            return "";
        }

        JsonNode example =
                schema.get("example");

        if (example != null
                && !example.isNull()) {
            return scalarOrJsonText(example);
        }

        return parseSchemaScalarDefault(
                schema,
                ""
        );
    }

    private String parseSchemaScalarDefault(
            JsonNode schema,
            String fallback
    ) {
        JsonNode defaultValue =
                schema.get("default");

        if (defaultValue == null
                || defaultValue.isNull()) {
            return fallback;
        }

        return scalarOrJsonText(
                defaultValue
        );
    }

    private String scalarOrJsonText(
            JsonNode value
    ) {
        if (value.isTextual()) {
            return value.textValue();
        }

        return value.toString();
    }

    private JsonNode resolveReference(
            JsonNode root,
            JsonNode node
    ) {
        if (node == null
                || !node.isObject()) {
            return node;
        }

        JsonNode reference =
                node.get("$ref");

        if (reference == null
                || !reference.isTextual()) {
            return node;
        }

        String value =
                reference.textValue();

        if (!value.startsWith("#/")) {
            return node;
        }

        JsonNode current =
                root;

        String[] parts =
                value.substring(2)
                        .split("/");

        for (String part : parts) {
            String decoded =
                    part.replace("~1", "/")
                            .replace("~0", "~");

            current =
                    current.get(decoded);

            if (current == null) {
                return node;
            }
        }

        return current;
    }

    private String parseServerUrl(
            JsonNode servers
    ) {
        if (servers == null
                || !servers.isArray()
                || servers.isEmpty()) {
            return "";
        }

        JsonNode server =
                servers.get(0);

        if (server == null
                || !server.isObject()) {
            return "";
        }

        return readText(
                server,
                "url",
                ""
        );
    }

    private String buildUrl(
            String serverUrl,
            String path
    ) {
        if (serverUrl == null
                || serverUrl.isBlank()) {
            return path;
        }

        if (serverUrl.endsWith("/")
                && path.startsWith("/")) {
            return serverUrl.substring(
                    0,
                    serverUrl.length() - 1
            ) + path;
        }

        if (!serverUrl.endsWith("/")
                && !path.startsWith("/")) {
            return serverUrl + "/" + path;
        }

        return serverUrl + path;
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