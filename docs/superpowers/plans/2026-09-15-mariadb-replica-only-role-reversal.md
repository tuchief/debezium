# MariaDB Replica-Only CDC Role Reversal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove that a Debezium 3.6 MariaDB connector whose hostname remains fixed on `192.168.0.137` continues incremental capture before role reversal, while `.137` is primary, and after `.137` is restored as replica.

**Architecture:** Use a dedicated external integration test and an isolated test schema. The connector never changes its configured host. Planned database role reversal is fenced by `read_only`, GTID catch-up, replication health checks, and a `finally` restoration path that returns `.136 -> .137` with `.137` read-only and `MASTER_DELAY=300`.

**Tech Stack:** MariaDB 11.8.5 GTID replication, Debezium 3.6 embedded connector test framework, JUnit 5, Maven Failsafe.

**Spec:** User-approved runtime sequence in this thread: replica-only capture, role reversal, reverse role reversal, and restoration.

## Global Constraints

- Do not touch `full_incr2` or `rps-ms-test`; use one unique `dbz_rps_role_reversal_*` schema.
- Read credentials only from environment variables; never write them to source, logs, Maven arguments, or documentation.
- `.136` and `.137` must never both be writable.
- Before every promotion, old primary is fenced read-only and candidate GTID catch-up must return success.
- Stop on any replication IO/SQL error, GTID non-containment, or missing marker.
- Final required topology is `.136` writable primary, `.137` read-only replica, `.136 -> .137`, `MASTER_DELAY=300`.
- GTID/binlog coordinates may advance and must not be rewound.

---

### Task 1: Capture live baseline and restoration contract

**Files:**
- Modify: `docs/rps-migration/task4-mariadb-external-failover-2026-09-15.md`

**Interfaces:**
- Consumes: live `SHOW SLAVE STATUS`, global GTID and role variables.
- Produces: exact baseline and final-state assertions used by Task 2.

- [x] **Step 1: Read both nodes without mutation**

  Record hostname, server ID, `read_only`, GTID positions, binlog settings, schemas, process list, replication source, SSL flags, delay, thread state, and error fields.

- [x] **Step 2: Define restoration contract**

  Require `.136 read_only=OFF`, `.137 read_only=ON`, `.137 Master_Host=.136`, IO/SQL `Yes`, all error codes zero, `Using_Gtid=Slave_Pos`, `SQL_Delay=300`, no test schema, and no unreplicated GTID.

### Task 2: Add the external role-reversal acceptance test

**Files:**
- Create: `debezium-connector-mariadb/src/test/java/io/debezium/connector/mariadb/MariaDbExternalRoleReversalIT.java`
- Modify: `docs/rps-migration/task4-mariadb-external-failover-2026-09-15.md`

**Interfaces:**
- Consumes: `RPS_MARIADB_PASSWORD`, `rps.mariadb.original.primary.host`, `rps.mariadb.fixed.connector.host`, and an explicit destructive-test opt-in.
- Produces: three ordered CDC markers and final offset/GTID/timestamp evidence from a connector fixed to `.137`.

- [x] **Step 1: Write the acceptance assertions and safety preflight**

  Assert exact starting roles/topology, fixed connector host, no non-test writers, required binlog/GTID settings, and unique schema absence. The regression caught is a connector that disconnects, snapshots, duplicates, or loses events when its fixed host changes replica/primary role.

- [x] **Step 2: Run the test without opt-in**

  Run the targeted Failsafe test without the destructive opt-in and verify it aborts before any database mutation.

- [x] **Step 3: Implement the minimal real sequence**

  Start the connector against `.137`; create and capture marker 1 from `.136`; fence and reverse roles; create and capture marker 2 from `.137`; fence and reverse roles back; create and capture marker 3 from `.136`. Assert IDs/markers, GTID semantic containment, monotonic `last_binlog_event_ts_ms`, and no snapshot records.

- [x] **Step 4: Implement unconditional restoration**

  In `finally`, stop connector, fence both nodes, use the node with the superset GTID as source only when containment is proven, restore `.136 -> .137`, set delay 300, restore roles, wait for the test-schema drop, and assert the restoration contract.

- [x] **Step 5: Run the authorized external test**

  Run only `MariaDbExternalRoleReversalIT` with credentials supplied out of band. Expected result is one passing test plus recorded phase offsets.

### Task 3: Fresh verification and evidence

**Files:**
- Modify: `docs/rps-migration/task4-mariadb-external-failover-2026-09-15.md`
- Modify: `docs/rps-migration/debezium-customization-inventory.yaml`
- Modify: `docs/superpowers/plans/2026-09-14-debezium-3.6-rps-upgrade.md`

**Interfaces:**
- Consumes: external IT output and final live database status.
- Produces: auditable acceptance evidence and the remaining RPS/MySQL boundary.

- [x] **Step 1: Run module verification**

  Run the targeted IT, MariaDB non-Docker suite, Checkstyle, and `git diff --check`; read exit codes and test counts.

- [x] **Step 2: Independently verify final topology**

  Reconnect to both nodes after the test and query roles, GTIDs, replication direction, IO/SQL state, errors, delay, existing schemas, and test-schema absence.

- [x] **Step 3: Scan for credential leakage**

  Search the worktree for the supplied database and replication passwords; expected result is zero tracked or untracked files containing either secret.

- [x] **Step 4: Record exact evidence and remaining limits**

  Document all three marker captures, role transitions, final restoration, normal GTID advancement, and that RPS process-level endpoint management remains a separate acceptance step.
