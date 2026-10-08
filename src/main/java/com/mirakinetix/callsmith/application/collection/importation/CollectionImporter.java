package com.mirakinetix.callsmith.application.collection.importation;

import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;

import java.nio.file.Path;

public interface CollectionImporter {

    boolean supports(Path file);

    ImportedCollection importCollection(Path file);
}