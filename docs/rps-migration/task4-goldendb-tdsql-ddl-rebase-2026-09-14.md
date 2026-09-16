# GoldenDB and TDSQL MySQL DDL rebase

Date: 2026-09-14

Target baseline: Debezium `v3.6.2.Final` at
`02810e25b19c04e5095b2b6fbbdcbae549a69f19`.

## Historical scope

Eleven commits changed grammar or the unique-index listener without adding an
automated test. They were converted into ten behavior tests before production
code was changed.

## Clean 3.6.2 qualification

The first focused run executed ten tests. Three passed without any production
change:

- `DUPLICATE` as a table option.
- Schema-qualified `DROP INDEX` when `ON table` is present.
- Normal unqualified unique-index names and primary-key derivation.

Seven produced the expected `ParsingException` and were marked RED:

- `XA_PREPARED_LIST`.
- `TRUNCATE ... FOR RECYCLEBIN_VERSION`.
- `DROP TABLE ... FOR RECYCLEBIN_VERSION`.
- `PURGE TABLE ... FOR RECYCLEBIN_VERSION`.
- Schema-qualified `CREATE INDEX`.
- Schema-qualified `DROP INDEX` without `ON table`.
- `RENAME old TO new` without the `TABLE` keyword.

## Minimal 3.6 implementation

- Added `XA_PREPARED_LIST_SYMBOL` and `RECYCLEBIN_VERSION_SYMBOL` to the current
  MySQL lexer and their correct identifier-keyword categories.
- Extended the current MySQL grammar at its existing XA, table lifecycle,
  index, and rename rules.
- Kept `DUPLICATE` on the existing 3.6 engine-option fallback.
- Kept the existing `indexRef` implementation for schema-qualified
  `DROP INDEX ... ON table`.
- Updated only the current MySQL unique-index listener to select the final
  segment of a qualified index name; the legacy parser was not changed.

The implementation is adapted to the 3.6 AST and is not a cherry-pick of the
3.0.3 grammar.

## Verification

- Combined focused run: 10 tests, 0 failures, 0 errors, 0 skipped in each
  selected Surefire execution.
- MySQL explicit non-Docker suite: 377 tests, 0 failures, 0 errors, 4 skipped.
- ANTLR QA: MySQL, MySQL legacy, MariaDB, and Oracle grammar roots completed
  with `BUILD SUCCESS`; the new `ddl_goldendb.sql` fixture was executed.

The first two ANTLR QA attempts were excluded from success evidence. Adding new
lexer tokens changed token-set text in existing expected-error snapshots. The
affected `.errors` files were updated mechanically, and only the final complete
QA run is considered passing evidence.

## Remaining acceptance boundary

These are parser-level results. GoldenDB/TDSQL binlog capture and RPS DDL replay
still require a real database or captured customer binlog event carrying each
exact statement before release.
