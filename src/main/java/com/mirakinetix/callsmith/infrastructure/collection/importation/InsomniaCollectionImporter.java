package com.mirakinetix.callsmith.infrastructure.collection.importation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImportDocument;
import com.mirakinetix.callsmith.application.collection.importation.CollectionImporter;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedFolder;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public ObjectReader documentReader() {
        return objectMapper.reader();
    }

    @Override
    public boolean supportsDocument(JsonNode root) {
        return isInsomniaDocument(root);
    }

    @Override
    public ImportedCollection importDocument(JsonNode root) {
        if (!isInsomniaDocument(root)) throw new IllegalArgumentException("Unsupported Insomnia collection format");
        return parseCollection(root);
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

    private ImportedCollection parseCollection(JsonNode root) {
        JsonNode resources = root.get("resources");
        CollectionImportDocument.checkResourceCount(resources.size());
        Map<String, JsonNode> indexed = indexResources(resources);
        var workspaces = indexed.values().stream().filter(resource -> "workspace".equals(type(resource))).toList();
        String name = workspaces.isEmpty() ? "" : workspaces.getFirst().path("name").asText("");
        if (name.isBlank()) name = "Imported Insomnia Collection";

        Map<String, List<JsonNode>> children = new HashMap<>();
        int supported = 0;
        int omitted = 0;
        for (var resource : indexed.values()) {
            String kind = type(resource);
            if ("workspace".equals(kind)) {
                if (parent(resource) != null) {
                    throw new IllegalArgumentException("An Insomnia workspace cannot have a parent.");
                }
                continue;
            }
            if (!"request".equals(kind) && !"request_group".equals(kind)) {
                omitted++;
                continue;
            }
            supported++;
            String parent = parent(resource);
            String key = rootOrFolder(parent, indexed, workspaces.isEmpty());
            children.computeIfAbsent(key, ignored -> new ArrayList<>()).add(resource);
        }

        Set<String> visited = new HashSet<>();
        var imported = importChildren("", children, visited, 0);
        if (visited.size() != supported) {
            throw new IllegalArgumentException("An Insomnia folder or request is unreachable. Check for a folder cycle.");
        }
        var warnings = new ArrayList<String>();
        warnings.add("Review imported requests. Insomnia authentication, scripts, structured body fields, "
                + "file references and environment definitions are not imported.");
        if (omitted > 0) warnings.add("Skipped " + omitted + " unsupported Insomnia resources.");
        return new ImportedCollection(name, imported.folders(), imported.requests(), warnings);
    }

    private static String rootOrFolder(String parent, Map<String, JsonNode> indexed, boolean workspaceLess) {
        if (parent == null && workspaceLess) return "";
        if (parent == null) throw new IllegalArgumentException("An Insomnia request or folder has no parent.");
        var owner = indexed.get(parent);
        if (owner == null) throw new IllegalArgumentException("An Insomnia request or folder has an unknown parent.");
        return switch (type(owner)) {
            case "workspace" -> "";
            case "request_group" -> parent;
            default -> throw new IllegalArgumentException("An Insomnia request or folder must belong to a workspace or folder.");
        };
    }

    private Children importChildren(String parent, Map<String, List<JsonNode>> groups, Set<String> visited, int depth) {
        var folders = new ArrayList<ImportedFolder>();
        var requests = new ArrayList<ImportedRequest>();
        var siblings = groups.getOrDefault(parent, List.of());
        for (int index = 0; index < siblings.size(); index++) {
            var resource = siblings.get(index);
            String id = resource.get("_id").textValue();
            visited.add(id);
            if ("request".equals(type(resource))) {
                requests.add(parseRequest(resource, index));
                continue;
            }
            CollectionImportDocument.checkFolderDepth(depth + 1);
            String name = resource.path("name").asText("");
            if (name.isBlank()) name = "Unnamed Folder";
            var nested = importChildren(id, groups, visited, depth + 1);
            folders.add(new ImportedFolder(name, nested.folders(), nested.requests(), index));
        }
        return new Children(folders, requests);
    }

    private record Children(List<ImportedFolder> folders, List<ImportedRequest> requests) {}

    private ImportedRequest parseRequest(JsonNode request, Integer order) {
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
                method.toUpperCase(java.util.Locale.ROOT);

        if (!HTTP_METHODS.contains(method)) {
            throw new IllegalArgumentException("The Insomnia export contains an unsupported HTTP method.");
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
                body,
                order
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
            // Structured params and file references are not raw HTTP request content.
            return null;
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

    private static Map<String, JsonNode> indexResources(JsonNode resources) {
        Map<String, JsonNode> indexed = new LinkedHashMap<>();
        for (var resource : resources) {
            if (!resource.isObject() || type(resource).isBlank()) {
                throw new IllegalArgumentException("Every Insomnia resource must be an object with a record type.");
            }
            var id = resource.path("_id");
            if (!id.isTextual() || id.textValue().isBlank()) {
                throw new IllegalArgumentException("Every Insomnia resource must have an identifier.");
            }
            if (indexed.putIfAbsent(id.textValue(), resource) != null) {
                throw new IllegalArgumentException("Insomnia export contains duplicate resource IDs.");
            }
        }
        return indexed;
    }

    private static String type(JsonNode resource) {
        var type = resource.path("_type");
        return type.isTextual() ? type.textValue() : "";
    }

    private static String parent(JsonNode resource) {
        var parent = resource.path("parentId");
        if (parent.isMissingNode() || parent.isNull()) return null;
        if (!parent.isTextual() || parent.textValue().isBlank()) {
            throw new IllegalArgumentException("An Insomnia parent identifier must be a non-empty string.");
        }
        return parent.textValue();
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