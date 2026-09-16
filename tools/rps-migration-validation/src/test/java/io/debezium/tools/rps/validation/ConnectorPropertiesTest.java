/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.tools.rps.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class ConnectorPropertiesTest {

    @Test
    void shouldBuildCredentialSafeMySqlFamilyProperties() {
        ValidationConfig config = configWithRequiredValues();

        Properties mysql = DebeziumMySQLExample.connectorProperties(config);
        Properties golden = DebeziumGoldenExample.connectorProperties(config);
        Properties mariadb = DebeziumMariaDBExample.connectorProperties(config);

        assertThat(mysql.getProperty("connector.class")).isEqualTo("io.debezium.connector.mysql.MySqlConnector");
        assertThat(golden.getProperty("connector.class")).isEqualTo("io.debezium.connector.mysql.MySqlConnector");
        assertThat(mariadb.getProperty("connector.class")).isEqualTo("io.debezium.connector.mariadb.MariaDbConnector");
        assertThat(mysql.getProperty("snapshot.mode")).isEqualTo("no_data");
        assertThat(golden.getProperty("snapshot.mode")).isEqualTo("no_data");
        assertThat(mariadb.getProperty("snapshot.mode")).isEqualTo("no_data");
        assertThat(mysql.getProperty("database.password")).isEqualTo("secret");
        assertThat(mysql.getProperty("offset.storage.file.filename"))
                .isNotEqualTo(mariadb.getProperty("offset.storage.file.filename"));
    }

    @Test
    void shouldBuildOracleLogMinerProperties() {
        ValidationConfig config = configWithRequiredValues();

        Properties oracle = DebeziumOracleExample.connectorProperties(config);

        assertThat(oracle.getProperty("connector.class")).isEqualTo("io.debezium.connector.oracle.OracleConnector");
        assertThat(oracle.getProperty("snapshot.mode")).isEqualTo("no_data");
        assertThat(oracle.getProperty("lob.enabled")).isEqualTo("true");
        assertThat(oracle.getProperty("log.mining.strategy")).isEqualTo("online_catalog");
    }

    @Test
    void shouldNeverSupplyDefaultCredentials() {
        Properties properties = baseProperties();
        properties.remove("validation.database.password");
        ValidationConfig config = new ValidationConfig(properties, Map.of());

        assertThatThrownBy(() -> DebeziumMySQLExample.connectorProperties(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validation.database.password");
        assertThatThrownBy(() -> DebeziumOracleExample.connectorProperties(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validation.database.password");
    }

    private static ValidationConfig configWithRequiredValues() {
        return new ValidationConfig(baseProperties(), Map.of());
    }

    private static Properties baseProperties() {
        Properties properties = new Properties();
        properties.setProperty("validation.database.hostname", "database.example.invalid");
        properties.setProperty("validation.database.port", "3306");
        properties.setProperty("validation.database.user", "validator");
        properties.setProperty("validation.database.password", "secret");
        properties.setProperty("validation.database.dbname", "ORCLCDB");
        properties.setProperty("validation.database.include.list", "validation_db");
        properties.setProperty("validation.schema.include.list", "VALIDATION");
        properties.setProperty("validation.table.include.list", "validation_db.validation_table");
        return properties;
    }
}
