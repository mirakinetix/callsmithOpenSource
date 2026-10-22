package com.mirakinetix.callsmith.application.collection;

import com.mirakinetix.callsmith.domain.collection.Collection;

import java.nio.file.Path;
import java.util.List;

public final class CollectionExportService {

    private final CollectionWorkspaceService workspaceService;
    private final List<CollectionExporter> exporters;

    public CollectionExportService(
            CollectionWorkspaceService workspaceService,
            List<CollectionExporter> exporters
    ) {
        if (workspaceService == null) {
            throw new IllegalArgumentException(
                    "Collection workspace service is required"
            );
        }

        if (exporters == null || exporters.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one collection exporter is required"
            );
        }

        this.workspaceService = workspaceService;
        this.exporters = List.copyOf(exporters);
    }

    public void exportCollection(
            Collection collection,
            Path file
    ) {
        if (collection == null) {
            throw new IllegalArgumentException(
                    "Collection is required"
            );
        }

        if (file == null) {
            throw new IllegalArgumentException(
                    "Export file is required"
            );
        }

        CollectionExporter exporter =
                exporters.stream()
                        .filter(candidate ->
                                candidate.supports(file))
                        .findFirst()
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Unsupported export format: "
                                                + file.getFileName()
                                )
                        );

        CollectionWorkspaceService.CollectionWorkspace workspace =
                workspaceService.loadCollection(collection);

        exporter.exportCollection(
                workspace,
                file
        );
    }
}