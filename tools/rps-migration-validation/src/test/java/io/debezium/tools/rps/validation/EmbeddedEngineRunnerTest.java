/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.tools.rps.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class EmbeddedEngineRunnerTest {

    @Test
    void shouldRejectAnUnboundedValidationDuration() {
        assertThatThrownBy(() -> EmbeddedEngineRunner.run(new Properties(), Duration.ZERO, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }
}
