package com.mirakinetix.callsmith.domain.collection.importation;

import java.util.List;

public record ImportedCollection(
        String name,
        List<ImportedFolder> folders,
        List<ImportedRequest> requests,
        List<String> warnings
) {
    public ImportedCollection(String name, List<ImportedFolder> folders, List<ImportedRequest> requests) {
        this(name, folders, requests, List.of());
    }

    public ImportedCollection {
        warnings = List.copyOf(warnings);
    }
}