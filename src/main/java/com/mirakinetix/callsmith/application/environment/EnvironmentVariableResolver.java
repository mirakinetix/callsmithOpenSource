package com.mirakinetix.callsmith.application.environment;

import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EnvironmentVariableResolver
        implements VariableResolver {

    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile("\\{\\{([^{}]+)}}");

    private final EnvironmentVariableRepository variableRepository;

    public EnvironmentVariableResolver(
            EnvironmentVariableRepository variableRepository
    ) {
        this.variableRepository = variableRepository;
    }

    @Override
    public String resolve(
            String value,
            String environmentId
    ) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        if (environmentId == null
                || environmentId.isBlank()) {
            return value;
        }

        List<EnvironmentVariable> variables =
                variableRepository.findByEnvironmentId(
                        environmentId
                );

        if (variables.isEmpty()) {
            return value;
        }

        Matcher matcher =
                VARIABLE_PATTERN.matcher(value);

        StringBuffer resolvedValue =
                new StringBuffer();

        while (matcher.find()) {
            String variableName =
                    matcher.group(1).trim();

            EnvironmentVariable variable =
                    findVariable(
                            variables,
                            variableName
                    );

            if (variable == null) {
                matcher.appendReplacement(
                        resolvedValue,
                        Matcher.quoteReplacement(
                                matcher.group()
                        )
                );
                continue;
            }

            matcher.appendReplacement(
                    resolvedValue,
                    Matcher.quoteReplacement(
                            variable.value()
                    )
            );
        }

        matcher.appendTail(resolvedValue);

        return resolvedValue.toString();
    }

    private EnvironmentVariable findVariable(
            List<EnvironmentVariable> variables,
            String name
    ) {
        return variables.stream()
                .filter(variable ->
                        variable.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}