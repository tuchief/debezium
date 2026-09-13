/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.ddl.parser.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.junit.Test;

import io.debezium.ddl.parser.mariadb.generated.MariaDBLexer;
import io.debezium.ddl.parser.mariadb.generated.MariaDBParser;

public class MariaDBTokenCompatibilityTest {

    @Test
    public void shouldPreserveTokenIdentifiersUsedByCompiledConnectors() {
        assertThat(MariaDBParser.DATE).isEqualTo(217);
        assertThat(MariaDBParser.CHAR).isEqualTo(222);
        assertThat(MariaDBParser.VARCHAR).isEqualTo(223);
        assertThat(MariaDBParser.TEXT).isEqualTo(234);
        assertThat(MariaDBParser.VARYING).isEqualTo(238);
        assertThat(MariaDBParser.MARIADB_SCHEMA_DOT).isEqualTo(1182);
        assertThat(MariaDBParser.ERROR_RECONGNIGION).isEqualTo(1183);
    }

    @Test
    public void shouldReuseExistingTokenIdentifiersForOracleModeTypeAliases() {
        final MariaDBLexer lexer = new MariaDBLexer(CharStreams.fromString("CREATE TABLE t (n NUMBER, d NUMBER(12,2), v VARCHAR2(50))"));
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final MariaDBParser parser = new MariaDBParser(tokens);

        parser.root();
        tokens.fill();

        final List<Token> visibleTokens = tokens.getTokens().stream()
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getText().equals("NUMBER") || token.getText().equals("VARCHAR2"))
                .toList();

        assertThat(visibleTokens).extracting(Token::getType)
                .containsExactly(MariaDBParser.DOUBLE, MariaDBParser.DECIMAL, MariaDBParser.VARCHAR);
    }

    @Test
    public void shouldParseCreateTableUsingMariaDbOracleModeAliases() {
        final String ddl = "CREATE TABLE l_user_maria (\n" +
                "id NUMBER,\n" +
                "user_id NUMBER NOT NULL,\n" +
                "region_id INT NOT NULL,\n" +
                "user_name VARCHAR2(50),\n" +
                "created_at DATE DEFAULT SYSDATE,\n" +
                "PRIMARY KEY (`id`, region_id)\n" +
                ")\n" +
                "PARTITION BY LIST (`region_id`) (\n" +
                "PARTITION p_init VALUES IN (0) -- placeholder partition\n" +
                ");";
        final MariaDBLexer lexer = new MariaDBLexer(CharStreams.fromString(ddl));
        final MariaDBParser parser = new MariaDBParser(new CommonTokenStream(lexer));

        parser.root();

        assertThat(parser.getNumberOfSyntaxErrors()).isZero();
    }

    @Test
    public void shouldParseAddingHashPartitions() {
        final MariaDBLexer lexer = new MariaDBLexer(CharStreams.fromString(
                "ALTER TABLE h_log_maria ADD PARTITION PARTITIONS 2"));
        final MariaDBParser parser = new MariaDBParser(new CommonTokenStream(lexer));

        parser.root();

        assertThat(parser.getNumberOfSyntaxErrors()).isZero();
    }

    @Test
    public void shouldParseDroppingColumnWithCascade() {
        final MariaDBLexer lexer = new MariaDBLexer(CharStreams.fromString(
                "ALTER TABLE t1_like DROP COLUMN c1 CASCADE"));
        final MariaDBParser parser = new MariaDBParser(new CommonTokenStream(lexer));

        parser.root();

        assertThat(parser.getNumberOfSyntaxErrors()).isZero();
    }
}
