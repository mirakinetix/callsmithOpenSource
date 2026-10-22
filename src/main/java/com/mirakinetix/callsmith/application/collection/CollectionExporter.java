package com.mirakinetix.callsmith.application.collection;

import java.nio.file.Path;

public interface CollectionExporter {

    boolean supports(
            Path file
    );

    void exportCollection(
            CollectionWorkspaceService.CollectionWorkspace workspace,
            Path file
    );
}