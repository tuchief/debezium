/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Test;

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

/**
 * @author Chris Cranford
 */
public class MariaDbAntlrDdlParserTest extends BinlogAntlrDdlParserTest<MariaDbValueConverters, MariaDbDefaultValueConverter, MariaDbAntlrDdlParser> {
    @Test
    public void shouldParseRpsCustBaseCreateTable() {
        final SimpleDdlParserListener listener = new SimpleDdlParserListener();
        final MariaDbAntlrDdlParser parser = getParser(listener);
        final Tables tables = new Tables();

        parser.parse("CREATE TABLE IF NOT EXISTS `rps_cust_base` (\n" +
                "  `cust_id` bigint(20) NOT NULL AUTO_INCREMENT,\n" +
                "  `cust_num` varchar(32) NOT NULL,\n" +
                "  `cust_name` varchar(120) NOT NULL DEFAULT '',\n" +
                "  `id_type` char(2) NOT NULL DEFAULT '01',\n" +
                "  `id_num` varchar(40) NOT NULL,\n" +
                "  `phone_num` varchar(20) DEFAULT NULL,\n" +
                "  `email_addr` varchar(100) DEFAULT NULL,\n" +
                "  `gender` enum('M','F','U') DEFAULT 'U',\n" +
                "  `birth_dt` date DEFAULT NULL,\n" +
                "  `cust_level` char(2) NOT NULL DEFAULT '01',\n" +
                "  `cust_status` char(2) NOT NULL DEFAULT 'N',\n" +
                "  `open_org` varchar(12) NOT NULL DEFAULT '0000',\n" +
                "  `open_dt` date NOT NULL DEFAULT (curdate()),\n" +
                "  `remark` varchar(500) DEFAULT '无',\n" +
                "  `create_dt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                "  `update_dt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                "  PRIMARY KEY (`cust_id`),\n" +
                "  UNIQUE KEY `uk_num` (`cust_num`),\n" +
                "  UNIQUE KEY `uk_id` (`id_type`, `id_num`),\n" +
                "  KEY `idx_name` (`cust_name`),\n" +
                "  KEY `idx_org_dt` (`open_org`, `open_dt`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci\n" +
                "  COMMENT='公共基础表-客户信息';", tables);

        assertThat(parser.getParsingExceptionsFromWalker()).isEmpty();
        final Table table = tables.forTable(new TableId(null, null, "rps_cust_base"));
        assertThat(table).isNotNull();
        assertThat(table.primaryKeyColumnNames()).containsExactly("cust_id");
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
