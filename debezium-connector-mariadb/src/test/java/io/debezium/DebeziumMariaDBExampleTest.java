/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class DebeziumMariaDBExampleTest {

    @Test
    public void shouldSelectEveryLocallySupportedRegressionForMariaDbValidationMode() {
        assertThat(DebeziumMariaDBExample.validationTestClassNames("mariadb")).containsExactly(
                "io.debezium.connector.mariadb.MariaDbGtidSetTest",
                "io.debezium.connector.mariadb.MariaDbAntlrDdlParserTest",
                "io.debezium.connector.mariadb.MariaDbStreamingChangeEventSourceTest",
                "io.debezium.connector.mariadb.MariaDbConnectorTaskTest",
                "io.debezium.connector.mysql.MySqlConnectorTaskTest",
                "io.debezium.relational.history.KafkaSchemaHistoryBufferingTest",
                "io.debezium.relational.history.KafkaSchemaHistoryTest",
                "io.debezium.util.BoundedConcurrentHashMapTest",
                "io.debezium.relational.history.SchemaHistoryBufferingTest",
                "io.debezium.ddl.parser.mariadb.MariaDBBackportTokenAliasTest",
                "io.debezium.ddl.parser.mariadb.MariaDBTokenCompatibilityTest",
                "io.debezium.ddl.parser.mariadb.MariaDBSystemVersioningTest",
                "io.debezium.connector.mariadb.SpecialCharactersIT",
                "io.debezium.connector.mariadb.UuidColumnIT",
                "io.debezium.connector.mariadb.MariaVectorIT",
                "io.debezium.connector.mariadb.SystemVersionedTableIT");
    }

    @Test
    public void shouldRejectUnknownValidationMode() {
        assertThatThrownBy(() -> DebeziumMariaDBExample.validationTestClassNames("all"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("all");
    }

    @Test
    public void shouldRequireExplicitDatabaseCredentialsForDefaultEngineMode() {
        assertThatThrownBy(() -> DebeziumMariaDBExample.requireNonBlank(null, "repro.database.password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repro.database.password");
        assertThatThrownBy(() -> DebeziumMariaDBExample.requireNonBlank("  ", "repro.database.user"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repro.database.user");
        assertThat(DebeziumMariaDBExample.requireNonBlank("safe", "property")).isEqualTo("safe");
    }

    @Test
    public void shouldRequireMariaDbVersionThatActuallyExecutesVectorTests() {
        assertThatThrownBy(() -> DebeziumMariaDBExample.requireMariaDbVectorVersion("11.4.3-MariaDB"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("11.7");
        DebeziumMariaDBExample.requireMariaDbVectorVersion("11.7.0-MariaDB");
        DebeziumMariaDBExample.requireMariaDbVectorVersion("11.8.9-MariaDB");
    }
}
