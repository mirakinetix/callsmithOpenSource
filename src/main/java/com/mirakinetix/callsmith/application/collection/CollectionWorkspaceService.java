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

    public SavedRequest saveRequest(SavedRequest request) {
        validateDestination(request.collectionId(), request.folderId());
        return savedRequestRepository.save(request);
    }

    public void deleteRequest(String id) {
        savedRequestRepository.deleteById(id);
    }

    public CollectionFolder createFolder(String collectionId, String parentId, String name) {
        validateDestination(collectionId, parentId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A folder name is required");
        }
        Instant now = Instant.now();
        return folderRepository.save(new CollectionFolder(
                java.util.UUID.randomUUID().toString(), collectionId, parentId,
                name.trim(), 0, now, now));
    }

    public void renameFolder(String id, String name) {
        CollectionFolder folder = requireFolder(id);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A folder name is required");
        }
        folderRepository.save(new CollectionFolder(folder.id(), folder.collectionId(),
                folder.parentFolderId(), name.trim(), folder.sortOrder(), folder.createdAt(), Instant.now()));
    }

    public void deleteEmptyFolder(String id) {
        CollectionFolder folder = requireFolder(id);
        boolean hasChildren = folderRepository.findByCollectionId(folder.collectionId()).stream()
                .anyMatch(child -> id.equals(child.parentFolderId()));
        if (hasChildren || !savedRequestRepository.findByFolderId(id).isEmpty()) {
            throw new IllegalArgumentException("Move the folder's contents before deleting it");
        }
        folderRepository.deleteById(id);
    }

    private CollectionFolder requireFolder(String id) {
        return folderRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException("This folder no longer exists"));
    }

    private void validateDestination(String collectionId, String folderId) {
        if (collectionRepository.findById(collectionId).isEmpty()) {
            throw new IllegalArgumentException("The destination collection no longer exists");
        }
        if (folderId != null && folderRepository.findById(folderId)
                .filter(folder -> folder.collectionId().equals(collectionId)).isEmpty()) {
            throw new IllegalArgumentException("The destination folder is not in this collection");
        }
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