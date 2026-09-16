/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.mariadb.antlr.listener;

import static io.debezium.relational.ddl.AbstractDdlParser.withoutQuotes;

import java.util.List;
import java.util.stream.Collectors;

import io.debezium.connector.mariadb.antlr.MariaDbAntlrDdlParser;
import io.debezium.ddl.parser.mariadb.generated.MariaDBParser;
import io.debezium.ddl.parser.mariadb.generated.MariaDBParserBaseListener;
import io.debezium.relational.Column;
import io.debezium.relational.Table;
import io.debezium.relational.TableEditor;
import io.debezium.relational.TableId;

/**
 * Parses MariaDB {@code COMMENT ON COLUMN} statements.
 */
public class CommentParserListener extends MariaDBParserBaseListener {

    private final MariaDbAntlrDdlParser parser;
    private TableEditor tableEditor;

    public CommentParserListener(MariaDbAntlrDdlParser parser) {
        this.parser = parser;
    }

    @Override
    public void enterCommentOnColumn(MariaDBParser.CommentOnColumnContext ctx) {
        tableEditor = null;
        if (parser.skipComments()) {
            return;
        }

        MariaDBParser.CommentColumnNameContext columnContext = ctx.commentColumnName();
        String schemaName = parser.currentSchema();
        String columnName = parser.parseName(columnContext.uid());

        String tableName = columnName;
        columnName = withoutQuotes(columnContext.dottedId(0).getText().substring(1));
        if (columnContext.dottedId().size() > 1) {
            schemaName = tableName;
            tableName = columnName;
            columnName = withoutQuotes(columnContext.dottedId(1).getText().substring(1));
        }

        TableId tableId = parser.resolveTableId(schemaName, tableName);
        if (!parser.getTableFilter().isIncluded(tableId)) {
            return;
        }

        Table table = parser.databaseTables().forTable(tableId);
        if (table == null) {
            return;
        }

        tableEditor = parser.databaseTables().editTable(tableId);
        String targetColumn = columnName;
        String comment = parser.withoutQuotes(ctx.STRING_LITERAL().getText());
        List<Column> columns = table.columns().stream()
                .map(column -> column.name().equalsIgnoreCase(targetColumn)
                        ? column.edit().comment(comment).create()
                        : column)
                .collect(Collectors.toList());
        tableEditor.setColumns(columns);

        super.enterCommentOnColumn(ctx);
    }

    @Override
    public void exitCommentOnColumn(MariaDBParser.CommentOnColumnContext ctx) {
        if (tableEditor != null) {
            parser.databaseTables().overwriteTable(tableEditor.create());
            parser.signalAlterTable(tableEditor.tableId(), null, ctx);
            tableEditor = null;
        }
        super.exitCommentOnColumn(ctx);
    }
}
