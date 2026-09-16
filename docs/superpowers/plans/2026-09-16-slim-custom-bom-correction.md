# Slim Custom BOM Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make RPS consume dated internal releases only for components whose production code differs from upstream Debezium 3.6.2, while resolving every unchanged component from the official `3.6.2.Final` release.

**Architecture:** Publish a new POM-only `debezium-rps-custom-bom` coordinate because the existing release BOM is immutable. The custom BOM imports the official upstream BOM and overrides only the six changed Debezium modules plus the changed binlog client; RPS imports this BOM and replaces the legacy AS400 coordinate with the official IBM i connector.

**Tech Stack:** Maven 3.9.x, JDK 21 for BOM verification/publication, JDK 17 for RPS, Nexus Repository, Maven Central.

**Spec:** `docs/superpowers/specs/2026-09-14-debezium-3.6-rps-upgrade-design.md`

## Global Constraints

- Do not delete or overwrite any existing Nexus release asset.
- Keep `3.6.2-20260916.Final` only for changed Debezium production modules.
- Keep `0.41.2-20260916.Final` only for the changed binlog client.
- Resolve unchanged Debezium and IBM i components at official `3.6.2.Final`.
- Preserve every unrelated RPS untracked file and commit only task-owned files.
- Do not represent a build with skipped tests as a test pass.

---

### Task 1: Define and verify the slim custom BOM

**Files:**
- Rename: `debezium-rps-bom/pom.xml` to `debezium-rps-custom-bom/pom.xml`
- Modify: `pom.xml`

**Interfaces:**
- Consumes: official `io.debezium:debezium-bom:3.6.2.Final`.
- Produces: `io.debezium:debezium-rps-custom-bom:3.6.2-20260916.Final`.

- [x] **Step 1: Capture a failing dependency assertion showing unchanged API, embedded, storage, and utility artifacts currently resolve to the dated version.**
- [x] **Step 2: Rename the BOM module and remove every override except connector-common, ddl-parser, binlog, MySQL, MariaDB, Oracle, and the custom client.**
- [x] **Step 3: Generate the effective BOM and assert the exact seven custom coordinates and official versions for unchanged components.**
- [x] **Step 4: Install the corrected BOM locally and run `git diff --check`.**

### Task 2: Publish and verify the corrected BOM

**Files:**
- Modify: `docs/rps-migration/task10-formal-release-2026-09-16.md`

**Interfaces:**
- Consumes: locally verified slim BOM.
- Produces: immutable Nexus POM with independently verified SHA-256.

- [x] **Step 1: Require HTTP 404 for the new BOM POM before publication.**
- [x] **Step 2: Publish only the POM using a permission-restricted temporary Maven settings file.**
- [x] **Step 3: Download the POM independently, require HTTP 200, and compare SHA-256 with the source POM.**
- [x] **Step 4: Record the old BOM and extra dated artifacts as deprecated and unused, without deleting them.**

### Task 3: Correct RPS develop dependencies

**Files:**
- Modify: `/Users/tuchief/workspace/z-rps-service/pom.xml`

**Interfaces:**
- Consumes: published slim BOM and official IBM i `3.6.2.Final` artifacts.
- Produces: an RPS dependency tree with exactly seven custom coordinates.

- [x] **Step 1: Replace the old BOM import with `debezium-rps-custom-bom`.**
- [x] **Step 2: Replace `io.debezium.connector.db2as400:ibmi-jdbc` and the direct journal parser with `io.debezium:debezium-connector-ibmi`.**
- [x] **Step 3: Run the RPS dependency tree and assert custom versions only for the six changed modules and client.**
- [x] **Step 4: Assert IBM i, API, util, config, embedded, storage, and unmodified connectors resolve to official `3.6.2.Final`, with Kafka Connect converged to `3.7.0`.**

### Task 4: Build, document, and commit

**Files:**
- Modify: `docs/superpowers/plans/2026-09-16-g0-develop-release-rps-alignment.md`
- Modify: `docs/rps-migration/task10-formal-release-2026-09-16.md`

**Interfaces:**
- Consumes: corrected Nexus BOM and RPS dependency graph.
- Produces: clean commits and reproducible verification evidence.

- [x] **Step 1: Run the complete RPS JDK 17 `clean package` with test execution status reported exactly.**
- [x] **Step 2: Review task-owned diffs and run `git diff --check` in both repositories.**
- [ ] **Step 3: Commit the corrected BOM and release evidence on the Debezium upgrade branch.**
- [ ] **Step 4: Commit only the RPS root POM on `develop` and record both commit IDs.**
- [ ] **Step 5: Re-run final repository, Nexus, and dependency-tree verification from the committed revisions.**
