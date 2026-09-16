/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.mariadb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.debezium.config.CommonConnectorConfig;
import io.debezium.connector.binlog.BinlogAntlrDdlParserTest;
import io.debezium.connector.binlog.BinlogConnectorConfig;
import io.debezium.connector.mariadb.antlr.MariaDbAntlrDdlParser;
import io.debezium.connector.mariadb.charset.MariaDbCharsetRegistry;
import io.debezium.connector.mariadb.jdbc.MariaDbDefaultValueConverter;
import io.debezium.connector.mariadb.jdbc.MariaDbValueConverters;
import io.debezium.connector.mariadb.util.MariaDbValueConvertersFactory;
import io.debezium.jdbc.TemporalPrecisionMode;
import io.debezium.relational.RelationalDatabaseConnectorConfig;
import io.debezium.relational.Table;
import io.debezium.relational.TableId;
import io.debezium.relational.Tables;
import io.debezium.relational.ddl.DdlChanges;
import io.debezium.relational.ddl.SimpleDdlParserListener;
import io.debezium.text.ParsingException;

/**
 * @author Chris Cranford
 */
public class MariaDbAntlrDdlParserTest extends BinlogAntlrDdlParserTest<MariaDbValueConverters, MariaDbDefaultValueConverter, MariaDbAntlrDdlParser> {
    @Test
    void shouldParseMariaDbSchemaQualifiedDate() {
        MariaDbAntlrDdlParser parser = getParser(new SimpleDdlParserListener());
        Tables tables = new Tables();

        parser.parse("CREATE TABLE qualified_date (id INT PRIMARY KEY, event_date mariadb_schema.date)", tables);

        assertThat(parser.getParsingExceptionsFromWalker()).isEmpty();
        Table table = tables.forTable(new TableId(null, null, "qualified_date"));
        assertThat(table).isNotNull();
        assertThat(table.columnWithName("event_date").typeName()).isEqualTo("DATE");
    }

    @Test
    void shouldParseMariaDbSchemaQualifiedFunction() {
        MariaDbAntlrDdlParser parser = getParser(new SimpleDdlParserListener());
        Tables tables = new Tables();

        parser.parse("CREATE TABLE qualified_function ("
                + "id INT PRIMARY KEY, cheque_no VARCHAR(100), "
                + "last_6 CHAR(6) GENERATED ALWAYS AS (mariadb_schema.substr(trim(cheque_no),-6)) STORED)", tables);

        assertThat(parser.getParsingExceptionsFromWalker()).isEmpty();
        Table table = tables.forTable(new TableId(null, null, "qualified_function"));
        assertThat(table).isNotNull();
        assertThat(table.columnWithName("last_6")).isNotNull();
    }

    @Test
    void shouldParseMariaDbOracleTypeAndFunctionAliases() {
        MariaDbAntlrDdlParser parser = getParser(new SimpleDdlParserListener());
        Tables tables = new Tables();

        parser.parse("CREATE TABLE oracle_aliases ("
                + "id mariadb_schema.number(10,2), "
                + "ratio mariadb_schema.number, "
                + "code mariadb_schema.varchar2(20), "
                + "created_at DATETIME DEFAULT sysdate())", tables);

        assertThat(parser.getParsingExceptionsFromWalker()).isEmpty();
        Table table = tables.forTable(new TableId(null, null, "oracle_aliases"));
        assertThat(table).isNotNull();
        assertThat(table.columnWithName("id").typeName()).isEqualTo("DECIMAL");
        assertThat(table.columnWithName("ratio").typeName()).isEqualTo("DOUBLE");
        assertThat(table.columnWithName("code").typeName()).isEqualTo("VARCHAR");
    }

    @Test
    void shouldParseAddPartitionPartitionsCount() {
        MariaDbAntlrDdlParser parser = getParser(new SimpleDdlParserListener());
        Tables tables = new Tables();
        parser.parse("CREATE TABLE h_log_maria (id INT)", tables);

        parser.parse("ALTER TABLE h_log_maria ADD PARTITION PARTITIONS 2", tables);

        assertThat(parser.getParsingExceptionsFromWalker()).isEmpty();
        assertThat(tables.forTable(new TableId(null, null, "h_log_maria"))).isNotNull();
    }

    @Test
    void shouldRemoveColumnWhenDropColumnUsesCascade() {
        MariaDbAntlrDdlParser parser = getParser(new SimpleDdlParserListener());
        Tables tables = new Tables();

        parser.parse("CREATE TABLE drop_cascade (id INT NOT NULL, obsolete INT); "
                + "ALTER TABLE drop_cascade DROP COLUMN obsolete CASCADE", tables);

        assertThat(parser.getParsingExceptionsFromWalker()).isEmpty();
        Table table = tables.forTable(new TableId(null, null, "drop_cascade"));
        assertThat(table).isNotNull();
        assertThat(table.columnWithName("id")).isNotNull();
        assertThat(table.columnWithName("obsolete")).isNull();
    }

    @Test
    void shouldApplyCommentOnColumn() {
        MariaDbAntlrDdlParser parser = getParser(new SimpleDdlParserListener(), false, true);
        Tables tables = new Tables();

        parser.parse("CREATE TABLE retail_core.kdpa_acct_bill (id INT, last_prt_date VARCHAR(8)); "
                + "COMMENT ON COLUMN retail_core.kdpa_acct_bill.last_prt_date IS 'Last Print Date'", tables);

        assertThat(parser.getParsingExceptionsFromWalker()).isEmpty();
        Table table = tables.forTable(new TableId(null, "retail_core", "kdpa_acct_bill"));
        assertThat(table).isNotNull();
        assertThat(table.columnWithName("last_prt_date").comment()).isEqualTo("Last Print Date");
    }

    @Test
    void shouldIgnoreCommentOnColumnWhenCommentsAreDisabled() {
        MariaDbAntlrDdlParser parser = getParser(new SimpleDdlParserListener(), false, false);
        Tables tables = new Tables();

        parser.parse("CREATE TABLE retail_core.kdpa_acct_bill (id INT, last_prt_date VARCHAR(8)); "
                + "COMMENT ON COLUMN retail_core.kdpa_acct_bill.last_prt_date IS 'Last Print Date'", tables);

        assertThat(parser.getParsingExceptionsFromWalker()).isEmpty();
        Table table = tables.forTable(new TableId(null, "retail_core", "kdpa_acct_bill"));
        assertThat(table.columnWithName("last_prt_date").comment()).isNull();
    }

    @Test
    void shouldResetCommentOnColumnListenerBetweenStatements() {
        MariaDbAntlrDdlParser parser = getParser(new SimpleDdlParserListener(), false, true);
        Tables tables = new Tables();
        parser.setCurrentSchema("retail_core");
        parser.parse("CREATE TABLE retail_core.kdpa_acct_bill (id INT, last_prt_date VARCHAR(8))", tables);

        parser.parse("COMMENT ON COLUMN missing_table.missing_column IS 'Ignored'; "
                + "COMMENT ON COLUMN kdpa_acct_bill.last_prt_date IS 'Last Print Date'", tables);

        assertThat(parser.getParsingExceptionsFromWalker()).isEmpty();
        Table table = tables.forTable(new TableId(null, "retail_core", "kdpa_acct_bill"));
        assertThat(table.columnWithName("last_prt_date").comment()).isEqualTo("Last Print Date");
    }

    @Test
    void shouldRejectCommentOnColumnWithoutTableName() {
        MariaDbAntlrDdlParser parser = getParser(new SimpleDdlParserListener(), false, true);

        assertThatThrownBy(() -> parser.parse("COMMENT ON COLUMN last_prt_date IS 'Last Print Date'", new Tables()))
                .isInstanceOf(ParsingException.class);
    }

    @Test
    void shouldMarkSystemVersionedTableInTableModel() {
        MariaDbAntlrDdlParser parser = getParser(new SimpleDdlParserListener());
        Tables tables = new Tables();

        parser.parse("CREATE TABLE implicit_versioned (id INT PRIMARY KEY) WITH SYSTEM VERSIONING", tables);

        assertThat(parser.getParsingExceptionsFromWalker()).isEmpty();
        Table table = tables.forTable(new TableId(null, null, "implicit_versioned"));
        assertThat(table).isNotNull();
        assertThat(table.attributeWithName("mariadb.system.versioned")).isNotNull();
        assertThat(table.attributeWithName("mariadb.system.versioned").asBoolean()).isTrue();
    }

    @Override
    protected MariaDbAntlrDdlParser getParser(SimpleDdlParserListener listener) {
        return new MariaDbDdlParserWithSimpleTestListener(listener);
    }

    @Override
    protected MariaDbAntlrDdlParser getParser(SimpleDdlParserListener listener, boolean includeViews) {
        return new MariaDbDdlParserWithSimpleTestListener(listener, includeViews);
    }

    @Override
    protected MariaDbAntlrDdlParser getParser(SimpleDdlParserListener listener, Tables.TableFilter tableFilter) {
        return new MariaDbDdlParserWithSimpleTestListener(listener, tableFilter);
    }

    @Override
    protected MariaDbAntlrDdlParser getParser(SimpleDdlParserListener listener, boolean includeViews, boolean includeComments) {
        return new MariaDbDdlParserWithSimpleTestListener(listener, includeViews, includeComments);
    }

    @Override
    protected MariaDbValueConverters getValueConverters() {
        return new MariaDbValueConvertersFactory().create(
                RelationalDatabaseConnectorConfig.DecimalHandlingMode.DOUBLE,
                TemporalPrecisionMode.ADAPTIVE_TIME_MICROSECONDS,
                BinlogConnectorConfig.BigIntUnsignedHandlingMode.PRECISE,
                CommonConnectorConfig.BinaryHandlingMode.BYTES,
                CommonConnectorConfig.EventConvertingFailureHandlingMode.WARN);
    }

    @Override
    protected MariaDbDefaultValueConverter getDefaultValueConverters(MariaDbValueConverters valueConverters) {
        return new MariaDbDefaultValueConverter(valueConverters);
    }

    @Override
    protected List<String> extractEnumAndSetOptions(List<String> enumValues) {
        return MariaDbAntlrDdlParser.extractEnumAndSetOptions(enumValues);
    }

    public static class MariaDbDdlParserWithSimpleTestListener extends MariaDbAntlrDdlParser {
        public MariaDbDdlParserWithSimpleTestListener(DdlChanges listener) {
            this(listener, false);
        }

        public MariaDbDdlParserWithSimpleTestListener(DdlChanges listener, Tables.TableFilter tableFilter) {
            this(listener, false, false, tableFilter);
        }

        public MariaDbDdlParserWithSimpleTestListener(DdlChanges listener, boolean includeViews) {
            this(listener, includeViews, false, Tables.TableFilter.includeAll());
        }

        public MariaDbDdlParserWithSimpleTestListener(DdlChanges listener, boolean includeViews, boolean includeComments) {
            this(listener, includeViews, includeComments, Tables.TableFilter.includeAll());
        }

        public MariaDbDdlParserWithSimpleTestListener(DdlChanges listener, boolean includeViews, boolean includeComments, Tables.TableFilter tableFilter) {
            super(false, includeViews, includeComments, tableFilter, new MariaDbCharsetRegistry());
            this.ddlChanges = listener;
        }
    }
}
