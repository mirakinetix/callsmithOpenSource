package com.mirakinetix.callsmith.application.collection;

import com.mirakinetix.callsmith.application.request.SavedRequestRepository;
import com.mirakinetix.callsmith.domain.collection.Collection;
import com.mirakinetix.callsmith.domain.collection.CollectionFolder;
import com.mirakinetix.callsmith.domain.request.SavedRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CollectionWorkspaceService {

    private final CollectionRepository collectionRepository;
    private final CollectionFolderRepository folderRepository;
    private final SavedRequestRepository savedRequestRepository;

    public CollectionWorkspaceService(
            CollectionRepository collectionRepository,
            CollectionFolderRepository folderRepository,
            SavedRequestRepository savedRequestRepository
    ) {
        if (collectionRepository == null) {
            throw new IllegalArgumentException(
                    "Collection repository is required"
            );
        }

        if (folderRepository == null) {
            throw new IllegalArgumentException(
                    "Collection folder repository is required"
            );
        }

        if (savedRequestRepository == null) {
            throw new IllegalArgumentException(
                    "Saved request repository is required"
            );
        }

        this.collectionRepository =
                collectionRepository;

        this.folderRepository =
                folderRepository;

        this.savedRequestRepository =
                savedRequestRepository;
    }

    public List<Collection> findCollections() {
        return collectionRepository.findAll();
    }

    public Collection saveCollection(
            Collection collection
    ) {
        if (collection == null) {
            throw new IllegalArgumentException(
                    "Collection is required"
            );
        }

        return collectionRepository.save(
                collection
        );
    }

    public void deleteCollection(
            String collectionId
    ) {
        if (collectionId == null
                || collectionId.isBlank()) {
            throw new IllegalArgumentException(
                    "Collection ID is required"
            );
        }

        collectionRepository.deleteById(
                collectionId
        );
    }

    public Optional<SavedRequest> findSavedRequest(
            String requestId
    ) {
        if (requestId == null || requestId.isBlank()) {
            return Optional.empty();
        }

        return savedRequestRepository.findById(
                requestId
        );
    }

    public CollectionWorkspace loadCollection(
            Collection collection
    ) {
        if (collection == null) {
            throw new IllegalArgumentException(
                    "Collection is required"
            );
        }

        List<CollectionFolder> folders =
                folderRepository.findByCollectionId(
                        collection.id()
                );

        List<SavedRequest> requests =
                savedRequestRepository.findByCollectionId(
                        collection.id()
                );

        Map<String, List<CollectionFolder>> foldersByParent =
                new LinkedHashMap<>();

        for (CollectionFolder folder : folders) {
            String parentFolderId =
                    folder.parentFolderId();

            String parentKey =
                    parentFolderId == null
                            ? ""
                            : parentFolderId;

            foldersByParent
                    .computeIfAbsent(
                            parentKey,
                            ignored -> new ArrayList<>()
                    )
                    .add(folder);
        }

        Map<String, List<SavedRequest>> requestsByFolder =
                new LinkedHashMap<>();

        for (SavedRequest request : requests) {
            String folderId =
                    request.folderId();

            String folderKey =
                    folderId == null
                            ? ""
                            : folderId;

            requestsByFolder
                    .computeIfAbsent(
                            folderKey,
                            ignored -> new ArrayList<>()
                    )
                    .add(request);
        }

        return new CollectionWorkspace(
                collection,
                foldersByParent,
                requestsByFolder
        );
    }

    public record CollectionWorkspace(
            Collection collection,
            Map<String, List<CollectionFolder>> foldersByParent,
            Map<String, List<SavedRequest>> requestsByFolder
    ) {
    }
}