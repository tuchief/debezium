/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.performance.core;

import java.sql.Types;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import io.debezium.jdbc.JdbcValueConverters;
import io.debezium.relational.Column;
import io.debezium.relational.CustomConverterRegistry;
import io.debezium.relational.Table;
import io.debezium.relational.TableEditor;
import io.debezium.relational.TableId;
import io.debezium.relational.TableSchema;
import io.debezium.relational.TableSchemaBuilder;
import io.debezium.schema.FieldNameSelector;
import io.debezium.schema.SchemaNameAdjuster;
import io.debezium.schema.SchemaTopicNamingStrategy;

@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class DmlRowConversionPerf {

    @Param({ "8", "64" })
    private int columnCount;

    private TableSchema schema;
    private Object[] row;

    @Setup
    public void setup() {
        final TableEditor editor = Table.editor().tableId(new TableId("catalog", "schema", "benchmark_table"));
        row = new Object[columnCount];
        for (int i = 0; i < columnCount; i++) {
            editor.addColumn(Column.editor()
                    .name("c" + i)
                    .type("INTEGER")
                    .jdbcType(Types.INTEGER)
                    .optional(true)
                    .create());
            row[i] = i;
        }
        final Properties topicProperties = new Properties();
        topicProperties.put("topic.prefix", "benchmark");
        schema = new TableSchemaBuilder(
                new JdbcValueConverters(),
                SchemaNameAdjuster.NO_OP,
                new CustomConverterRegistry(null),
                SchemaBuilder.struct().name("benchmark.source").build(),
                FieldNameSelector.defaultSelector(SchemaNameAdjuster.NO_OP),
                false)
                .create(new SchemaTopicNamingStrategy(topicProperties, false), editor.create(), null, null, null);
    }

    @Benchmark
    public Struct convertSuccessfulRow() {
        return schema.valueFromColumnData(row);
    }
}
