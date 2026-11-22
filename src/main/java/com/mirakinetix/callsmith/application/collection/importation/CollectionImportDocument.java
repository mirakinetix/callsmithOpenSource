package com.mirakinetix.callsmith.application.collection.importation;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedCollection;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedFolder;
import com.mirakinetix.callsmith.domain.collection.importation.ImportedRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CollectionImportDocument {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_RESOURCES = 5_000;
    public static final int MAX_FOLDER_DEPTH = 64;
    private static final JsonFactory FACTORY = JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(192)
                    .maxTokenCount(250_000)
                    .maxStringLength(MAX_BYTES)
                    .build())
            .build();
    private static final ObjectReader READER = new ObjectMapper().reader();

    private CollectionImportDocument() {}

    static boolean isJsonFile(Path file) {
        return file != null && file.getFileName() != null
                && file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json")
                && Files.isRegularFile(file);
    }

    static JsonNode read(Path file) {
        return read(file, READER);
    }

    static JsonNode read(Path file, ObjectReader reader) {
        if (file == null) throw new IllegalArgumentException("Import file is required");
        if (!isJsonFile(file)) throw new IllegalArgumentException("Select a readable JSON collection file.");

        byte[] bytes;
        try {
            if (Files.size(file) > MAX_BYTES) throw oversized();
            try (var input = Files.newInputStream(file)) {
                // Limit the opened stream as well, in case the file changes after the size check.
                bytes = input.readNBytes(MAX_BYTES + 1);
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the collection file.");
        }
        if (bytes.length > MAX_BYTES) throw oversized();

        try (var parser = FACTORY.createParser(bytes)) {
            JsonNode root = reader.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(parser);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("A collection must be a JSON object.");
            }
            return root;
        } catch (StreamConstraintsException exception) {
            throw new IllegalArgumentException("Collection JSON exceeds the parsing size or nesting limit.");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Malformed collection JSON.");
        }
    }

    public static void checkResourceCount(int count) {
        if (count > MAX_RESOURCES) {
            throw new IllegalArgumentException("A collection import allows at most 5,000 resources.");
        }
    }

    public static void checkFolderDepth(int depth) {
        if (depth > MAX_FOLDER_DEPTH) {
            throw new IllegalArgumentException("A collection import allows at most 64 folder levels.");
        }
    }

    static ImportedCollection validated(ImportedCollection collection) {
        var budget = new ResourceBudget();
        budget.visit(collection.folders(), collection.requests(), 1);
        return collection;
    }

    private static final class ResourceBudget {
        private int count;
        private final TextBudget text = new TextBudget();

        void visit(List<ImportedFolder> folders, List<ImportedRequest> requests, int depth) {
            count += folders.size() + requests.size();
            checkResourceCount(count);
            if (!folders.isEmpty()) checkFolderDepth(depth);
            requests.forEach(text::include);
            for (var folder : folders) {
                text.charge(folder.name());
                visit(folder.folders(), folder.requests(), depth + 1);
            }
        }
    }

    public static final class TextBudget {
        private long textRemaining = 16L * 1024 * 1024;

        public void include(ImportedRequest request) {
            charge(request.name(), request.method(), request.url(), request.body());
            request.headers().forEach((name, value) -> charge(name, value));
            request.queryParameters().forEach((name, value) -> charge(name, value));
        }

        private void charge(String... fields) {
            for (var field : fields) {
                if (field != null) textRemaining -= field.length();
            }
            if (textRemaining < 0) throw new IllegalArgumentException("Imported collection data exceeds the size limit.");
        }
    }

    private static IllegalArgumentException oversized() {
        return new IllegalArgumentException("Collection files are limited to 8 MiB.");
    }
}
