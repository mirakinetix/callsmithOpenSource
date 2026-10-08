package com.mirakinetix.callsmith.application.collection.importation;

import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;

public interface CollectionImportPersistenceService {

    void persist(
            ImportedCollection collection
    );
}