package com.mirakinetix.callsmith.application.collection;

import com.mirakinetix.callsmith.application.request.SavedRequestRepository;
import com.mirakinetix.callsmith.domain.collection.Collection;
import com.mirakinetix.callsmith.domain.collection.CollectionFolder;
import com.mirakinetix.callsmith.domain.request.SavedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionWorkspaceServiceTest {

    private CollectionRepository collectionRepository;
    private CollectionFolderRepository folderRepository;
    private SavedRequestRepository savedRequestRepository;

    private CollectionWorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        collectionRepository =
                mock(CollectionRepository.class);

        folderRepository =
                mock(CollectionFolderRepository.class);

        savedRequestRepository =
                mock(SavedRequestRepository.class);

        workspaceService =
                new CollectionWorkspaceService(
                        collectionRepository,
                        folderRepository,
                        savedRequestRepository
                );
    }

    @Test
    void shouldFindCollections() {
        Collection first =
                createCollection(
                        "collection-1",
                        "Alpha API"
                );

        Collection second =
                createCollection(
                        "collection-2",
                        "Beta API"
                );

        when(collectionRepository.findAll())
                .thenReturn(
                        List.of(
                                first,
                                second
                        )
                );

        List<Collection> collections =
                workspaceService.findCollections();

        assertEquals(
                List.of(
                        first,
                        second
                ),
                collections
        );

        verify(
                collectionRepository
        ).findAll();
    }

    @Test
    void shouldFindSavedRequest() {
        SavedRequest request =
                createRequest(
                        "request-1",
                        "collection-1",
                        null,
                        "Get Users"
                );

        when(savedRequestRepository.findById(
                "request-1"
        )).thenReturn(
                Optional.of(request)
        );

        Optional<SavedRequest> found =
                workspaceService.findSavedRequest(
                        "request-1"
                );

        assertTrue(found.isPresent());

        assertEquals(
                request,
                found.orElseThrow()
        );

        verify(
                savedRequestRepository
        ).findById("request-1");
    }

    @Test
    void shouldReturnEmptyForBlankSavedRequestId() {
        Optional<SavedRequest> found =
                workspaceService.findSavedRequest(
                        "   "
                );

        assertFalse(found.isPresent());
    }

    @Test
    void shouldReturnEmptyForNullSavedRequestId() {
        Optional<SavedRequest> found =
                workspaceService.findSavedRequest(
                        null
                );

        assertFalse(found.isPresent());
    }

    @Test
    void shouldLoadCollectionWithRootRequestsAndFolders() {
        Collection collection =
                createCollection(
                        "collection-1",
                        "Users API"
                );

        CollectionFolder usersFolder =
                new CollectionFolder(
                        "folder-users",
                        "collection-1",
                        null,
                        "Users",
                        0,
                        timestamp(),
                        timestamp()
                );

        CollectionFolder adminFolder =
                new CollectionFolder(
                        "folder-admin",
                        "collection-1",
                        "folder-users",
                        "Admin",
                        0,
                        timestamp(),
                        timestamp()
                );

        SavedRequest rootRequest =
                createRequest(
                        "request-root",
                        "collection-1",
                        null,
                        "Health Check"
                );

        SavedRequest usersRequest =
                createRequest(
                        "request-users",
                        "collection-1",
                        "folder-users",
                        "Get Users"
                );

        SavedRequest adminRequest =
                createRequest(
                        "request-admin",
                        "collection-1",
                        "folder-admin",
                        "Delete User"
                );

        when(folderRepository.findByCollectionId(
                "collection-1"
        )).thenReturn(
                List.of(
                        usersFolder,
                        adminFolder
                )
        );

        when(savedRequestRepository.findByCollectionId(
                "collection-1"
        )).thenReturn(
                List.of(
                        rootRequest,
                        usersRequest,
                        adminRequest
                )
        );

        CollectionWorkspaceService.CollectionWorkspace workspace =
                workspaceService.loadCollection(
                        collection
                );

        assertEquals(
                collection,
                workspace.collection()
        );

        assertEquals(
                List.of(usersFolder),
                workspace.foldersByParent()
                        .get("")
        );

        assertEquals(
                List.of(adminFolder),
                workspace.foldersByParent()
                        .get("folder-users")
        );

        assertEquals(
                List.of(rootRequest),
                workspace.requestsByFolder()
                        .get("")
        );

        assertEquals(
                List.of(usersRequest),
                workspace.requestsByFolder()
                        .get("folder-users")
        );

        assertEquals(
                List.of(adminRequest),
                workspace.requestsByFolder()
                        .get("folder-admin")
        );

        verify(
                folderRepository
        ).findByCollectionId(
                "collection-1"
        );

        verify(
                savedRequestRepository
        ).findByCollectionId(
                "collection-1"
        );
    }

    @Test
    void shouldLoadEmptyCollection() {
        Collection collection =
                createCollection(
                        "collection-empty",
                        "Empty API"
                );

        when(folderRepository.findByCollectionId(
                "collection-empty"
        )).thenReturn(
                List.of()
        );

        when(savedRequestRepository.findByCollectionId(
                "collection-empty"
        )).thenReturn(
                List.of()
        );

        CollectionWorkspaceService.CollectionWorkspace workspace =
                workspaceService.loadCollection(
                        collection
                );

        assertEquals(
                collection,
                workspace.collection()
        );

        assertTrue(
                workspace.foldersByParent().isEmpty()
        );

        assertTrue(
                workspace.requestsByFolder().isEmpty()
        );
    }

    @Test
    void shouldRejectNullCollection() {
        assertThrows(
                IllegalArgumentException.class,
                () -> workspaceService.loadCollection(
                        null
                )
        );
    }

    private Collection createCollection(
            String id,
            String name
    ) {
        Instant timestamp =
                timestamp();

        return new Collection(
                id,
                name,
                timestamp,
                timestamp
        );
    }

    private SavedRequest createRequest(
            String id,
            String collectionId,
            String folderId,
            String name
    ) {
        Instant timestamp =
                timestamp();

        return new SavedRequest(
                id,
                collectionId,
                folderId,
                name,
                "GET",
                "https://example.com/api",
                Map.of(
                        "Accept",
                        "application/json"
                ),
                Map.of(),
                null,
                0,
                timestamp,
                timestamp
        );
    }

    private Instant timestamp() {
        return Instant.parse(
                "2026-10-05T12:00:00Z"
        );
    }
}