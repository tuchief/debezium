/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.tools.rps.validation;

import java.util.Properties;

/** 3.6-compatible replacement for the recovered GoldenDB/MySQL validation program. */
public final class DebeziumGoldenExample {

    private DebeziumGoldenExample() {
    }

    static Properties connectorProperties(ValidationConfig config) {
        return ConnectorProperties.binlog(
                config, "goldendb", "io.debezium.connector.mysql.MySqlConnector", "3306", "123412");
    }

    public static void main(String[] args) throws Exception {
        ValidationConfig config = ValidationConfig.system();
        EmbeddedEngineRunner.runFromSystem(connectorProperties(config), config);
    }
}
