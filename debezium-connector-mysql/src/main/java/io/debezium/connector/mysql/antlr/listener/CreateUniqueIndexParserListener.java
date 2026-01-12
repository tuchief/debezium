/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.debezium.connector.mysql.antlr.listener;

import static io.debezium.antlr.AntlrDdlParser.getText;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.connector.mysql.antlr.MySqlAntlrDdlParser;
import io.debezium.ddl.parser.mysql.generated.MySqlParser;
import io.debezium.ddl.parser.mysql.generated.MySqlParserBaseListener;
import io.debezium.relational.TableEditor;
import io.debezium.relational.TableId;
import io.debezium.text.ParsingException;

/**
 * Parser listener that is parsing MySQL CREATE UNIQUE INDEX statements, that will be used as a primary key
 * if it's not already defined for the table.
 *
 * @author Roman Kuchár <kucharrom@gmail.com>.
 */
public class CreateUniqueIndexParserListener extends MySqlParserBaseListener {

    private final static Logger LOG = LoggerFactory.getLogger(AlterTableParserListener.class);

    private final MySqlAntlrDdlParser parser;

    public CreateUniqueIndexParserListener(MySqlAntlrDdlParser parser) {
        this.parser = parser;
    }

    @Override
    public void enterCreateIndex(MySqlParser.CreateIndexContext ctx) {
        // Only process CREATE UNIQUE INDEX statements.
        // Non-unique indexes are ignored since they cannot be used as a primary key.
        if (ctx.UNIQUE() != null) {

            // Defensive check: in some MySQL binlog DDL events, the table name
            // or its identifier may be missing, which would otherwise cause NPE.
            if (ctx.tableName() == null || ctx.tableName().fullId() == null) {
                LOG.warn("Skip CREATE UNIQUE INDEX due to missing table name: {}", getText(ctx));
                return;
            }

            // Defensive check: ensure index identifier exists.
            // MySQL does not support schema-qualified index names, but binlog DDL
            // parsing may still yield unexpected or incomplete AST nodes.
            if (ctx.fullId() == null || ctx.fullId().uid().isEmpty()) {
                LOG.warn("Skip CREATE UNIQUE INDEX due to missing index name: {}", getText(ctx));
                return;
            }

            // Resolve the table identifier from the parsed table name.
            TableId tableId = parser.parseQualifiedTableId(ctx.tableName().fullId());

            // Skip processing if the table is not included by the connector filter.
            if (!parser.getTableFilter().isIncluded(tableId)) {
                LOG.debug("{} is not monitored, no need to process unique index", tableId);
                return;
            }

            // Retrieve table metadata editor from the current schema snapshot.
            TableEditor tableEditor = parser.databaseTables().editTable(tableId);
            if (tableEditor != null) {

                // Only derive a primary key from a UNIQUE INDEX if:
                // 1) the table does not already have a primary key, and
                // 2) the unique index columns are fully included in the table definition.
                if (!tableEditor.hasPrimaryKey()
                        && parser.isTableUniqueIndexIncluded(ctx.indexColumnNames(), tableEditor)) {

                    // Parse unique index columns and treat them as a primary key.
                    parser.parseUniqueIndexColumnNames(ctx.indexColumnNames(), tableEditor);
                    parser.databaseTables().overwriteTable(tableEditor.create());

                    // Resolve the index name correctly.
                    // MySQL does NOT support schema-qualified index names, so fullId
                    // usually contains a single identifier. To be defensive and avoid
                    // invalid assumptions, always use the last UID if multiple are present.
                    int uidCount = ctx.fullId().uid().size();
                    String indexName =
                            uidCount == 1
                                    ? parser.parseName(ctx.fullId().uid(0))
                                    : parser.parseName(ctx.fullId().uid(uidCount - 1));

                    // Notify the parser about the created unique index.
                    parser.signalCreateIndex(indexName, tableId, ctx);
                }
            }
            else {
                // Table metadata not found while processing CREATE UNIQUE INDEX,
                // which indicates an invalid DDL sequence or missing table definition.
                throw new ParsingException(null,
                        "Trying to create index on non existing table " + tableId
                                + ". Query: " + getText(ctx));
            }
        }

        // Continue walking the parse tree.
        super.enterCreateIndex(ctx);
        /*if (ctx.UNIQUE() != null) {
            TableId tableId = parser.parseQualifiedTableId(ctx.tableName().fullId());
            if (!parser.getTableFilter().isIncluded(tableId)) {
                LOG.debug("{} is not monitored, no need to process unique index", tableId);
                return;
            }
            TableEditor tableEditor = parser.databaseTables().editTable(tableId);
            if (tableEditor != null) {
                if (!tableEditor.hasPrimaryKey() && parser.isTableUniqueIndexIncluded(ctx.indexColumnNames(), tableEditor)) {
                    parser.parseUniqueIndexColumnNames(ctx.indexColumnNames(), tableEditor);
                    parser.databaseTables().overwriteTable(tableEditor.create());
                    parser.signalCreateIndex(parser.parseName(ctx.fullId().uid(1)), tableId, ctx);
                }
            }
            else {
                throw new ParsingException(null, "Trying to create index on non existing table " + tableId.toString() + "."
                        + "Query: " + getText(ctx));
            }
        }
        super.enterCreateIndex(ctx);*/
    }
}
