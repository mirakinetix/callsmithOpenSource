package com.mirakinetix.callsmith.application.environment;

import java.util.Optional;

public interface VariableResolver {

    String resolve(
            String value,
            String environmentId
    );

    /** Resolves one named variable and distinguishes a missing value from an empty one. */
    default Optional<String> lookup(String name, String environmentId) {
        String reference = "{{" + name + "}}";
        return Optional.ofNullable(resolve(reference, environmentId)).filter(value -> !value.equals(reference));
    }
}