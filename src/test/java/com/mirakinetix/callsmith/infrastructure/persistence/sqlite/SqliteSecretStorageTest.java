package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.mirakinetix.callsmith.application.environment.EnvironmentVariableResolver;
import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;
import com.mirakinetix.callsmith.infrastructure.persistence.CallsmithDatabase;
import com.mirakinetix.callsmith.infrastructure.persistence.migration.MigrationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SqliteSecretStorageTest {
    private static final String FICTIONAL_TOKEN = "demo-token-with-accent-" + (char) 233;
    private static final String LEGACY_TOKEN = "fictional-legacy-token-to-migrate";
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-01-02T00:00:00Z");
    @TempDir
    Path temporaryDirectory;

    @Test
    void encryptsBeforeSavingAndPreservesOrdinaryVariablesAndTimestamps() throws Exception {
        var database = freshDatabase("new.db");
        var storage = new SqliteSecretStorage(database);
        var repository = repository(database, storage);
        var secret = variable("secret", "key", FICTIONAL_TOKEN, true);
        assertThrows(IllegalStateException.class, () -> repository.save(secret));
        assertTrue(repository.findById(secret.id()).isEmpty());

        storage.initialize(passphrase());
        repository.save(secret);
        repository.save(variable("ordinary", "host", "localhost", false));
        var firstCiphertext = storedValue(database, "secret");
        assertFalse(firstCiphertext.contains(FICTIONAL_TOKEN));
        assertEquals("localhost", storedValue(database, "ordinary"));
        assertEquals("1", cell(database, "SELECT protection_version FROM environment_variables WHERE id='secret'"));
        assertEquals(secret, repository.findById("secret").orElseThrow());

        repository.save(secret);
        assertNotEquals(firstCiphertext, storedValue(database, "secret"));
        var changed = variable("secret", "new-key", "", true);
        repository.save(changed);
        assertEquals(changed, repository.findById("secret").orElseThrow());

        assertThrows(IllegalStateException.class,
                () -> repository.save(variable("secret", "new-key", "fictional", false)));
        repository.deleteById("secret");
        assertTrue(repository.findById("secret").isEmpty());
    }

    @Test
    void restartsLockedAndResolvesOnlyReferencedVariablesAfterUnlock() throws Exception {
        var database = freshDatabase("restart.db");
        var original = new SqliteSecretStorage(database);
        original.initialize(passphrase());
        repository(database, original).save(variable("secret", "key", FICTIONAL_TOKEN, true));
        repository(database, original).save(variable("ordinary", "host", "localhost", false));

        var reopened = new SqliteDatabase(database.databasePath());
        var storage = new SqliteSecretStorage(reopened);
        var repository = repository(reopened, storage);
        var resolver = new EnvironmentVariableResolver(repository);
        assertTrue(storage.isConfigured());
        assertFalse(storage.isUnlocked());
        var secretSummary = repository.findSummariesByEnvironmentId("env").getLast();
        assertTrue(secretSummary.secret());
        assertNull(secretSummary.value());
        assertEquals("localhost", resolver.resolve("{{host}}", "env"));
        assertThrows(IllegalStateException.class, () -> resolver.resolve("{{key}}", "env"));
        assertThrows(IllegalStateException.class, () -> storage.unlock("incorrect fixture password".toCharArray()));
        assertFalse(storage.isUnlocked());
        storage.unlock(passphrase());
        assertEquals("Bearer " + FICTIONAL_TOKEN, resolver.resolve("Bearer {{key}}", "env"));
        storage.lock();
        assertThrows(IllegalStateException.class, () -> repository.findById("secret"));
    }

    @Test
    void rejectsTamperingTruncationAndRowSwaps() throws Exception {
        var database = freshDatabase("tampered.db");
        var storage = new SqliteSecretStorage(database);
        storage.initialize(passphrase());
        var repository = repository(database, storage);
        repository.save(variable("a", "first", FICTIONAL_TOKEN, true));
        repository.save(variable("b", "second", "another-demo-value", true));
        byte[] altered = Base64.getDecoder().decode(storedValue(database, "a"));
        altered[altered.length - 1] ^= 1;
        setStoredValue(database, "a", Base64.getEncoder().encodeToString(altered));
        var exception = assertThrows(IllegalStateException.class, () -> repository.findById("a"));
        assertFalse(exception.toString().contains(FICTIONAL_TOKEN));
        assertNull(exception.getCause());
        setStoredValue(database, "a", "AAAA");
        assertThrows(IllegalStateException.class, () -> repository.findById("a"));
        setStoredValue(database, "a", storedValue(database, "b"));
        assertThrows(IllegalStateException.class, () -> repository.findById("a"));
    }

    @Test
    void rejectsUnsupportedVersionsMissingMetadataAndCorruptRecords() throws Exception {
        var database = freshDatabase("versions.db");
        var storage = new SqliteSecretStorage(database);
        storage.initialize(passphrase());
        var repository = repository(database, storage);
        repository.save(variable("a", "first", FICTIONAL_TOKEN, true));
        execute(database, "UPDATE environment_variables SET protection_version=77");
        assertTrue(assertThrows(IllegalStateException.class, () -> repository.findById("a"))
                .getMessage().contains("Unsupported"));
        execute(database, "UPDATE environment_variables SET protection_version='bad'");
        assertThrows(IllegalStateException.class, () -> repository.findById("a"));
        execute(database, "UPDATE environment_variables SET protection_version=1, is_secret=0");
        assertThrows(IllegalStateException.class, () -> repository.findById("a"));
        execute(database, "UPDATE environment_variables SET is_secret=1");
        execute(database, "UPDATE secret_storage SET format_version=77");
        storage.lock();
        assertTrue(assertThrows(IllegalStateException.class, () -> storage.unlock(passphrase()))
                .getMessage().contains("Unsupported"));
        execute(database, "DELETE FROM secret_storage");
        assertThrows(IllegalStateException.class, () -> storage.unlock(passphrase()));
        var encrypted = storedValue(database, "a");
        assertThrows(IllegalStateException.class, () -> storage.initialize(passphrase()));
        assertEquals(encrypted, storedValue(database, "a"));
    }

    @Test
    void upgradesVersionTenCopyAndRestoresFromCompactedSnapshot() throws Exception {
        var source = legacyDatabase();
        Path copy = temporaryDirectory.resolve("migration-copy.db");
        Files.copy(source.databasePath(), copy);
        var database = new SqliteDatabase(copy);
        initializeSchema(database);
        var storage = new SqliteSecretStorage(database);
        var repository = repository(database, storage);
        assertFalse(storage.isConfigured());
        assertEquals(LEGACY_TOKEN, storedValue(database, "legacy"));
        var summary = repository.findSummariesByEnvironmentId("env").stream()
                .filter(variable -> variable.id().equals("legacy")).findFirst().orElseThrow();
        assertTrue(summary.secret());
        assertNull(summary.value());
        assertThrows(IllegalStateException.class, () -> repository.findById("legacy"));
        assertThrows(IllegalStateException.class, () -> storage.unlock(passphrase()));

        storage.initialize(passphrase());
        String protectedValue = storedValue(database, "legacy");
        assertNotEquals(LEGACY_TOKEN, protectedValue);
        assertEquals("plain-fixture-value", storedValue(database, "ordinary"));
        var expected = variable("legacy", "legacy-key", LEGACY_TOKEN, true);
        assertEquals(expected, repository.findById("legacy").orElseThrow());
        storage.initialize(passphrase());
        assertEquals(protectedValue, storedValue(database, "legacy"));
        assertEquals(LEGACY_TOKEN, cell(source, "SELECT value FROM environment_variables WHERE id='legacy'"));

        Path backup = temporaryDirectory.resolve("restored.db");
        try (var connection = database.openConnection();
             var snapshot = connection.prepareStatement("VACUUM INTO ?")) {
            snapshot.setString(1, backup.toString());
            snapshot.execute();
        }
        assertFalse(new String(Files.readAllBytes(backup), java.nio.charset.StandardCharsets.ISO_8859_1)
                .contains(LEGACY_TOKEN));
        var restored = new SqliteDatabase(backup);
        assertEquals("ok", cell(restored, "PRAGMA integrity_check"));
        var backupStorage = new SqliteSecretStorage(restored);
        assertThrows(IllegalStateException.class, () -> backupStorage.unlock("wrong recovery passphrase".toCharArray()));
        backupStorage.unlock(passphrase());
        assertEquals(expected, repository(restored, backupStorage).findById("legacy").orElseThrow());
    }

    @Test
    void rollsBackPartialMigrationAndRetriesWithoutChangingBackup() throws Exception {
        var source = legacyDatabase();
        Path copy = temporaryDirectory.resolve("interrupted-copy.db");
        Files.copy(source.databasePath(), copy);
        var database = new SqliteDatabase(copy);
        initializeSchema(database);
        execute(database, """
                CREATE TRIGGER fail_secret_setup BEFORE INSERT ON secret_storage
                BEGIN SELECT RAISE(ABORT, 'simulated write failure'); END
                """);
        var storage = new SqliteSecretStorage(database);
        assertThrows(IllegalStateException.class, () -> storage.initialize(passphrase()));
        assertFalse(storage.isConfigured());
        assertFalse(storage.isUnlocked());
        assertEquals(LEGACY_TOKEN, storedValue(database, "legacy"));
        assertEquals("0", cell(database, "SELECT protection_version FROM environment_variables WHERE id='legacy'"));
        execute(database, "DROP TRIGGER fail_secret_setup");

        execute(database, "UPDATE environment_variables SET updated_at='malformed' WHERE id='legacy'");
        assertThrows(IllegalStateException.class, () -> storage.initialize(passphrase()));
        assertEquals(LEGACY_TOKEN, storedValue(database, "legacy"));
        execute(database, "UPDATE environment_variables SET updated_at='" + UPDATED + "' WHERE id='legacy'");
        storage.initialize(passphrase());
        assertEquals(LEGACY_TOKEN, repository(database, storage).findById("legacy").orElseThrow().value());
        assertEquals(LEGACY_TOKEN, cell(source, "SELECT value FROM environment_variables WHERE id='legacy'"));
    }

    @Test
    void legacyWriterCannotOverwriteProtectedRow() throws Exception {
        var database = freshDatabase("downgrade.db");
        var storage = new SqliteSecretStorage(database);
        storage.initialize(passphrase());
        repository(database, storage).save(variable("a", "key", FICTIONAL_TOKEN, true));
        assertThrows(SQLException.class, () ->
                execute(database, "UPDATE environment_variables SET value='fictional' WHERE id='a'"));
        assertEquals(FICTIONAL_TOKEN, repository(database, storage).findById("a").orElseThrow().value());
    }

    private SqliteDatabase freshDatabase(String file) throws SQLException {
        var database = new SqliteDatabase(temporaryDirectory.resolve(file));
        initializeSchema(database);
        insertEnvironment(database);
        return database;
    }

    private SqliteDatabase legacyDatabase() throws Exception {
        var database = new SqliteDatabase(temporaryDirectory.resolve("legacy.db"));
        String[] migrations = {
                "V1__initial_schema.sql", "V2__create_collection_folders.sql", "V3__create_saved_requests.sql",
                "V4__create_environments.sql", "V5__create_environment_variables.sql",
                "V6__create_collection_folders_collection_index.sql", "V7__create_collection_folders_parent_index.sql",
                "V8__create_saved_requests_collection_index.sql", "V9__create_saved_requests_folder_index.sql",
                "V10__create_environment_variables_environment_index.sql"
        };
        execute(database, "CREATE TABLE schema_migrations (version INTEGER PRIMARY KEY, name TEXT, applied_at TEXT)");
        for (int index = 0; index < migrations.length; index++) {
            try (var input = getClass().getResourceAsStream("/db/migration/" + migrations[index])) {
                execute(database, new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            execute(database, "INSERT INTO schema_migrations VALUES ("
                    + (index + 1) + ", '" + migrations[index] + "', '" + CREATED + "')");
        }
        insertEnvironment(database);
        try (var connection = database.openConnection();
             var insert = connection.prepareStatement("""
                     INSERT INTO environment_variables (id, environment_id, name, value, is_secret, created_at, updated_at)
                     VALUES ('legacy', 'env', 'legacy-key', ?, 1, ?, ?),
                            ('ordinary', 'env', 'host', 'plain-fixture-value', 0, ?, ?)
                     """)) {
            insert.setString(1, LEGACY_TOKEN);
            insert.setString(2, CREATED.toString());
            insert.setString(3, UPDATED.toString());
            insert.setString(4, CREATED.toString());
            insert.setString(5, UPDATED.toString());
            insert.executeUpdate();
        }
        return database;
    }

    private static void insertEnvironment(SqliteDatabase database) throws SQLException {
        execute(database, "INSERT INTO environments (id, name, created_at, updated_at) "
                + "VALUES ('env', 'Demo', '" + CREATED + "', '" + UPDATED + "')");
    }

    private static void initializeSchema(SqliteDatabase database) {
        new CallsmithDatabase(database, new MigrationRunner()).initialize();
    }

    private static SqliteEnvironmentVariableRepository repository(SqliteDatabase db, SqliteSecretStorage store) {
        return new SqliteEnvironmentVariableRepository(db, store);
    }

    private static EnvironmentVariable variable(String id, String name, String value, boolean secret) {
        return new EnvironmentVariable(id, "env", name, value, secret, CREATED, UPDATED);
    }

    private static char[] passphrase() {
        return "fixture-only workspace passphrase".toCharArray();
    }

    private static void execute(SqliteDatabase database, String sql) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String cell(SqliteDatabase database, String sql) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            return rows.getString(1);
        }
    }

    private static String storedValue(SqliteDatabase database, String id) throws SQLException {
        return cell(database, "SELECT stored_value FROM environment_variables WHERE id='" + id + "'");
    }

    private static void setStoredValue(SqliteDatabase database, String id, String value) throws SQLException {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("UPDATE environment_variables SET stored_value=? WHERE id=?")) {
            statement.setString(1, value);
            statement.setString(2, id);
            statement.executeUpdate();
        }
    }
}
