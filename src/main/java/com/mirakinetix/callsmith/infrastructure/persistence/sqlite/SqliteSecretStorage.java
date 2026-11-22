package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.mirakinetix.callsmith.application.environment.SecretStorage;

import javax.crypto.SecretKey;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class SqliteSecretStorage implements SecretStorage {
    private static final String CHECK_VALUE = "Callsmith workspace key check";
    private final SqliteDatabase database;
    private volatile SecretKey key;

    public SqliteSecretStorage(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public boolean isConfigured() {
        try (var connection = database.openConnection()) {
            return readMetadata(connection) != null;
        } catch (SQLException exception) {
            throw storageFailure();
        }
    }

    @Override
    public boolean isUnlocked() {
        return key != null;
    }

    @Override
    public synchronized void lock() {
        key = null;
    }

    @Override
    public synchronized void unlock(char[] passphrase) {
        try (var connection = database.openConnection()) {
            var metadata = readMetadata(connection);
            if (metadata == null) {
                throw new IllegalStateException("Set up Secret Storage before unlocking it.");
            }
            key = verifiedKey(passphrase, metadata);
        } catch (SQLException exception) {
            throw storageFailure();
        }
    }

    @Override
    public synchronized void initialize(char[] passphrase) {
        if (passphrase == null || passphrase.length < 16 || passphrase.length > 1024) {
            throw new IllegalArgumentException("Use a strong passphrase of at least 16 characters (maximum 1024).");
        }
        try (var connection = database.openConnection();
             var transaction = connection.createStatement()) {
            transaction.execute("PRAGMA secure_delete = ON");
            transaction.execute("BEGIN IMMEDIATE");
            try {
                Metadata metadata = readMetadata(connection);
                SecretKey candidate;
                if (metadata != null) {
                    candidate = verifiedKey(passphrase, metadata);
                } else {
                    byte[] salt = SecretCipher.randomBytes(SecretCipher.SALT_BYTES);
                    candidate = SecretCipher.derive(passphrase, salt);
                    String verifier = SecretCipher.encrypt(candidate, verifierContext(salt), CHECK_VALUE);
                    metadata = new Metadata(salt, verifier);
                    migrateLegacy(connection, candidate);
                    saveMetadata(connection, metadata);
                }

                transaction.execute("COMMIT");
                key = candidate;
            } catch (SQLException | RuntimeException exception) {
                // Some errors, including SQLITE_FULL, may already have rolled back the transaction.
                try {
                    transaction.execute("ROLLBACK");
                } catch (SQLException ignored) {
                    // Closing the connection also releases any remaining uncommitted transaction.
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw storageFailure();
        }
    }

    String protect(String id, String environmentId, String name, String value) {
        return SecretCipher.encrypt(requireKey(), rowContext(id, environmentId, name), value);
    }

    String reveal(String id, String environmentId, String name, String value) {
        return SecretCipher.decrypt(requireKey(), rowContext(id, environmentId, name), value);
    }

    private SecretKey requireKey() {
        SecretKey current = key;
        if (current == null) {
            throw new IllegalStateException("Unlock Secret Storage in Manage Environments to use protected variables.");
        }
        return current;
    }

    private static SecretKey verifiedKey(char[] passphrase, Metadata metadata) {
        SecretKey candidate = SecretCipher.derive(passphrase, metadata.salt());
        try {
            if (CHECK_VALUE.equals(SecretCipher.decrypt(
                    candidate, verifierContext(metadata.salt()), metadata.verifier()))) {
                return candidate;
            }
        } catch (IllegalStateException exception) {
            // A wrong passphrase and modified metadata are indistinguishable here.
        }
        throw new IllegalStateException("Unable to unlock Secret Storage. Check the passphrase and database backup.");
    }

    private static void migrateLegacy(Connection connection, SecretKey candidate) throws SQLException {
        List<ProtectedRow> protectedRows = new ArrayList<>();
        try (var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT id, environment_id, name, stored_value, is_secret, protection_version,
                            created_at, updated_at, typeof(stored_value) AS value_type
                     FROM environment_variables
                     WHERE is_secret IS NOT 0 OR protection_version IS NOT 0
                     """)) {
            while (rows.next()) {
                if (integer(rows, "is_secret") != 1 || integer(rows, "protection_version") != 0
                        || !"text".equals(rows.getString("value_type"))) {
                    throw malformedLegacy();
                }
                String id = rows.getString("id");
                byte[] context = rowContext(id, rows.getString("environment_id"), rows.getString("name"));
                try {
                    Instant.parse(rows.getString("created_at"));
                    Instant.parse(rows.getString("updated_at"));
                } catch (java.time.format.DateTimeParseException | NullPointerException exception) {
                    throw malformedLegacy();
                }
                String original = rows.getString("stored_value");
                String encrypted = SecretCipher.encrypt(candidate, context, original);
                if (!original.equals(SecretCipher.decrypt(candidate, context, encrypted))) {
                    throw new IllegalStateException("A migrated secret failed verification.");
                }
                protectedRows.add(new ProtectedRow(id, encrypted));
            }
        }
        try (var update = connection.prepareStatement("""
                UPDATE environment_variables SET stored_value = ?, protection_version = ?
                WHERE id = ? AND protection_version = 0 AND is_secret = 1
                """);
             var verify = connection.prepareStatement("""
                     SELECT stored_value, protection_version, is_secret FROM environment_variables WHERE id = ?
                     """)) {
            for (ProtectedRow row : protectedRows) {
                update.setString(1, row.envelope());
                update.setInt(2, SecretCipher.VERSION);
                update.setString(3, row.id());
                if (update.executeUpdate() != 1) {
                    throw new IllegalStateException("A legacy secret changed during migration.");
                }
                verify.setString(1, row.id());
                try (var result = verify.executeQuery()) {
                    if (!result.next() || !row.envelope().equals(result.getString("stored_value"))
                            || integer(result, "protection_version") != SecretCipher.VERSION
                            || integer(result, "is_secret") != 1) {
                        throw new IllegalStateException("A migrated secret could not be verified in the database.");
                    }
                }
            }
        }
    }

    private static Metadata readMetadata(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT format_version, salt, verifier FROM secret_storage WHERE id = 1")) {
            if (!result.next()) {
                return null;
            }
            if (integer(result, "format_version") != SecretCipher.VERSION) {
                throw new IllegalStateException("Unsupported Secret Storage version. Use a compatible Callsmith build.");
            }
            byte[] salt = result.getBytes("salt");
            String verifier = result.getString("verifier");
            if (salt == null || salt.length != SecretCipher.SALT_BYTES || verifier == null || verifier.isEmpty()) {
                throw new IllegalStateException("Invalid Secret Storage metadata. Check your database backup.");
            }
            return new Metadata(salt, verifier);
        }
    }

    private static void saveMetadata(Connection connection, Metadata metadata) throws SQLException {
        try (var insert = connection.prepareStatement(
                "INSERT INTO secret_storage (id, format_version, salt, verifier) VALUES (1, ?, ?, ?)")) {
            insert.setInt(1, SecretCipher.VERSION);
            insert.setBytes(2, metadata.salt());
            insert.setString(3, metadata.verifier());
            insert.executeUpdate();
        }
    }

    static int integer(ResultSet row, String column) throws SQLException {
        Object value = row.getObject(column);
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Long number && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
            return number.intValue();
        }
        throw new IllegalStateException("Invalid environment secret storage record.");
    }

    private static byte[] rowContext(String id, String environmentId, String name) {
        if (id == null || id.isBlank() || environmentId == null || environmentId.isBlank()
                || name == null || name.isBlank()) {
            throw new IllegalStateException("Invalid environment secret identity.");
        }
        return SecretCipher.context("environment-variable", id, environmentId, name);
    }

    private static byte[] verifierContext(byte[] salt) {
        return SecretCipher.context("workspace-key-check", Base64.getEncoder().encodeToString(salt));
    }

    private static IllegalStateException malformedLegacy() {
        return new IllegalStateException("A legacy secret record is malformed or already protected without metadata. Migration stopped.");
    }

    private static IllegalStateException storageFailure() {
        return new IllegalStateException(
                "Secret Storage could not read or update the database. Check file access and free disk space.");
    }

    private record ProtectedRow(String id, String envelope) {}
    private record Metadata(byte[] salt, String verifier) {}
}
