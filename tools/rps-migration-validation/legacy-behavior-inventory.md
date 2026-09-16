# Legacy validation behavior inventory

The 3.0.3 source worktree is read-only for this migration. Values below are
sanitized descriptions; no recovered credential or customer endpoint is copied
into this repository.

| Legacy entry point | Source status | Connector | Retained behavior | Reworked or retired behavior |
| --- | --- | --- | --- | --- |
| `DebeziumMySQLExample` | restored from IntelliJ Local History; untracked | `MySqlConnector` | `no_data`, file offset/history, query capture, bounded retries, shutdown hook | hard-coded connection/state values become external configuration; unbounded latch gains finite duration |
| `DebeziumGoldenExample` | restored from IntelliJ Local History; untracked | `MySqlConnector` | `no_data`, schema changes, file offset/history, database include list, parallel snapshot option | fixed endpoint/schema and 60,000-second sleep are removed; engine is closed explicitly |
| `DebeziumMariaDBExample` | tracked in `5694e7c59b` | `MariaDbConnector` | file offset/history, include lists, completion callback, optional GTID/file/position offset seed | `schema_only` becomes `no_data`; JUnit aggregation remains in connector test suites rather than being launched reflectively |
| `DebeziumOracleExample` | restored from IntelliJ Local History; untracked | `OracleConnector` | `no_data`, interval-as-string, LogMiner, file offset/history, schema/table filters | deprecated `log.mining.continuous.mine` and irrelevant `database.server.id` are omitted; endpoint, database, schema, and table become external configuration |

## 3.6 API classification

- Retained: `DebeziumEngine.create(Json.class)`, completion callbacks,
  `FileOffsetBackingStore`, `FileSchemaHistory`, `snapshot.mode=no_data`,
  `snapshot.max.threads`, `schema.history.internal.skip.unparseable.ddl`, and
  `interval.handling.mode`.
- Renamed behavior: legacy `snapshot.mode=schema_only` is represented by
  `snapshot.mode=no_data` in the 3.6 tools.
- Retired from the tool: deprecated `log.mining.continuous.mine`, Oracle
  `database.server.id`, reflective JUnit suite launching, developer-specific
  absolute paths, and embedded live credentials.
- Preserved through explicit state reuse: operators may copy an existing
  offset/history pair into the documented connector-specific state directory.
  MariaDB structured offset seeding is retained only as externally supplied
  `validation.seed.*` values and must never be enabled implicitly.
