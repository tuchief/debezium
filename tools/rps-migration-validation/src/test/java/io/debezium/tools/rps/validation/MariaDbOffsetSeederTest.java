/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.tools.rps.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MariaDbOffsetSeederTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldSkipSeedingWhenNoGtidWasSupplied() throws Exception {
        Properties connector = connectorProperties(new Properties());

        assertThat(MariaDbOffsetSeeder.seedWhenRequested(
                connector, new ValidationConfig(new Properties(), Map.of()))).isFalse();
    }

    @Test
    void shouldWriteRequestedGtidOffsetToTheConfiguredFile() throws Exception {
        Properties settings = new Properties();
        settings.setProperty("validation.seed.gtid", "0-136-23469");
        settings.setProperty("validation.seed.file", "mysql-bin.000123");
        settings.setProperty("validation.seed.position", "456");
        Properties connector = connectorProperties(settings);

        assertThat(MariaDbOffsetSeeder.seedWhenRequested(
                connector, new ValidationConfig(settings, Map.of()))).isTrue();
        assertThat(Path.of(connector.getProperty("offset.storage.file.filename"))).exists().isNotEmptyFile();
    }

    @Test
    void shouldRefuseToOverwriteAnExistingOffsetFile() throws Exception {
        Path offsetFile = temporaryDirectory.resolve("offsets.dat");
        Files.writeString(offsetFile, "existing-state");
        Properties settings = new Properties();
        settings.setProperty("validation.seed.gtid", "0-136-23469");
        Properties connector = connectorProperties(settings);

        assertThatThrownBy(() -> MariaDbOffsetSeeder.seedWhenRequested(
                connector, new ValidationConfig(settings, Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
        assertThat(Files.readString(offsetFile)).isEqualTo("existing-state");
    }

    private Properties connectorProperties(Properties settings) {
        Properties connector = new Properties();
        connector.setProperty("name", "rps-validation-mariadb-test");
        connector.setProperty("topic.prefix", "rps_validation_mariadb_test");
        connector.setProperty("offset.storage.file.filename", temporaryDirectory.resolve("offsets.dat").toString());
        settings.forEach((key, value) -> connector.setProperty(key.toString(), value.toString()));
        return connector;
    }
}
