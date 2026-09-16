# RPS Migration Validation Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the MySQL, GoldenDB, MariaDB, and Oracle migration validation entry points as a standalone, documented, credential-safe tool that does not participate in the default Debezium build.

**Architecture:** Recover the missing 3.0.3 programs as behavioral references, then implement four 3.6-compatible main classes over shared configuration and Embedded Engine lifecycle helpers. A standalone Maven POM imports the RPS custom BOM but is intentionally absent from the root reactor.

**Tech Stack:** Java 21, Maven 3.9.x, Debezium Embedded Engine 3.6.2, JUnit 5, AssertJ, exec-maven-plugin.

**Spec:** `docs/superpowers/specs/2026-09-16-rps-migration-validation-tools-design.md`

## Global Constraints

- Do not modify the 3.0.3 maintenance worktree.
- Do not add `tools/rps-migration-validation` to the root `<modules>` list.
- Do not commit credentials, customer hosts, customer schemas, or absolute developer paths.
- Live validation must require explicit external configuration and have a finite run duration.
- Unit/package success must not be reported as live database acceptance.

---

### Task 1: Recover and inventory the legacy validation behavior

**Files:**
- Read: `/Users/tuchief/workspace/debezium/debezium-connector-mariadb/src/test/java/io/debezium/DebeziumMariaDBExample.java`
- Read: `/Users/tuchief/workspace/debezium/debezium-connector-mariadb/src/test/java/io/debezium/DebeziumMariaDBExampleTest.java`
- Recover to temporary storage: `DebeziumGoldenExample.java`, `DebeziumMySQLExample.java`, `DebeziumOracleExample.java`
- Create: `tools/rps-migration-validation/legacy-behavior-inventory.md`

**Interfaces:**
- Consumes: tracked commit `5694e7c59b` and IntelliJ Local History.
- Produces: a sanitized behavior/configuration inventory used by the four new entry points.

- [x] **Step 1:** Restore the three missing sources with IntelliJ Local History in the original read-only worktree and inspect them in place without adding them to Git.
- [x] **Step 2:** Record each legacy connector class, snapshot mode, offset store, schema-history store, include-list behavior, timeout behavior, and emitted evidence; replace every credential value with `<redacted>`.
- [x] **Step 3:** Compare every referenced API with 3.6 source and classify it as retained, renamed, or retired.
- [x] **Step 4:** Run a secret scan against the inventory and require no original credential or host value.

### Task 2: Add the standalone project and configuration core

**Files:**
- Create: `tools/rps-migration-validation/pom.xml`
- Create: `tools/rps-migration-validation/src/main/java/io/debezium/tools/rps/validation/ValidationConfig.java`
- Create: `tools/rps-migration-validation/src/main/java/io/debezium/tools/rps/validation/EmbeddedEngineRunner.java`
- Create: `tools/rps-migration-validation/src/test/java/io/debezium/tools/rps/validation/ValidationConfigTest.java`

**Interfaces:**
- `ValidationConfig.required(property, environment)` returns a non-blank string or throws `IllegalArgumentException` naming both keys.
- `ValidationConfig.optional(property, environment, defaultValue)` implements property > environment > default precedence.
- `ValidationConfig.longValue(...)` parses a positive long and names the invalid property on failure.
- `EmbeddedEngineRunner.run(Properties, Duration)` returns an immutable summary containing record count and completion status.

- [x] **Step 1:** Write `ValidationConfigTest` for precedence, required-value failure, positive-long validation, and password non-disclosure; run it and confirm compilation fails because the class is absent.
- [x] **Step 2:** Implement the minimal `ValidationConfig` API and rerun the test to green.
- [x] **Step 3:** Add the standalone POM with the RPS custom BOM, official embedded/storage dependencies, JUnit 5, AssertJ, and exec-maven-plugin; do not edit the root POM.
- [x] **Step 4:** Implement `EmbeddedEngineRunner` with bounded shutdown and failure propagation, then compile the tool project.

### Task 3: Port four connector entry points

**Files:**
- Create: `tools/rps-migration-validation/src/main/java/io/debezium/tools/rps/validation/DebeziumMySQLExample.java`
- Create: `tools/rps-migration-validation/src/main/java/io/debezium/tools/rps/validation/DebeziumGoldenExample.java`
- Create: `tools/rps-migration-validation/src/main/java/io/debezium/tools/rps/validation/DebeziumMariaDBExample.java`
- Create: `tools/rps-migration-validation/src/main/java/io/debezium/tools/rps/validation/DebeziumOracleExample.java`
- Create: `tools/rps-migration-validation/src/test/java/io/debezium/tools/rps/validation/ConnectorPropertiesTest.java`

**Interfaces:**
- Each class exposes package-visible `static Properties connectorProperties(ValidationConfig config)` and a public `main(String[])`.
- Each `main` delegates lifecycle handling to `EmbeddedEngineRunner`.

- [x] **Step 1:** Write `ConnectorPropertiesTest` asserting connector class, `snapshot.mode=no_data`, distinct state files, and absence of default passwords; run it and confirm the four classes are missing.
- [x] **Step 2:** Implement MySQL and GoldenDB property builders from the recovered behavior using only external configuration; rerun their assertions.
- [x] **Step 3:** Implement MariaDB property building and explicit, non-overwriting offset seed input using the tracked 3.0.3 program as reference; rerun its assertions.
- [x] **Step 4:** Implement Oracle LogMiner properties for transaction-name, LOB, include lists, and file-backed state using external configuration; rerun its assertions.
- [x] **Step 5:** Add the four `main` methods and compile all tool sources under Java 21.

### Task 4: Add operator documentation and verification

**Files:**
- Create: `tools/rps-migration-validation/README.md`
- Modify: `tools/rps-migration-validation/legacy-behavior-inventory.md`

**Interfaces:**
- Consumes: the four runnable classes and configuration contract.
- Produces: copy-paste build, test, execution, evidence, and cleanup commands.

- [x] **Step 1:** Document prerequisites, why the tool is outside the reactor, configuration precedence, and every supported property/environment variable.
- [x] **Step 2:** Add complete `exec:java` commands for MySQL, GoldenDB, MariaDB, and Oracle plus expected `ENGINE_SUMMARY` evidence.
- [x] **Step 3:** Document offset/history isolation, finite duration, restart validation, credential handling, and cleanup.
- [x] **Step 4:** Run unit tests and package; report executed test counts separately from skipped live validation.
- [ ] **Step 5:** Assert the root POM does not reference the tool, scan for secrets/absolute developer paths, run `git diff --check`, review the task-owned diff, and commit the implementation.
