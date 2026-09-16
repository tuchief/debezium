/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.tools.rps.validation;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Resolves validation settings without embedding environment-specific values. */
public final class ValidationConfig {

    private final Properties properties;
    private final Map<String, String> environment;

    ValidationConfig(Properties properties, Map<String, String> environment) {
        this.properties = Objects.requireNonNull(properties);
        this.environment = Objects.requireNonNull(environment);
    }

    public static ValidationConfig system() {
        return new ValidationConfig(System.getProperties(), System.getenv());
    }

    public String required(String propertyName, String environmentName) {
        String value = resolve(propertyName, environmentName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Required configuration is missing: " + propertyName + " or " + environmentName);
        }
        return value;
    }

    public String optional(String propertyName, String environmentName, String defaultValue) {
        String value = resolve(propertyName, environmentName);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public long positiveLong(String propertyName, String environmentName, long defaultValue) {
        String raw = optional(propertyName, environmentName, Long.toString(defaultValue));
        try {
            long value = Long.parseLong(raw);
            if (value <= 0) {
                throw new NumberFormatException("not positive");
            }
            return value;
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Configuration must be a positive integer: " + propertyName, e);
        }
    }

    public boolean bool(String propertyName, String environmentName, boolean defaultValue) {
        return Boolean.parseBoolean(optional(propertyName, environmentName, Boolean.toString(defaultValue)));
    }

    public Path stateFile(String connectorName, String fileName) {
        String stateDirectory = optional(
                "validation.state.directory",
                "RPS_VALIDATION_STATE_DIRECTORY",
                "target/validation-state");
        return Path.of(stateDirectory, connectorName, fileName);
    }

    private String resolve(String propertyName, String environmentName) {
        String value = properties.getProperty(propertyName);
        return value == null || value.isBlank() ? environment.get(environmentName) : value;
    }
}
