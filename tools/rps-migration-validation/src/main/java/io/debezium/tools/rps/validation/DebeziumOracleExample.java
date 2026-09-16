/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.tools.rps.validation;

import java.util.Properties;

/** 3.6-compatible replacement for the recovered local Oracle validation program. */
public final class DebeziumOracleExample {

    private DebeziumOracleExample() {
    }

    static Properties connectorProperties(ValidationConfig config) {
        return ConnectorProperties.oracle(config);
    }

    public static void main(String[] args) throws Exception {
        ValidationConfig config = ValidationConfig.system();
        EmbeddedEngineRunner.runFromSystem(connectorProperties(config), config);
    }
}
