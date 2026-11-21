package com.mirakinetix.callsmith.application.collection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Objects;

public final class CollectionExportDestination {
    private final Path path;
    private final BasicFileAttributes existing;
    private final boolean replacementApproved;

    private CollectionExportDestination(Path path, BasicFileAttributes existing, boolean replacementApproved) {
        this.path = path;
        this.existing = existing;
        this.replacementApproved = replacementApproved;
    }

    public static CollectionExportDestination prepare(Path selected) {
        if (selected == null || selected.getFileName() == null) {
            throw new IllegalArgumentException("Export file is required");
        }
        String name = selected.getFileName().toString();
        Path finalPath = name.toLowerCase(Locale.ROOT).endsWith(".json")
                ? selected : selected.resolveSibling(name + ".json");
        Path target = finalPath.toAbsolutePath().normalize();
        try {
            return new CollectionExportDestination(target, readExisting(target), false);
        } catch (NoSuchFileException exception) {
            return new CollectionExportDestination(target, null, false);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to use the export destination " + target + ". "
                    + exception.getMessage(), exception);
        }
    }

    public Path path() {
        return path;
    }

    public boolean requiresConfirmation() {
        return existing != null;
    }

    CollectionExportDestination approveReplacement() {
        return new CollectionExportDestination(path, existing, true);
    }

    public void write(StagedWriter writer) throws IOException {
        if (requiresConfirmation() && !replacementApproved) {
            throw new IOException("Replacement of the export destination was not confirmed.");
        }
        Path work = Files.createTempDirectory(path.getParent(), ".callsmith-export-");
        try {
            // Use the platform's private temporary file permissions for the final export content.
            Path staged = Files.createTempFile(work, "collection-", ".json");
            try {
                writer.write(staged);
                if (Thread.currentThread().isInterrupted()) throw new IOException("Export was interrupted.");
                if (existing == null) {
                    Files.createLink(path, staged);
                } else {
                    replace(staged, work.resolve("previous.json"));
                }
            } finally {
                clean(staged);
            }
        } catch (UnsupportedOperationException exception) {
            throw new IOException("This filesystem cannot safely publish the export. Choose another location.", exception);
        } finally {
            clean(work); // A backup left after a failed restore keeps this directory non-empty.
        }
    }

    private void replace(Path staged, Path previous) throws IOException {
        verifyExisting(path);
        // Claim the current file, verify what was actually moved, then publish without clobbering a new arrival.
        Files.move(path, previous, StandardCopyOption.ATOMIC_MOVE);
        try {
            verifyExisting(previous);
            if (Thread.currentThread().isInterrupted()) throw new IOException("Export was interrupted.");
            Files.createLink(path, staged);
        } catch (IOException | RuntimeException failure) {
            try {
                readExisting(previous);
                Files.createLink(path, previous);
                clean(previous);
            } catch (IOException | RuntimeException restoreFailure) {
                var preserved = new IOException("The previous file was preserved at " + previous
                        + ". The destination could not be safely restored.", failure);
                preserved.addSuppressed(restoreFailure);
                throw preserved;
            }
            throw failure;
        }
        clean(previous);
    }

    private void verifyExisting(Path candidate) throws IOException {
        BasicFileAttributes actual = readExisting(candidate);
        if (actual.size() != existing.size() || !actual.lastModifiedTime().equals(existing.lastModifiedTime())
                || !actual.creationTime().equals(existing.creationTime())
                || !Objects.equals(actual.fileKey(), existing.fileKey())) {
            throw new IOException("The export destination changed. Select and confirm it again.");
        }
    }

    private static BasicFileAttributes readExisting(Path path) throws IOException {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()) throw new IOException("The destination must be a regular file.");
        return attributes;
    }

    private static void clean(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Cleanup must not remove a competing file or change the result of a published export.
        }
    }

    @FunctionalInterface
    public interface StagedWriter {
        void write(Path staged) throws IOException;
    }
}
