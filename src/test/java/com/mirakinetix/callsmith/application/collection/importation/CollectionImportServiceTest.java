package com.mirakinetix.callsmith.application.collection.importation;

import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionImportServiceTest {

    @Test
    void shouldRejectEmptyImporterList() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new CollectionImportService(
                                List.of()
                        )
                );

        assertEquals(
                "At least one collection importer is required",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullImporterList() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new CollectionImportService(
                                null
                        )
                );

        assertEquals(
                "At least one collection importer is required",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectNullFile() {
        CollectionImporter importer =
                mock(CollectionImporter.class);

        CollectionImportService service =
                new CollectionImportService(
                        List.of(importer)
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.importCollection(null)
                );

        assertEquals(
                "Import file is required",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectUnsupportedFormat() {
        Path file =
                Path.of("collection.json");

        CollectionImporter importer =
                mock(CollectionImporter.class);

        when(importer.supports(file))
                .thenReturn(false);

        CollectionImportService service =
                new CollectionImportService(
                        List.of(importer)
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> service.importCollection(file)
                );

        assertEquals(
                "Unsupported collection format: collection.json",
                exception.getMessage()
        );

        verify(importer).supports(file);
    }

    @Test
    void shouldUseImporterThatSupportsFile() {
        Path file =
                Path.of("collection.json");

        CollectionImporter unsupportedImporter =
                mock(CollectionImporter.class);

        CollectionImporter supportedImporter =
                mock(CollectionImporter.class);

        ImportedCollection importedCollection =
                new ImportedCollection(
                        "Imported Collection",
                        List.of(),
                        List.of()
                );

        when(unsupportedImporter.supports(file))
                .thenReturn(false);

        when(supportedImporter.supports(file))
                .thenReturn(true);

        when(supportedImporter.importCollection(file))
                .thenReturn(importedCollection);

        CollectionImportService service =
                new CollectionImportService(
                        List.of(
                                unsupportedImporter,
                                supportedImporter
                        )
                );

        ImportedCollection result =
                service.importCollection(file);

        assertEquals(
                importedCollection,
                result
        );

        verify(unsupportedImporter).supports(file);
        verify(supportedImporter).supports(file);
        verify(supportedImporter)
                .importCollection(file);
    }

    @Test
    void shouldUseFirstSupportingImporter() {
        Path file =
                Path.of("collection.json");

        CollectionImporter firstImporter =
                mock(CollectionImporter.class);

        CollectionImporter secondImporter =
                mock(CollectionImporter.class);

        ImportedCollection firstCollection =
                new ImportedCollection(
                        "First",
                        List.of(),
                        List.of()
                );

        when(firstImporter.supports(file))
                .thenReturn(true);

        when(secondImporter.supports(file))
                .thenReturn(true);

        when(firstImporter.importCollection(file))
                .thenReturn(firstCollection);

        CollectionImportService service =
                new CollectionImportService(
                        List.of(
                                firstImporter,
                                secondImporter
                        )
                );

        ImportedCollection result =
                service.importCollection(file);

        assertEquals(
                firstCollection,
                result
        );

        verify(firstImporter)
                .importCollection(file);
    }
}