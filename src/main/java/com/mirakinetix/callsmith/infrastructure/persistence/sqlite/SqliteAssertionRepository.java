package com.mirakinetix.callsmith.infrastructure.persistence.sqlite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirakinetix.callsmith.application.testing.AssertionRepository;
import com.mirakinetix.callsmith.application.testing.ResponseAssertion;

import java.sql.SQLException;
import java.util.List;

public final class SqliteAssertionRepository implements AssertionRepository {
    private static final TypeReference<List<ResponseAssertion>> LIST = new TypeReference<>() {};
    private final SqliteDatabase database;
    private final ObjectMapper mapper;

    public SqliteAssertionRepository(SqliteDatabase database, ObjectMapper mapper) {
        this.database = database;
        this.mapper = mapper;
    }

    @Override
    public List<ResponseAssertion> find(String requestId) {
        try (var connection = database.openConnection();
             var select = connection.prepareStatement("""
                     SELECT definitions FROM request_assertions WHERE request_id = ?
                     """)) {
            select.setString(1, requestId);
            try (var rows = select.executeQuery()) {
                if (!rows.next()) return List.of();
                var data = rows.getString(1);
                if (data.length() > 180_000) {
                    throw new IllegalStateException("Stored assertions exceed the supported size.");
                }
                return List.copyOf(mapper.readValue(data, LIST));
            }
        } catch (SQLException | JsonProcessingException | NullPointerException exception) {
            throw new IllegalStateException("Callsmith could not read the saved assertions.");
        }
    }

    @Override
    public void replace(String requestId, List<ResponseAssertion> assertions) {
        if (assertions.size() > ResponseAssertion.MAX_PER_REQUEST) {
            throw new IllegalArgumentException("A request can have at most 32 assertions.");
        }
        assertions.forEach(ResponseAssertion::validate);
        try (var connection = database.openConnection();
             var save = connection.prepareStatement("""
                     INSERT INTO request_assertions (request_id, definitions) VALUES (?, ?)
                     ON CONFLICT(request_id) DO UPDATE SET definitions = excluded.definitions
                     """)) {
            var data = mapper.writeValueAsString(assertions);
            if (data.length() > 180_000) {
                throw new IllegalArgumentException("These assertions exceed the supported storage size.");
            }
            save.setString(1, requestId);
            save.setString(2, data);
            save.executeUpdate();
        } catch (SQLException | JsonProcessingException exception) {
            throw new IllegalStateException("Callsmith could not save the assertions.");
        }
    }
}
