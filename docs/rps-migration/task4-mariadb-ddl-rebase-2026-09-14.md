# MariaDB extended DDL rebase

Date: 2026-09-14

Target baseline: Debezium `v3.6.2.Final` at
`02810e25b19c04e5095b2b6fbbdcbae549a69f19`.

## Scope and clean-baseline evidence

Seven historical commits were converted into behavior tests before production
changes were made. The initial nine-test parser run produced seven errors:

- `mariadb_schema`-qualified data types and Oracle-compatible aliases.
- `ALTER TABLE ... ADD PARTITION PARTITIONS 2`.
- `ALTER TABLE ... DROP COLUMN ... CASCADE`.
- Three valid `COMMENT ON COLUMN` paths.

The schema-qualified function path and rejection of a one-part COMMENT target
already passed. Clean 3.6.2 also parsed `WITH SYSTEM VERSIONING`, but a separate
RED test proved that the `Table` model did not retain this attribute. The
implicit-row test then failed compilation because no MariaDB row-size hook
existed.

## Minimal 3.6 implementation

- Added the dedicated `MARIADB_SCHEMA_DOT` token and applied it to current 3.6
  data-type and scalar-function rules.
- Preserved current UUID/VECTOR grammar while adding NUMBER and VARCHAR2 alias
  normalization in the 3.6 column listener.
- Added one parser-only branch for `ADD PARTITION PARTITIONS count`.
- Allowed CASCADE in the existing drop-column rule.
- Added the COMMENT grammar, a dedicated listener, and per-statement listener
  state reset. Comments-disabled and malformed-target behavior remain covered.
- Stored `mariadb.system.versioned=true` on parsed tables.
- Extracted an overridable binlog row-size predicate. MariaDB accepts exactly
  two trailing implicit columns only for marked system-versioned tables;
  ordinary tables remain strict.

The unrelated skip-unparseable-DDL logging changes bundled in historical commit
`f576299f47` were not included here.

## Verification

- Combined focused suite: 12 tests, 0 failures, 0 errors, 0 skipped.
- MariaDB non-Docker suite: 303 tests, 0 failures, 0 errors, 0 skipped.
- Binlog non-Docker suite: 76 tests, 0 failures, 0 errors, 0 skipped.
- ANTLR QA: MySQL, MySQL legacy, MariaDB, and Oracle grammar roots completed
  with `BUILD SUCCESS`.
- MariaDB 11.8.9 Failsafe: explicit and implicit system-versioned tables both
  completed snapshot and streaming UPDATE, 2 tests with no failure, error, or
  skip. The container was stopped and removed.

## Remaining acceptance boundary

Schema-qualified Oracle-mode types, partition-count DDL, DROP CASCADE, and
COMMENT ON COLUMN still require GoldenDB/TDSQL/MariaDB site SQL or captured
query-event replay. The Docker acceptance here proves only the two
system-versioned table variants.
