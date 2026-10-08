package com.mirakinetix.callsmith.domain.collection.importation;

import java.util.List;

public record ImportedFolder(
        String name,
        List<ImportedFolder> folders,
        List<ImportedRequest> requests
) {
}