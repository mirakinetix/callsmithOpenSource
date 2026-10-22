package com.mirakinetix.callsmith.infrastructure.collection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mirakinetix.callsmith.application.collection.CollectionExporter;
import com.mirakinetix.callsmith.application.collection.CollectionWorkspaceService;
import com.mirakinetix.callsmith.domain.collection.CollectionFolder;
import com.mirakinetix.callsmith.domain.request.SavedRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PostmanCollectionExporter
        implements CollectionExporter {

    private static final String POSTMAN_SCHEMA =
            "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";

    private final ObjectMapper objectMapper;

    public PostmanCollectionExporter(
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
    public boolean supports(
            Path file
    ) {
        return file != null
                && file.getFileName() != null
                && file.getFileName()
                        .toString()
                        .toLowerCase()
                        .endsWith(".json");
    }

    @Override
    public void exportCollection(
            CollectionWorkspaceService.CollectionWorkspace workspace,
            Path file
    ) {
        if (workspace == null) {
            throw new IllegalArgumentException(
                    "Collection workspace is required"
            );
        }

        if (file == null) {
            throw new IllegalArgumentException(
                    "Export file is required"
            );
        }

        ObjectNode root =
                objectMapper.createObjectNode();

        ObjectNode info =
                root.putObject("info");

        info.put(
                "_postman_id",
                UUID.randomUUID().toString()
        );

        info.put(
                "name",
                workspace.collection().name()
        );

        info.put(
                "schema",
                POSTMAN_SCHEMA
        );

        ArrayNode items =
                root.putArray("item");

        appendRequests(
                items,
                workspace.requestsByFolder().get("")
        );

        appendFolders(
                items,
                workspace,
                null
        );

        try {
            String json =
                    objectMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(root);

            Files.writeString(
                    file,
                    json,
                    StandardCharsets.UTF_8
            );

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to export Postman collection to: "
                            + file,
                    exception
            );
        }
    }

    private void appendFolders(
            ArrayNode items,
            CollectionWorkspaceService.CollectionWorkspace workspace,
            String parentFolderId
    ) {
        String parentKey =
                parentFolderId == null
                        ? ""
                        : parentFolderId;

        List<CollectionFolder> folders =
                workspace.foldersByParent()
                        .get(parentKey);

        if (folders == null) {
            return;
        }

        for (CollectionFolder folder : folders) {
            ObjectNode folderItem =
                    items.addObject();

            folderItem.put(
                    "name",
                    folder.name()
            );

            ArrayNode folderItems =
                    folderItem.putArray("item");

            appendRequests(
                    folderItems,
                    workspace.requestsByFolder()
                            .get(folder.id())
            );

            appendFolders(
                    folderItems,
                    workspace,
                    folder.id()
            );
        }
    }

    private void appendRequests(
            ArrayNode items,
            List<SavedRequest> requests
    ) {
        if (requests == null) {
            return;
        }

        for (SavedRequest savedRequest : requests) {
            ObjectNode item =
                    items.addObject();

            item.put(
                    "name",
                    savedRequest.name()
            );

            ObjectNode request =
                    item.putObject("request");

            request.put(
                    "method",
                    savedRequest.method()
            );

            ArrayNode headers =
                    request.putArray("header");

            appendKeyValues(
                    headers,
                    savedRequest.headers()
            );

            ObjectNode url =
                    request.putObject("url");

            url.put(
                    "raw",
                    savedRequest.url() == null
                            ? ""
                            : savedRequest.url()
            );

            ArrayNode query =
                    url.putArray("query");

            appendKeyValues(
                    query,
                    savedRequest.queryParameters()
            );

            String body =
                    savedRequest.body();

            if (body != null && !body.isEmpty()) {
                ObjectNode bodyNode =
                        request.putObject("body");

                bodyNode.put(
                        "mode",
                        "raw"
                );

                bodyNode.put(
                        "raw",
                        body
                );
            }
        }
    }

    private void appendKeyValues(
            ArrayNode array,
            Map<String, String> values
    ) {
        if (values == null || values.isEmpty()) {
            return;
        }

        values.forEach(
                (key, value) -> {
                    if (key == null || key.isBlank()) {
                        return;
                    }

                    ObjectNode entry =
                            array.addObject();

                    entry.put(
                            "key",
                            key
                    );

                    entry.put(
                            "value",
                            value == null
                                    ? ""
                                    : value
                    );
                }
        );
    }
}