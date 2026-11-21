package com.mirakinetix.callsmith.domain.http;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

public final class SelectedUpload {
    private static final long MAX_BYTES = 128L * 1024 * 1024;
    private final Path path;
    private final String field;
    private final long size;
    private final FileTime modified;
    private final FileTime created;
    private final Object fileKey;

    private SelectedUpload(Path path, String field, BasicFileAttributes attributes) {
        this.path = path;
        this.field = field;
        size = attributes.size();
        modified = attributes.lastModifiedTime();
        created = attributes.creationTime();
        fileKey = attributes.fileKey();
    }

    /** Call only with a path chosen explicitly by the user. */
    public static SelectedUpload select(Path path, String field) {
        try {
            Path absolute = path.toAbsolutePath().normalize();
            BasicFileAttributes attributes = attributes(absolute);
            if (!attributes.isRegularFile() || attributes.size() > MAX_BYTES) {
                throw new IllegalStateException("Choose a regular upload file of at most 128 MiB.");
            }
            return new SelectedUpload(absolute, field, attributes);
        } catch (IOException exception) {
            throw new IllegalStateException("The selected upload file could not be read.");
        }
    }

    public SelectedUpload withField(String name) {
        return new SelectedUpload(this, Objects.requireNonNull(name));
    }

    private SelectedUpload(SelectedUpload selected, String field) {
        path = selected.path;
        this.field = field;
        size = selected.size;
        modified = selected.modified;
        created = selected.created;
        fileKey = selected.fileKey;
    }

    public String field() { return field; }
    public String filename() { return path.getFileName().toString(); }
    public long size() { return size; }

    public InputStream open() throws IOException {
        verify();
        InputStream stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
        try {
            verify();
            return new CheckedUploadStream(stream);
        } catch (IOException | RuntimeException exception) {
            stream.close();
            throw exception;
        }
    }

    private final class CheckedUploadStream extends FilterInputStream {
        private long remaining = size;

        private CheckedUploadStream(InputStream stream) {
            super(stream);
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value == -1) {
                verifyEnd();
            } else if (remaining-- == 0) {
                throw changed();
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, buffer.length);
            if (length == 0) return 0;
            if (remaining == 0) return read();
            int count = in.read(buffer, offset, (int) Math.min(length, remaining));
            if (count == -1) verifyEnd();
            else remaining -= count;
            return count;
        }

        private void verifyEnd() throws IOException {
            if (remaining != 0) throw changed();
            try {
                verify();
            } catch (IOException exception) {
                throw changed();
            }
        }

        private IOException changed() {
            return new IOException("Upload changed", new HttpFailure(HttpFailure.Kind.UPLOAD));
        }
    }

    public void verify() throws IOException {
        BasicFileAttributes attributes = attributes(path);
        if (!attributes.isRegularFile() || attributes.size() != size
                || !modified.equals(attributes.lastModifiedTime()) || !created.equals(attributes.creationTime())
                || !Objects.equals(fileKey, attributes.fileKey())) {
            throw new IOException("Upload changed");
        }
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    @Override public String toString() { return "SelectedUpload"; }
}
