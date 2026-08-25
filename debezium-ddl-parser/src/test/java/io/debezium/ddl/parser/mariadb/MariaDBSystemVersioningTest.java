/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.ddl.parser.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;

import io.debezium.ddl.parser.mariadb.generated.MariaDBLexer;
import io.debezium.ddl.parser.mariadb.generated.MariaDBParser;

public class MariaDBSystemVersioningTest {

    @Test
    public void shouldParseCreateTableWithSystemVersioning() {
        final MariaDBParser parser = parserFor(
                "CREATE TABLE \"t_sm_mix_10\" (" +
                        "\"id\" decimal(10,0) NOT NULL," +
                        "\"name\" varchar(100) DEFAULT NULL," +
                        "PRIMARY KEY (\"id\")" +
                        ") WITH SYSTEM VERSIONING");

        parser.root();

        assertThat(parser.getNumberOfSyntaxErrors()).isZero();
    }

    @Test
    public void shouldRejectUnknownWithTableOption() {
        final MariaDBParser parser = parserFor("CREATE TABLE t (id INT) WITH UNKNOWN OPTION");
        parser.removeErrorListeners();

        parser.root();

        assertThat(parser.getNumberOfSyntaxErrors()).isPositive();
    }

    @Test
    public void shouldNotAddListenerMethodForSystemVersioningOption() throws ClassNotFoundException {
        final Class<?> listenerClass = Class.forName("io.debezium.ddl.parser.mariadb.generated.MariaDBParserListener");
        assertThat(Arrays.stream(listenerClass.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .doesNotContain("enterTableOptionWithSystemVersioning", "exitTableOptionWithSystemVersioning");
    }

    private static MariaDBParser parserFor(String ddl) {
        return new MariaDBParser(new CommonTokenStream(new MariaDBLexer(CharStreams.fromString(ddl))));
    }
}
