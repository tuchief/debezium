# G0, Develop Integration, Release, and RPS Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the historical-artifact inventory, commit the verified client and Debezium upgrade branches, publish locked formal artifacts and an internal BOM, then align and verify RPS develop.

**Architecture:** Treat Nexus inventory, Git integration, artifact publication, and RPS consumption as four ordered release gates. Every later gate consumes immutable coordinates and checksums produced by the prior gate; no SNAPSHOT coordinate may enter RPS.

**Tech Stack:** Nexus Repository REST API, Git worktrees, Maven 3.9.x, JDK 21 for Debezium/client, JDK 17 for RPS, SHA-256, Gitee/GitHub remotes.

**Spec:** `docs/superpowers/specs/2026-09-14-debezium-3.6-rps-upgrade-design.md`

## Global Constraints

- Preserve unrelated dirty and untracked files in every repository.
- Never force-push or overwrite an existing Nexus release coordinate.
- Use fixed date-formatted coordinates `0.41.2-20260916.Final` and `3.6.2-20260916.Final`, plus an internal BOM with POM metadata.
- Verify uploaded assets by HTTP status and SHA-256 before changing RPS.
- Change only Debezium dependency declarations on RPS `develop`; reject residual
  3.0.3 from the RPS Debezium runtime, duplicate clients, and duplicate Kafka
  Connect versions. Consume the official IBM i connector at `3.6.2.Final`.
- Do not call package success a test pass when tests were skipped.

---

### Task 1: Close G0 historical inventory

**Files:**
- Modify: `docs/rps-migration/debezium-customization-inventory.yaml`
- Create: `docs/rps-migration/g0-nexus-artifact-inventory-2026-09-16.md`

**Interfaces:**
- Consumes: Nexus `maven-releases` search/component APIs, Git refs, RPS POM history.
- Produces: complete asset table with coordinate, asset URL, size, SHA-256, publication timestamp, POM presence, source mapping, and consuming RPS commit/release evidence.

- [x] **Step 1: Enumerate every `io.debezium` Nexus component and asset matching internal `3.0.3-*` releases or custom binlog-client artifacts, following continuation tokens to exhaustion.**
- [x] **Step 2: Cross-reference every coordinate against all Git refs and RPS history; classify exact, bounded historical, or unresolved mappings.**
- [x] **Step 3: Download unresolved JARs to a temporary directory and compare manifests, class lists, and bytecode hashes against candidate source builds.**
- [x] **Step 4: Update the inventory, run YAML parsing and `git diff --check`, and close G0 only when no production artifact remains unresolved.**

### Task 2: Commit both verified upgrade worktrees

**Files:**
- Commit all verified task-owned files in `/Users/tuchief/workspace/mysql-binlog-connector-java-0.41-rps`.
- Commit all verified task-owned files in `/Users/tuchief/workspace/debezium-3.6-rps`.

**Interfaces:**
- Consumes: closed G0 and the verified uncommitted worktrees.
- Produces: commits on client `upgrade/mysql-binlog-0.41-rps` and Debezium `upgrade/debezium-3.6-rps` with clean task-owned diffs.

- [x] **Step 1: Fetch remotes, verify merge bases, and ensure target develop heads have not changed during validation.**
- [x] **Step 2: Run the full approved client and affected Debezium verification suites on the exact trees to be committed.**
- [x] **Step 3: Commit the client changes on `upgrade/mysql-binlog-0.41-rps` and verify the committed tree.**
- [x] **Step 4: Commit the Debezium changes on `upgrade/debezium-3.6-rps`, preserving the exact `v3.6.2.Final` base, and verify the committed tree.**
- [x] **Step 5: Record commit IDs and preserve the host-managed worktrees.**

Implementation commits: client `9e3a4c08b2` plus release version commit
`097cef8`; Debezium customization commit `6124e4fba9`. Both host-managed
worktrees remain in place on their upgrade branches.

### Task 3: Lock and publish formal artifacts

**Files:**
- Modify fixed release versions in the client and Debezium develop trees.
- Create an internal BOM module/POM under the Debezium repository.
- Create: `docs/rps-migration/task10-formal-release-2026-09-16.md`

**Interfaces:**
- Consumes: immutable develop commits from Task 2.
- Produces: client `0.41.2-20260916.Final`, Debezium component set and BOM `3.6.2-20260916.Final`, verified in Nexus.

- [x] **Step 1: Select unused formal coordinates and verify HTTP 404 for every intended asset before upload.**
- [x] **Step 2: Pin the complete custom dependency graph and add the internal BOM with explicit component versions.**
- [x] **Step 3: Build and test with the approved JDKs; record executed and skipped checks separately.**
- [x] **Step 4: Publish client first, then Debezium components and BOM with POM metadata.**
- [x] **Step 5: Download every published JAR/POM, require HTTP 200 and matching SHA-256, and record asset metadata.**

Publication evidence is recorded in
`docs/rps-migration/task10-formal-release-2026-09-16.md`.

### Task 4: Align RPS develop and verify convergence

**Files:**
- Modify only the exact Maven POM files on `/Users/tuchief/workspace/z-rps-service` that currently declare Debezium/client versions.

**Interfaces:**
- Consumes: verified Task 3 BOM and component coordinates.
- Produces: RPS develop dependency tree containing only the formal 3.6 runtime.

- [x] **Step 1: Refresh `origin/develop`, preserve all unrelated RPS working-tree changes, and identify the exact dependency declarations.**
- [x] **Step 2: Replace direct 3.0.3/SNAPSHOT declarations with the formal BOM and any required explicit runtime components.**
- [x] **Step 3: Run dependency trees for every consuming RPS module and reject old RPS Debezium releases, duplicate binlog clients, duplicate Oracle connectors, or multiple Kafka Connect APIs.**
- [x] **Step 4: Run the RPS JDK 17 clean build and report the exact test-execution boundary.**
- [x] **Step 5: Commit only the exact RPS dependency files on develop and record the final commit and resolved coordinates.**

RPS `develop` corrective commit `bbee51a785` imports
`io.debezium:debezium-rps-custom-bom:3.6.2-20260916.Final`. The JDK 17 clean
package completed successfully for all five reactor modules with tests
explicitly skipped. The resolved tree contains exactly six dated Debezium
modules plus the dated custom client; every unchanged component, including IBM
i, resolves to official `3.6.2.Final`. Kafka Connect remains converged to
`3.7.0` and only one Oracle connector is present.
