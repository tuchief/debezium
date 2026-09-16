# RPS Migration Validation Tools Design

## Goal

Preserve and modernize the four local Embedded Engine validation programs used
during the Debezium 3.0.3 to 3.6.2 migration without adding them to Debezium's
default reactor or embedding site credentials in Git.

## Scope

The retained entry points are:

- `DebeziumMySQLExample`
- `DebeziumGoldenExample`
- `DebeziumMariaDBExample`
- `DebeziumOracleExample`

The MariaDB program and its companion unit test are tracked on the 3.0.3
maintenance branch. The other three programs are absent from Git and the file
system, but their names are present in IntelliJ Local History; recovery from
Local History is the authoritative starting point for their behavior.

## Architecture

Create a standalone Maven project at `tools/rps-migration-validation`. It is
deliberately absent from the root `<modules>` list, so ordinary Debezium
`mvn test`, `mvn package`, and release builds do not compile or execute it.

The tool imports `io.debezium:debezium-rps-custom-bom` and consumes the same
published artifacts as RPS. Four public main classes remain independently
runnable. Common configuration and engine lifecycle behavior live in small
shared classes so every database uses the same credential rules, timeout,
completion reporting, and offset-path handling.

## Package and files

All Java code uses package `io.debezium.tools.rps.validation`.

- `ValidationConfig`: resolves a Java system property first, then an
  environment variable; required values fail with the missing key name and
  secrets are never printed.
- `EmbeddedEngineRunner`: starts one `DebeziumEngine`, counts records, prints a
  bounded completion summary, closes after the configured duration, and
  propagates failures to the process exit status.
- `DebeziumMySQLExample`: creates MySQL connector properties.
- `DebeziumGoldenExample`: creates the GoldenDB/MySQL-compatible connector
  properties, including the RPS query/status-variable validation flags.
- `DebeziumMariaDBExample`: creates MariaDB connector properties and preserves
  the offset-seeding and selected-regression behavior that remains meaningful
  on 3.6.
- `DebeziumOracleExample`: creates Oracle LogMiner connector properties,
  including transaction-name and LOB validation options.
- `ValidationConfigTest`: verifies precedence, missing-value failures, numeric
  parsing, and secret redaction.
- `ConnectorPropertiesTest`: verifies each entry point selects the expected
  connector class and never supplies a default password.
- `README.md`: documents prerequisites, configuration, build/test commands,
  commands for all four entry points, expected evidence, cleanup, and the
  distinction between unit tests and live acceptance.

## Configuration contract

Every setting accepts `-Dvalidation.*` and an uppercase environment fallback.
At minimum, live execution requires:

- `validation.database.hostname` / `RPS_VALIDATION_DATABASE_HOSTNAME`
- `validation.database.port` / `RPS_VALIDATION_DATABASE_PORT`
- `validation.database.user` / `RPS_VALIDATION_DATABASE_USER`
- `validation.database.password` / `RPS_VALIDATION_DATABASE_PASSWORD`
- `validation.database.include.list` / `RPS_VALIDATION_DATABASE_INCLUDE_LIST`
- `validation.table.include.list` / `RPS_VALIDATION_TABLE_INCLUDE_LIST`

Database-specific settings use the same prefix, for example
`validation.oracle.url`, `validation.oracle.pdb.name`, and
`validation.mysql.server.id`. Offset and schema-history files default only to
paths below the tool's `target/validation-state` directory; no user-home or
absolute developer path is permitted as a source default.

## Safety

- No hostname, username, password, service name, Kafka address, or customer
  schema is committed as a live default.
- Password values are accepted but never logged.
- The default mode is `snapshot.mode=no_data`; snapshot data capture must be
  requested explicitly.
- Each run has a finite default duration and closes the engine cleanly.
- Live tests create no database objects. The README requires an operator-owned
  test schema/table and explicit cleanup instructions.
- Offset and history paths are unique per connector unless explicitly
  overridden.

## Recovery and provenance

The three missing sources must first be exported from IntelliJ Local History to
a temporary, non-repository location. They are read as behavioral references;
credentials and obsolete 3.0.3 APIs are not copied. The tracked MariaDB source
and test are read directly from commit `5694e7c59b`.

The resulting 3.6 tools record the legacy source name in class-level Javadoc
and document any retired option in the README. The original 3.0.3 maintenance
branch is not modified.

## Verification

1. `mvn -f tools/rps-migration-validation/pom.xml test` must pass without a
   live database.
2. `mvn -f tools/rps-migration-validation/pom.xml package` must succeed and
   compile all four main classes.
3. The root Debezium reactor must not list the tool as a module.
4. Static scans must find no committed password value or developer-specific
   absolute path.
5. Each documented `exec:java` command must reach configuration validation
   without requiring compilation changes.
6. Live database results are reported separately and are not inferred from the
   unit-test or package result.
