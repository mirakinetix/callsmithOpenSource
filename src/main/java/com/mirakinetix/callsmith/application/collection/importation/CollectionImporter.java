package com.mirakinetix.callsmith.application.collection.importation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;

import java.nio.file.Path;

public interface CollectionImporter {
    ObjectReader documentReader();
    boolean supportsDocument(JsonNode document);
    ImportedCollection importDocument(JsonNode document);

    default boolean supports(Path file) {
        try {
            return supportsDocument(CollectionImportDocument.read(file, documentReader()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    default ImportedCollection importCollection(Path file) {
        return CollectionImportDocument.validated(importDocument(CollectionImportDocument.read(file, documentReader())));
    }
}
