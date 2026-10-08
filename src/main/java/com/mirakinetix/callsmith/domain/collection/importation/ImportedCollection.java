package com.mirakinetix.callsmith.domain.collection.importation;

import java.util.List;

public record ImportedCollection(
        String name,
        List<ImportedFolder> folders,
        List<ImportedRequest> requests
) {
}