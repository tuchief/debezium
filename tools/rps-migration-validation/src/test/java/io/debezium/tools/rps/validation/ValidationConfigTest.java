/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.tools.rps.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class ValidationConfigTest {

    @Test
    void shouldResolvePropertyBeforeEnvironmentAndDefault() {
        Properties properties = new Properties();
        properties.setProperty("validation.database.hostname", "property-host");
        ValidationConfig config = new ValidationConfig(properties,
                Map.of("RPS_VALIDATION_DATABASE_HOSTNAME", "environment-host"));

        assertThat(config.optional("validation.database.hostname",
                "RPS_VALIDATION_DATABASE_HOSTNAME", "default-host")).isEqualTo("property-host");
        assertThat(config.optional("validation.database.port",
                "RPS_VALIDATION_DATABASE_PORT", "3306")).isEqualTo("3306");
    }

    @Test
    void shouldRejectMissingRequiredValueWithoutDisclosingOtherSecrets() {
        ValidationConfig config = new ValidationConfig(new Properties(),
                Map.of("RPS_VALIDATION_DATABASE_PASSWORD", "do-not-print-this"));

        assertThatThrownBy(() -> config.required("validation.database.user", "RPS_VALIDATION_DATABASE_USER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validation.database.user")
                .hasMessageContaining("RPS_VALIDATION_DATABASE_USER")
                .hasMessageNotContaining("do-not-print-this");
    }

    @Test
    void shouldRequirePositiveLongValues() {
        Properties properties = new Properties();
        properties.setProperty("validation.run.seconds", "0");
        ValidationConfig config = new ValidationConfig(properties, Map.of());

        assertThatThrownBy(() -> config.positiveLong("validation.run.seconds",
                "RPS_VALIDATION_RUN_SECONDS", 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validation.run.seconds");
    }

    @Test
    void shouldCreateConnectorSpecificStateFilesBelowTargetByDefault() {
        ValidationConfig config = new ValidationConfig(new Properties(), Map.of());

        Path mysqlOffset = config.stateFile("mysql", "offsets.dat");
        Path oracleOffset = config.stateFile("oracle", "offsets.dat");

        assertThat(mysqlOffset).isNotEqualTo(oracleOffset);
        assertThat(mysqlOffset.toString()).startsWith("target/validation-state/mysql/");
        assertThat(oracleOffset.toString()).startsWith("target/validation-state/oracle/");
    }
}
