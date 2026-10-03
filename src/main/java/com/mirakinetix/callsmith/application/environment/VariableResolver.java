package com.mirakinetix.callsmith.application.environment;

public interface VariableResolver {

    String resolve(
            String value,
            String environmentId
    );
}