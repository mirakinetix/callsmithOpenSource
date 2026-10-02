package com.mirakinetix.callsmith.application.collection;

import com.mirakinetix.callsmith.domain.collection.CollectionFolder;

import java.util.List;
import java.util.Optional;

public interface CollectionFolderRepository {

    CollectionFolder save(CollectionFolder folder);

    Optional<CollectionFolder> findById(String id);

    List<CollectionFolder> findByCollectionId(
            String collectionId
    );

    void deleteById(String id);
}