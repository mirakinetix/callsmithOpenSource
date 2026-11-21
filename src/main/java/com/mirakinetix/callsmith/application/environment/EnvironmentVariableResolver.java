package com.mirakinetix.callsmith.application.environment;

import com.mirakinetix.callsmith.domain.environment.EnvironmentVariable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EnvironmentVariableResolver implements VariableResolver {
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^{}]+)}}");
    private final EnvironmentVariableRepository variableRepository;

    public EnvironmentVariableResolver(EnvironmentVariableRepository variableRepository) {
        this.variableRepository = variableRepository;
    }

    @Override
    public String resolve(String value, String environmentId) {
        if (value == null || value.isEmpty() || environmentId == null || environmentId.isBlank()) {
            return value;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(value);
        Map<String, Optional<String>> resolved = new HashMap<>();
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            String replacement = resolved.computeIfAbsent(name, key -> variableRepository
                    .findByName(environmentId, key).map(EnvironmentVariable::value)).orElse(matcher.group());
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        return matcher.appendTail(output).toString();
    }
}
