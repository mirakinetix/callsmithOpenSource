package com.mirakinetix.callsmith.application.collection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CollectionExportDestinationTest {
    @TempDir Path directory;

    @Test
    void writesNewTargetsWithOrWithoutJsonSuffix() throws IOException {
        for (String original : new String[]{"one.json", "two"}) {
            var target = CollectionExportDestination.prepare(directory.resolve(original));
            assertFalse(target.requiresConfirmation());
            assertTrue(target.path().getFileName().toString().endsWith(".json"));
            target.write(staged -> Files.writeString(staged, "{}"));
            assertEquals("{}", Files.readString(target.path()));
        }
    }

    @Test
    void replacesConfirmedNormalizedTargetAndKeepsOriginalOnWriteFailure() throws IOException {
        var file = Files.writeString(directory.resolve("existing.json"), "old content");
        var target = CollectionExportDestination.prepare(directory.resolve("existing"));
        assertTrue(target.requiresConfirmation());
        assertEquals(file, target.path());
        assertThrows(IOException.class, () -> target.write(staged -> fail("Unapproved writer ran")));
        var approved = target.approveReplacement();
        assertThrows(IOException.class, () -> approved.write(staged -> {
            Files.writeString(staged, "partial data");
            throw new IOException("Interrupted fixture write");
        }));
        assertEquals("old content", Files.readString(file));
        approved.write(staged -> Files.writeString(staged, "new content"));
        assertEquals("new content", Files.readString(file));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void preservesFileCreatedAfterSelectionEvenDuringStaging() throws IOException {
        var target = CollectionExportDestination.prepare(directory.resolve("race"));
        assertFalse(target.requiresConfirmation());
        assertThrows(FileAlreadyExistsException.class, () -> target.write(staged -> {
            Files.writeString(staged, "candidate");
            Files.writeString(target.path(), "concurrent file");
        }));
        assertEquals("concurrent file", Files.readString(target.path()));
    }

    @Test
    void refusesReplacementAfterFileChangesDuringStaging() throws IOException {
        var file = Files.writeString(directory.resolve("race.json"), "old");
        var authorized = CollectionExportDestination.prepare(file).approveReplacement();
        assertThrows(IOException.class, () -> authorized.write(staged -> {
            Files.writeString(staged, "candidate");
            Files.writeString(file, "changed original");
        }));
        assertEquals("changed original", Files.readString(file));
    }

    @Test
    void rejectsDirectoriesAndInvalidParents() throws IOException {
        var directoryTarget = Files.createDirectory(directory.resolve("folder.json"));
        assertThrows(IllegalArgumentException.class, () -> CollectionExportDestination.prepare(directoryTarget));
        var missingParent = CollectionExportDestination.prepare(directory.resolve("missing").resolve("export"));
        assertThrows(IOException.class, () -> missingParent.write(staged -> Files.writeString(staged, "{}")));
        assertFalse(Files.exists(missingParent.path()));
    }
}
