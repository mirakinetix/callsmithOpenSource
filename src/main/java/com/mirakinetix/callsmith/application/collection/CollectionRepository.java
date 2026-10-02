package com.mirakinetix.callsmith.application.collection;

import com.mirakinetix.callsmith.domain.collection.Collection;

import java.util.List;
import java.util.Optional;

public interface CollectionRepository {

    Collection save(Collection collection);

    Optional<Collection> findById(String id);

    List<Collection> findAll();

    void deleteById(String id);
}