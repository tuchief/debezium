/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.tools.rps.validation;

import java.util.Properties;

/** 3.6-compatible replacement for the tracked 3.0.3 MariaDB validation program. */
public final class DebeziumMariaDBExample {

    private DebeziumMariaDBExample() {
    }

    static Properties connectorProperties(ValidationConfig config) {
        return ConnectorProperties.binlog(
                config, "mariadb", "io.debezium.connector.mariadb.MariaDbConnector", "3306", "123124");
    }

    public static void main(String[] args) throws Exception {
        ValidationConfig config = ValidationConfig.system();
        Properties properties = connectorProperties(config);
        MariaDbOffsetSeeder.seedWhenRequested(properties, config);
        EmbeddedEngineRunner.runFromSystem(properties, config);
    }
}
