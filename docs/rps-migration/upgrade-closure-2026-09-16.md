# Debezium 3.6.2 RPS upgrade closure

Date: 2026-09-16

Status: **implementation and release scope closed**

This document is the final disposition for the Debezium 3.6.2 RPS code
migration. Earlier reports retain their point-in-time wording and evidence;
where an earlier report says a gate remains open, this closure document decides
whether it was completed, waived for this upgrade, or transferred to product
regression.

## Completed in this upgrade

- G0 historical Nexus/source/RPS-consumer inventory.
- Clean 3.6.2 baseline and affected-module unit/integration qualification.
- Behavior-level `UPSTREAM`, `REBASE`, `REDESIGN`, and `RETIRE` decisions.
- MySQL, MariaDB, GoldenDB/TDSQL grammar, diagnostics, event-time, compressed
  binlog, and Oracle custom behavior implementation.
- Custom binlog client `0.41.2-20260916.Final`.
- Selective dated Debezium modules and
  `debezium-rps-custom-bom:3.6.2-20260916.Final` publication and checksum
  verification.
- RPS `develop` dependency convergence: unchanged components use official
  `3.6.2.Final`; only changed components use dated internal versions.
- Official IBM i connector alignment at `3.6.2.Final`.
- Clean RPS JDK 17 compile/package verification with skipped tests reported as
  skipped, not passed.
- Standalone, credential-safe migration validation tools for MySQL, GoldenDB,
  MariaDB, and Oracle.
- Versioned maintenance branches:
  `upgrade/debezium-3.0.3-rps` and `upgrade/debezium-3.6.2-rps`.

## Explicit waiver: MariaDB V2 compressed events

Decision: **WAIVED_FOR_3_6_2_UPGRADE**

Scope: live vendor-emitted event types 169 through 171.

Evidence retained:

- deterministic byte-level V2 reconstruction tests;
- real MariaDB V1 event types 165 through 168 from the supplied replica
  binlog;
- MySQL transaction-payload and restart evidence;
- client and affected Debezium module regression suites.

Non-claim: this waiver is not a statement that live 169-171 events passed.
None of the available servers emitted those event types, and no vendor fixture
was supplied.

Rationale: real V2 evidence is not required to close this code migration and
is not a release blocker for the currently supported environments. Reopen the
waiver if a supported customer/vendor environment emits 169-171 or supplies a
captured binlog fixture.

## Transferred to product test regression

The following are product-level acceptance work, not remaining implementation
tasks for this upgrade branch:

1. Old offset, schema-history, and RPS checkpoint continuation, including
   first-event boundary, restart, filtering, and zero-loss/duplicate criteria.
2. Independent `increment`, `full`, and `full_and_increment` acceptance across
   the product's supported source/topology matrix.
3. Shadow/canary comparison, performance and allocation testing, controlled
   failure injection, RAC/node failure, packaged log routing, and executable
   rollback rehearsal.

The product regression plan owns environment selection, duration, acceptance
records, and any release decision derived from these activities. Their absence
from this repository does not reopen the completed source migration, but they
must not be reported as executed or passed until product testing records the
evidence.

## Final gate disposition

| Gate | Disposition |
| --- | --- |
| G0 | Complete |
| G1 | Complete |
| G2 | Complete |
| G3 | Complete |
| G4 | Transferred to product regression |
| G5 | Transferred to product regression; live V2 169-171 explicitly waived for this upgrade |
| G6 | Transferred to product regression |
