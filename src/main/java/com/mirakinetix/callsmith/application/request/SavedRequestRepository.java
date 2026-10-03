package com.mirakinetix.callsmith.application.request;

import com.mirakinetix.callsmith.domain.request.SavedRequest;

import java.util.List;
import java.util.Optional;

public interface SavedRequestRepository {

    SavedRequest save(SavedRequest request);

    Optional<SavedRequest> findById(String id);

    List<SavedRequest> findByCollectionId(
            String collectionId
    );

    List<SavedRequest> findByFolderId(
            String folderId
    );

    void deleteById(String id);
}