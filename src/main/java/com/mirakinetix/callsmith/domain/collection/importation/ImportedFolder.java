package com.mirakinetix.callsmith.domain.collection.importation;

import java.util.List;

public record ImportedFolder(
        String name,
        List<ImportedFolder> folders,
        List<ImportedRequest> requests,
        Integer sortOrder
) {
    public ImportedFolder(String name, List<ImportedFolder> folders, List<ImportedRequest> requests) {
        this(name, folders, requests, null);
    }
}