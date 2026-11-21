package com.mirakinetix.callsmith.application.collection.importation;

import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;

import java.nio.file.Path;
import java.util.List;

public final class CollectionImportService {
    private final List<CollectionImporter> importers;

    public CollectionImportService(List<CollectionImporter> importers) {
        if (importers == null || importers.isEmpty()) {
            throw new IllegalArgumentException("At least one collection importer is required");
        }
        this.importers = List.copyOf(importers);
    }

    public ImportedCollection importCollection(Path file) {
        var document = CollectionImportDocument.read(file);
        CollectionImporter importer = importers.stream()
                .filter(candidate -> candidate.supportsDocument(document))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported collection format: " + file.getFileName()));
        return CollectionImportDocument.validated(importer.importDocument(document));
    }
}
