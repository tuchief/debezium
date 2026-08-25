/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import io.debezium.connector.mariadb.antlr.MariaDbAntlrDdlParser;
import io.debezium.relational.Attribute;
import io.debezium.relational.Column;
import io.debezium.relational.Table;
import io.debezium.relational.TableId;

public class MariaDbStreamingChangeEventSourceTest {

    @Test
    public void shouldRecognizeImplicitSystemVersioningColumnsInBinlogRows() {
        final Table table = Table.editor()
                .tableId(new TableId("db1", null, "customers"))
                .addColumn(Column.editor().name("id").type("INT").jdbcType(java.sql.Types.INTEGER).position(1).create())
                .addAttribute(Attribute.editor()
                        .name(MariaDbAntlrDdlParser.SYSTEM_VERSIONED_TABLE_ATTRIBUTE)
                        .value(true)
                        .create())
                .create();

        assertThat(MariaDbStreamingChangeEventSource.hasExpectedSystemVersionedRowSize(table, new Object[3])).isTrue();
        assertThat(MariaDbStreamingChangeEventSource.hasExpectedSystemVersionedRowSize(table, new Object[2])).isFalse();
    }

    @Test
    public void shouldNotAcceptExtraColumnsForRegularTables() {
        final Table table = Table.editor()
                .tableId(new TableId("db1", null, "customers"))
                .addColumn(Column.editor().name("id").type("INT").jdbcType(java.sql.Types.INTEGER).position(1).create())
                .create();

        assertThat(MariaDbStreamingChangeEventSource.hasExpectedSystemVersionedRowSize(table, new Object[3])).isFalse();
    }
}
