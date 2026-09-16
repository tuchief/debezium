# Binlog Client, MySQL, and Clean Build Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the custom binlog-client, MySQL integration, and isolated-build gates without committing or publishing.

**Architecture:** Add one opt-in external MariaDB protocol acceptance test, reuse Debezium's Docker-backed MySQL 8.4 amd64 integration harness, then replay both working-tree patches onto detached clean bases and build against an empty Maven repository.

**Tech Stack:** Java 17 connector target, JDK 21 build runtime, Maven 3.9.12, TestNG/JUnit 5, Docker 29.4, MariaDB 11.8.5, MySQL 8.4.

**Spec:** `docs/superpowers/specs/2026-09-15-binlog-client-mysql-clean-build-design.md`

## Global Constraints

- Do not commit, push, publish, deploy, or edit RPS.
- Do not change MariaDB roles, replication direction, or persistent configuration; replica delay may be 0 only inside the test and must return to 300.
- Store no credentials in source, reports, Maven arguments, or shell history.
- Treat event types 165-168 as real MariaDB V1 evidence; do not claim 169-171 real evidence.
- Use JDK 21 for the Debezium build and preserve Java 17 connector bytecode target.
- Remove Docker containers and validated temporary worktrees after verification.

---

### Task 1: External MariaDB compressed-event acceptance

**Files:**
- Create: `/Users/tuchief/workspace/mysql-binlog-connector-java-0.41-rps/src/test/java/com/github/shyiko/mysql/binlog/MariadbExternalCompressedEventsIT.java`
- Modify: `docs/rps-migration/task5-compressed-binlog-redesign-2026-09-14.md`

**Interfaces:**
- Consumes: root password from `RPS_MARIADB_PASSWORD`, primary `.136`, fixed reader `.137`.
- Produces: exact 165-168 event-type and decoded-row evidence.

- [x] **Step 1: Add an opt-in preflight that fails before mutation without `rps.mariadb.external.compressed.enabled=true`.**
- [x] **Step 2: Run the test without opt-in and record the expected pre-mutation failure.**
- [x] **Step 3: Implement unique-schema DDL/DML, exact event assertions, and unconditional cleanup.**
- [x] **Step 4: Run the authorized test and independently verify replication topology and schema cleanup.**
- [x] **Step 5: Run the client unit suite, formatting, Checkstyle-equivalent checks, and credential scan.**

### Task 2: MySQL 8.4 real integration acceptance

**Files:**
- Modify only if a focused test exposes a real regression.
- Modify: `docs/rps-migration/task5-compressed-binlog-redesign-2026-09-14.md`

**Interfaces:**
- Consumes: existing MySQL Docker/Failsafe profiles and aligned client artifact.
- Produces: MySQL 8.4 amd64 functional results and container cleanup evidence.

- [x] **Step 1: Build/install the aligned client locally and verify its dependency coordinate.**
- [x] **Step 2: Run focused MySQL 8.4 transaction-payload and DDL/DML integration tests.**
- [x] **Step 3: Run MySQL GTID/restart and event-time continuation coverage.**
- [x] **Step 4: Verify all Docker containers created by the test are removed.**

### Task 3: Empty-Maven-repository patch-replay build

**Files:**
- Create: `docs/rps-migration/task9-clean-patch-replay-build-2026-09-15.md`

**Interfaces:**
- Consumes: uncommitted tracked and untracked source state from both isolated worktrees.
- Produces: reproducible build commands, checksums, and limitations.

- [x] **Step 1: Create validated temporary detached worktrees at client `v0.41.2` and Debezium `v3.6.2.Final`.**
- [x] **Step 2: Stream tracked diffs and copy the exact untracked-file manifest into each temporary worktree.**
- [x] **Step 3: Build/install the client with JDK 21 and an empty temporary Maven repository.**
- [x] **Step 4: Build and test the affected Debezium modules against the same repository.**
- [x] **Step 5: Record JAR SHA-256 values and dependency-tree proof.**
- [x] **Step 6: Validate temporary paths, remove temporary worktrees/repository, and confirm source worktrees are unchanged.**

### Task 4: Final review

**Files:**
- Modify: `docs/rps-migration/debezium-customization-inventory.yaml`
- Modify: `docs/superpowers/plans/2026-09-14-debezium-3.6-rps-upgrade.md`

**Interfaces:**
- Consumes: Tasks 1-3 evidence.
- Produces: exact closed and remaining release gates.

- [x] **Step 1: Review every custom-client production diff for protocol bounds, compatibility modes, and API surface.**
- [x] **Step 2: Run `git diff --check`, YAML parsing, credential scanning, and graph coverage checks.**
- [x] **Step 3: Document real V1, synthetic V2, MySQL architecture, and no-publication boundaries.**
