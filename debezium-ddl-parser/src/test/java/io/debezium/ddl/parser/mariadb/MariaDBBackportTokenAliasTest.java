/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.ddl.parser.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.Test;

import io.debezium.ddl.parser.mariadb.generated.MariaDBLexer;
import io.debezium.ddl.parser.mariadb.generated.MariaDBParser;

public class MariaDBBackportTokenAliasTest {

    @Test
    public void shouldReuseStableTokenIdentifiersForBackportedKeywords() {
        assertTokenType("AUTO", MariaDBParser.AUTOCOMMIT);
        assertTokenType("AUTOCOMMIT", MariaDBParser.AUTOCOMMIT);
        assertTokenType("PERIOD", MariaDBParser.PERIOD_ADD);
        assertTokenType("PERIOD_ADD", MariaDBParser.PERIOD_ADD);
        assertTokenType("SYSTEM_TIME", MariaDBParser.SYSTEM_USER);
        assertTokenType("SYSTEM_USER", MariaDBParser.SYSTEM_USER);
        assertTokenType("VECTOR", MariaDBParser.UUID_SHORT);
        assertTokenType("UUID_SHORT", MariaDBParser.UUID_SHORT);
    }

    private static void assertTokenType(String text, int expectedType) {
        Token token = new MariaDBLexer(CharStreams.fromString(text)).nextToken();
        assertThat(token.getType()).as(text).isEqualTo(expectedType);
        assertThat(token.getText()).isEqualTo(text);
    }
}
