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

public final class InsomniaCollectionImporter
        implements CollectionImporter {

    private static final String INSOMNIA_VERSION =
            "insomnia.rest/4.0";

    private static final List<String> HTTP_METHODS =
            List.of(
                    "GET",
                    "POST",
                    "PUT",
                    "PATCH",
                    "DELETE",
                    "HEAD",
                    "OPTIONS",
                    "TRACE"
            );

    private final ObjectMapper objectMapper;

    public InsomniaCollectionImporter() {
        this(new ObjectMapper());
    }

    public InsomniaCollectionImporter(
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
        if (file == null) {
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
                    objectMapper.readTree(
                            Files.readString(file)
                    );

            return isInsomniaDocument(root);

        } catch (IOException | RuntimeException exception) {
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
                    objectMapper.readTree(
                            Files.readString(file)
                    );

            if (!isInsomniaDocument(root)) {
                throw new IllegalArgumentException(
                        "Unsupported Insomnia collection format"
                );
            }

            return parseCollection(root);

        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Unable to read Insomnia collection",
                    exception
            );
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }

            throw new IllegalArgumentException(
                    "Unable to parse Insomnia collection",
                    exception
            );
        }
    }

    private boolean isInsomniaDocument(
            JsonNode root
    ) {
        if (root == null || !root.isObject()) {
            return false;
        }

        JsonNode resources =
                root.get("resources");

        if (resources == null
                || !resources.isArray()) {
            return false;
        }

        JsonNode format =
                root.get("__export_format");

        if (format != null
                && format.isInt()
                && format.asInt() == 4) {
            return true;
        }

        for (JsonNode resource : resources) {
            if (!resource.isObject()) {
                continue;
            }

            String type =
                    textValue(
                            resource.get("_type")
                    );

            if ("workspace".equals(type)
                    || "request".equals(type)
                    || "request_group".equals(type)) {
                return true;
            }
        }

        return false;
    }

    private ImportedCollection parseCollection(
            JsonNode root
    ) {
        List<JsonNode> resources =
                new ArrayList<>();

        root.get("resources")
                .forEach(resources::add);

        JsonNode workspace =
                resources.stream()
                        .filter(resource ->
                                "workspace".equals(
                                        textValue(
                                                resource.get("_type")
                                        )
                                )
                        )
                        .findFirst()
                        .orElse(null);

        String collectionName =
                workspace == null
                        ? "Imported Insomnia Collection"
                        : textValue(
                                workspace.get("name")
                        );

        if (collectionName == null
                || collectionName.isBlank()) {
            collectionName =
                    "Imported Insomnia Collection";
        }

        Map<String, JsonNode> resourcesById =
                indexResources(resources);

        List<ImportedRequest> rootRequests =
                new ArrayList<>();

        List<ImportedFolder> folders =
                new ArrayList<>();

        for (JsonNode resource : resources) {
            String type =
                    textValue(
                            resource.get("_type")
                    );

            if (!"request".equals(type)) {
                continue;
            }

            String parentId =
                    textValue(
                            resource.get("parentId")
                    );

            if (parentId == null
                    || parentId.isBlank()
                    || isWorkspace(
                            resourcesById.get(parentId)
                    )) {

                rootRequests.add(
                        parseRequest(resource)
                );
            }
        }

        for (JsonNode resource : resources) {
            if (!"request_group".equals(
                    textValue(
                            resource.get("_type")
                    ))) {
                continue;
            }

            String parentId =
                    textValue(
                            resource.get("parentId")
                    );

            if (parentId == null
                    || parentId.isBlank()
                    || isWorkspace(
                            resourcesById.get(parentId)
                    )) {

                folders.add(
                        parseFolder(
                                resource,
                                resourcesById
                        )
                );
            }
        }

        return new ImportedCollection(
                collectionName,
                folders,
                rootRequests
        );
    }

    private ImportedFolder parseFolder(
            JsonNode folder,
            Map<String, JsonNode> resourcesById
    ) {
        String folderId =
                textValue(
                        folder.get("_id")
                );

        String folderName =
                textValue(
                        folder.get("name")
                );

        if (folderName == null
                || folderName.isBlank()) {
            folderName = "Unnamed Folder";
        }

        List<ImportedFolder> childFolders =
                new ArrayList<>();

        List<ImportedRequest> requests =
                new ArrayList<>();

        for (JsonNode resource :
                resourcesById.values()) {

            String parentId =
                    textValue(
                            resource.get("parentId")
                    );

            if (!folderId.equals(parentId)) {
                continue;
            }

            String type =
                    textValue(
                            resource.get("_type")
                    );

            if ("request_group".equals(type)) {
                childFolders.add(
                        parseFolder(
                                resource,
                                resourcesById
                        )
                );
            } else if ("request".equals(type)) {
                requests.add(
                        parseRequest(resource)
                );
            }
        }

        return new ImportedFolder(
                folderName,
                childFolders,
                requests
        );
    }

    private ImportedRequest parseRequest(
            JsonNode request
    ) {
        String name =
                textValue(
                        request.get("name")
                );

        if (name == null
                || name.isBlank()) {
            name = "Unnamed Request";
        }

        String method =
                textValue(
                        request.get("method")
                );

        if (method == null
                || method.isBlank()) {
            method = "GET";
        }

        method =
                method.toUpperCase();

        if (!HTTP_METHODS.contains(method)) {
            method = "GET";
        }

        String url =
                textValue(
                        request.get("url")
                );

        if (url == null) {
            url = "";
        }

        Map<String, String> headers =
                parseHeaders(
                        request.get("headers")
                );

        Map<String, String> queryParameters =
                parseQueryParameters(
                        request.get("parameters")
                );

        String body =
                parseBody(
                        request.get("body")
                );

        return new ImportedRequest(
                name,
                method,
                url,
                headers,
                queryParameters,
                body
        );
    }

    private Map<String, String> parseHeaders(
            JsonNode headersNode
    ) {
        Map<String, String> headers =
                new LinkedHashMap<>();

        if (headersNode == null
                || !headersNode.isArray()) {
            return headers;
        }

        for (JsonNode header : headersNode) {
            if (isDisabled(header)) {
                continue;
            }

            String name =
                    textValue(
                            header.get("name")
                    );

            if (name == null
                    || name.isBlank()) {
                continue;
            }

            String value =
                    textValue(
                            header.get("value")
                    );

            headers.put(
                    name,
                    value == null ? "" : value
            );
        }

        return headers;
    }

    private Map<String, String> parseQueryParameters(
            JsonNode parametersNode
    ) {
        Map<String, String> parameters =
                new LinkedHashMap<>();

        if (parametersNode == null
                || !parametersNode.isArray()) {
            return parameters;
        }

        for (JsonNode parameter : parametersNode) {
            if (isDisabled(parameter)) {
                continue;
            }

            String name =
                    textValue(
                            parameter.get("name")
                    );

            if (name == null
                    || name.isBlank()) {
                continue;
            }

            String value =
                    textValue(
                            parameter.get("value")
                    );

            parameters.put(
                    name,
                    value == null ? "" : value
            );
        }

        return parameters;
    }

    private String parseBody(
            JsonNode bodyNode
    ) {
        if (bodyNode == null
                || !bodyNode.isObject()) {
            return null;
        }

        String mimeType =
                textValue(
                        bodyNode.get("mimeType")
                );

        JsonNode text =
                bodyNode.get("text");

        if (text != null
                && !text.isNull()) {
            return text.isTextual()
                    ? text.asText()
                    : text.toString();
        }

        JsonNode params =
                bodyNode.get("params");

        if (params != null
                && params.isArray()) {
            return params.toString();
        }

        if (mimeType != null
                && mimeType.equalsIgnoreCase(
                        "application/json"
                )) {
            JsonNode json =
                    bodyNode.get("json");

            if (json != null
                    && !json.isNull()) {
                return json.toString();
            }
        }

        return null;
    }

    private boolean isDisabled(
            JsonNode node
    ) {
        JsonNode disabled =
                node.get("disabled");

        return disabled != null
                && disabled.isBoolean()
                && disabled.asBoolean();
    }

    private Map<String, JsonNode> indexResources(
            List<JsonNode> resources
    ) {
        Map<String, JsonNode> resourcesById =
                new LinkedHashMap<>();

        for (JsonNode resource : resources) {
            String id =
                    textValue(
                            resource.get("_id")
                    );

            if (id != null
                    && !id.isBlank()) {
                resourcesById.put(
                        id,
                        resource
                );
            }
        }

        return resourcesById;
    }

    private boolean isWorkspace(
            JsonNode resource
    ) {
        return resource != null
                && "workspace".equals(
                        textValue(
                                resource.get("_type")
                        )
                );
    }

    private String textValue(
            JsonNode node
    ) {
        if (node == null
                || node.isNull()) {
            return null;
        }

        return node.isTextual()
                ? node.asText()
                : node.asText();
    }
}