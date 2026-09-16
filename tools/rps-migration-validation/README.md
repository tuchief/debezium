# RPS migration validation tools

This standalone project preserves the Embedded Engine programs used to verify
the RPS migration from Debezium 3.0.3 to 3.6.2. It supports MySQL, GoldenDB,
MariaDB, and Oracle without adding developer credentials or customer endpoints
to the Debezium source tree.

The directory is intentionally absent from the root Debezium reactor. Normal
Debezium `mvn test`, `mvn package`, and release builds do not compile or run
these tools.

## What these commands prove

- `mvn test` proves configuration precedence, connector property generation,
  bounded execution checks, and MariaDB offset-file seeding without connecting
  to a database.
- `mvn package` proves all four entry points compile against the RPS 3.6.2
  dependency set.
- Running an entry point against a real database is live acceptance evidence.
  A successful unit test or package does not imply that live CDC passed.

## Prerequisites

- JDK 21.
- Maven 3.9 or newer.
- Read access to the company Maven `maven-public` group. This group combines
  internal RPS releases with Maven Central. Do not mirror all repositories to
  the hosted-only `maven-releases` repository.
- A dedicated validation account and test schema/table on the source database.
- Binlog/LogMiner configuration and privileges required by the selected
  Debezium connector.

Recommended Maven mirror:

```xml
<mirror>
  <id>company-public</id>
  <url>http://10.169.190.188:18587/repository/maven-public/</url>
  <mirrorOf>*</mirrorOf>
</mirror>
```

## Build and offline tests

Run from the Debezium 3.6 worktree root:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

mvn -f tools/rps-migration-validation/pom.xml test
mvn -f tools/rps-migration-validation/pom.xml package
```

The expected offline result is eleven tests with zero failures, errors, or skips.
No database connection is opened by those tests.

## Configuration

Java system properties take precedence over environment variables. Passwords
have no defaults and are never printed by the tool.

### Required for MySQL, GoldenDB, and MariaDB

| Java property | Environment variable | Meaning |
| --- | --- | --- |
| `validation.database.hostname` | `RPS_VALIDATION_DATABASE_HOSTNAME` | Source host or VIP |
| `validation.database.user` | `RPS_VALIDATION_DATABASE_USER` | Validation account |
| `validation.database.password` | `RPS_VALIDATION_DATABASE_PASSWORD` | Validation account password |
| `validation.database.include.list` | `RPS_VALIDATION_DATABASE_INCLUDE_LIST` | Captured database |
| `validation.table.include.list` | `RPS_VALIDATION_TABLE_INCLUDE_LIST` | Fully qualified captured table |

### Required for Oracle

| Java property | Environment variable | Meaning |
| --- | --- | --- |
| `validation.database.hostname` | `RPS_VALIDATION_DATABASE_HOSTNAME` | Oracle host, VIP, or scan name |
| `validation.database.user` | `RPS_VALIDATION_DATABASE_USER` | LogMiner validation account |
| `validation.database.password` | `RPS_VALIDATION_DATABASE_PASSWORD` | Validation account password |
| `validation.database.dbname` | `RPS_VALIDATION_DATABASE_DBNAME` | Database or service name used by the connector |
| `validation.schema.include.list` | `RPS_VALIDATION_SCHEMA_INCLUDE_LIST` | Captured schema |
| `validation.table.include.list` | `RPS_VALIDATION_TABLE_INCLUDE_LIST` | Fully qualified captured table |

### Common optional settings

| Java property | Environment variable | Default |
| --- | --- | --- |
| `validation.database.port` | `RPS_VALIDATION_DATABASE_PORT` | `3306`, or `1521` for Oracle |
| `validation.snapshot.mode` | `RPS_VALIDATION_SNAPSHOT_MODE` | `no_data` |
| `validation.topic.prefix` | `RPS_VALIDATION_TOPIC_PREFIX` | Connector-specific value |
| `validation.run.seconds` | `RPS_VALIDATION_RUN_SECONDS` | `60` |
| `validation.print.records` | `RPS_VALIDATION_PRINT_RECORDS` | `false` |
| `validation.state.directory` | `RPS_VALIDATION_STATE_DIRECTORY` | `target/validation-state` |
| `validation.snapshot.max.threads` | `RPS_VALIDATION_SNAPSHOT_MAX_THREADS` | `1` |
| `validation.errors.max.retries` | `RPS_VALIDATION_ERRORS_MAX_RETRIES` | `2` |
| `validation.offset.flush.interval.ms` | `RPS_VALIDATION_OFFSET_FLUSH_INTERVAL_MS` | `1000` |
| `validation.include.query` | `RPS_VALIDATION_INCLUDE_QUERY` | `true` |
| `validation.skip.unparseable.ddl` | `RPS_VALIDATION_SKIP_UNPARSEABLE_DDL` | `false` |

Oracle additionally supports:

| Java property | Environment variable | Default |
| --- | --- | --- |
| `validation.oracle.lob.enabled` | `RPS_VALIDATION_ORACLE_LOB_ENABLED` | `true` |
| `validation.oracle.log.mining.strategy` | `RPS_VALIDATION_ORACLE_LOG_MINING_STRATEGY` | `online_catalog` |

## Run an entry point

Set the required variables using values for a dedicated validation environment:

```bash
export RPS_VALIDATION_DATABASE_HOSTNAME='<validation-host>'
export RPS_VALIDATION_DATABASE_PORT='<validation-port>'
export RPS_VALIDATION_DATABASE_USER='<validation-user>'
export RPS_VALIDATION_DATABASE_PASSWORD='<validation-password>'
export RPS_VALIDATION_DATABASE_INCLUDE_LIST='<validation-database>'
export RPS_VALIDATION_TABLE_INCLUDE_LIST='<database.table>'
export RPS_VALIDATION_RUN_SECONDS='120'
```

### MySQL

```bash
mvn -f tools/rps-migration-validation/pom.xml \
  -Dexec.mainClass=io.debezium.tools.rps.validation.DebeziumMySQLExample \
  exec:java
```

### GoldenDB

GoldenDB uses the RPS-customized MySQL connector. Set its actual listener port
and database/table include lists before running. To validate the RPS-specific
query/status-variable path, execute a uniquely identifiable session change and
DML statement, then inspect the emitted source fields with
`RPS_VALIDATION_PRINT_RECORDS=true`:

```bash
mvn -f tools/rps-migration-validation/pom.xml \
  -Dexec.mainClass=io.debezium.tools.rps.validation.DebeziumGoldenExample \
  exec:java
```

### MariaDB

```bash
mvn -f tools/rps-migration-validation/pom.xml \
  -Dexec.mainClass=io.debezium.tools.rps.validation.DebeziumMariaDBExample \
  exec:java
```

To start from an explicit existing GTID position, set all seed inputs before
the first run with a new state directory:

| Java property | Environment variable | Default |
| --- | --- | --- |
| `validation.seed.gtid` | `RPS_VALIDATION_SEED_GTID` | disabled when absent |
| `validation.seed.file` | `RPS_VALIDATION_SEED_FILE` | `binlog.000001` |
| `validation.seed.position` | `RPS_VALIDATION_SEED_POSITION` | `4` |
| `validation.seed.server.id` | `RPS_VALIDATION_SEED_SERVER_ID` | `1` |

Example:

```bash
export RPS_VALIDATION_SEED_GTID='<domain-server-sequence>'
export RPS_VALIDATION_SEED_FILE='<binlog-file>'
export RPS_VALIDATION_SEED_POSITION='<binlog-position>'

mvn -f tools/rps-migration-validation/pom.xml \
  -Dexec.mainClass=io.debezium.tools.rps.validation.DebeziumMariaDBExample \
  exec:java
```

Do not seed an existing offset file. Archive the prior state and use a new
connector-specific state directory.

### Oracle

Oracle requires database, schema, and table settings rather than the MySQL
database include list:

```bash
export RPS_VALIDATION_DATABASE_PORT='1521'
export RPS_VALIDATION_DATABASE_DBNAME='<validation-service>'
export RPS_VALIDATION_SCHEMA_INCLUDE_LIST='<VALIDATION_SCHEMA>'
export RPS_VALIDATION_TABLE_INCLUDE_LIST='<VALIDATION_SCHEMA.TABLE_NAME>'

mvn -f tools/rps-migration-validation/pom.xml \
  -Dexec.mainClass=io.debezium.tools.rps.validation.DebeziumOracleExample \
  exec:java
```

For transaction-name acceptance, start a source transaction with a unique
transaction name, change the validation table, commit, and confirm that the
printed change event carries the same `source.txName`. LOB acceptance likewise
requires an operator-issued change to a test CLOB/BLOB column; the tool enables
LOB capture but does not create or mutate source objects itself.

## Evidence and restart procedure

Every bounded run ends with output shaped like:

```text
ENGINE_SUMMARY: records=<count>, successful=<true-or-false>, message=<completion-message>
```

For incremental and restart acceptance:

1. Start the tool with an empty connector-specific state directory.
2. Make one uniquely identifiable change in the operator-owned test table.
3. Confirm the expected change event and record the `ENGINE_SUMMARY` line.
4. Run the same entry point again with the same state directory.
5. Make a second uniquely identifiable change and confirm capture resumes
   without replaying unexpected prior events.
6. Archive the offset and schema-history files together with the command and
   sanitized output.

The tool does not create or drop database objects. Cleanup of source rows,
tables, users, and privileges remains the validation operator's responsibility.
Only archive or remove a state directory after confirming its exact connector
name and preserving any evidence required for rollback analysis.

## Legacy provenance

See [legacy-behavior-inventory.md](legacy-behavior-inventory.md) for the
sanitized mapping from the four original local programs to the 3.6-compatible
entry points.
