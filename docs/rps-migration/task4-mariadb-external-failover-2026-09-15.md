# MariaDB external GTID failover acceptance

Date: 2026-09-15

Status: passed and source topology restored. No connector artifact was
published and no RPS dependency was changed.

## Baseline

- primary `.136`: MariaDB 11.8.5, `server_id=136`, `read_only=OFF`;
- replica `.137`: MariaDB 11.8.5, `server_id=137`, `read_only=ON`;
- replication direction `.136 -> .137`, `Using_Gtid=Slave_Pos`;
- replica IO and SQL threads `Yes`, all replication error codes zero;
- original `SQL_Delay=300`;
- both nodes used Oracle SQL mode, ROW/FULL binlog, strict GTID, and binlog
  compression;
- initial replicated GTID was `0-136-23443` and the unique test schema did
  not exist.

## Test method

The test did not promote or write locally to the replica. It temporarily
changed only the replica delay from 300 to 0, keeping `.136` as the sole
writer, so the original topology could be restored without introducing a
replica-local GTID branch.

`MariaDbExternalFailoverIT` then:

1. created the unique `dbz_rps_failover_a09d99.events` table on the primary;
2. started the 3.6 connector on `.136` with `snapshot.mode=no_data`;
3. captured marker row 1 and persisted GTID plus
   `last_binlog_event_ts_ms`;
4. stopped the connector and restarted it on `.137` with the same logical
   connector identity, offset store, and schema history;
5. wrote marker row 2 on `.136`, waited for replica persistence, and captured
   row 2 from `.137`;
6. compared GTIDs using `MariaDbGtidSet.isContainedWithin()` and asserted the
   event timestamp did not regress.

The restart log proved the connector switched from primary file
`mariadb-bin.000004` to replica file `mariadb-bin.000002` using the recorded
GTID rather than the incompatible filename/position. The replica started from
`0-136-23452`, observed available `0-136-23453`, and advanced through the
post-switch transaction.

The first run reached the post-switch event but the test used ordinary string
containment for `0-136-23446` versus `0-136-23447`; that assertion was invalid
for MariaDB GTID sequence semantics. It was replaced by the connector's real
domain-aware `MariaDbGtidSet` containment operation. The corrected external IT
passed 1/1, and Checkstyle completed successfully.

## Restoration evidence

After the connector stopped, the test schema was dropped on the primary and
the drop was confirmed on the replica before restoring delay. Final state:

- primary schema count: 0;
- replica schema count: 0;
- primary/replica GTID: `0-136-23455` on both sides;
- `.136`: `read_only=OFF`;
- `.137`: `read_only=ON`, IO/SQL `Yes`, all errors zero, `SQL_Delay=300`;
- no `.137`-origin GTID was introduced.

GTID/binlog coordinates necessarily advanced from the baseline because the
authorized test generated real replicated transactions. They were not and
must not be destructively rewound.

## Boundary

This proves planned connector relocation from primary to an up-to-date replica
using copied offset/schema-history state. It does not prove automatic endpoint
discovery, replica promotion, writes on a promoted replica, or failback from a
new GTID origin; those require a separately authorized topology mutation.

## Replica-only capture through role reversal and failback

The separately authorized `MariaDbExternalRoleReversalIT` kept the connector
hostname fixed on `.137` for the complete run. It did not stop or reconfigure
the connector between phases:

1. with `.136` primary and `.137` replica, marker 1
   (`replica-before-reversal`) was written on `.136`, replicated to `.137`, and
   captured from `.137`;
2. after fencing `.136`, waiting for GTID containment, promoting `.137`, and
   attaching `.136` as a `current_pos` replica, marker 2
   (`fixed-host-as-primary`) was written and captured locally on `.137` and
   replicated to `.136`;
3. after the symmetric fenced switch back, marker 3
   (`fixed-host-restored-as-replica`) was written on `.136`, replicated to
   `.137`, and captured from the same continuing connector connection.

The successful run passed 1/1 in 22.00 seconds. Its record offsets progressed
through the three phases as follows:

- phase 1: GTID offset `0-136-23465`, event `server_id=136`, timestamp
  `1789480865000`;
- phase 2: GTID offset `0-136-23466`, event `server_id=137`, timestamp
  `1789480869000`;
- phase 3: GTID offset `0-137-23467`, event `server_id=136`, timestamp
  `1789480873000`.

The event operations were all incremental create events, IDs were exactly
`1,2,3`, GTID containment progressed, and `last_binlog_event_ts_ms` was
monotonic. The connector's final recorded stream offset advanced to
`0-136-23468` before shutdown.

The first two attempts were stopped and restored safely while refining the
acceptance harness:

- `MASTER_USE_GTID=current_pos` correctly reports `Using_Gtid=Current_Pos`, not
  the original topology's `Slave_Pos`;
- `MASTER_GTID_WAIT()` must not be used to wait for a GTID that was originally
  generated locally by the node being attached as a replica. That gate was
  replaced by replication-thread health plus semantic containment of
  `gtid_binlog_pos`.

After the passing run, both nodes ended at `0-136-23469`. `.136` retained the
promoted node's replicated history in `gtid_slave_pos` as `0-137-23467`, which
is expected and proves the new GTID origin returned safely. Final topology was
independently queried and confirmed:

- `.136`: `server_id=136`, `read_only=OFF`, no slave configuration;
- `.137`: `server_id=137`, `read_only=ON`, source `.136`, IO/SQL `Yes`, all
  replication errors zero, `Using_Gtid=Slave_Pos`, `SQL_Delay=300`;
- both sides had only the pre-existing `full_incr2` and `rps-ms-test` schemas;
- the role-reversal test schema was absent on both sides.

After restoration, external idle clients appeared on `.137`. The successful
GTID sequence contained exactly the six expected transactions (create schema,
create table, three markers, and drop schema), so they did not add a committed
transaction during the writable window. The reusable test now refuses to
start when either node has a non-replication client, preventing a later drill
from opening an unintended write window for shared clients.

Fresh local verification after adding the reusable safety preflight:

- explicit MariaDB non-Docker unit set: 314 tests, 0 failures, 0 errors,
  0 skips;
- targeted test source compilation: successful;
- Checkstyle: 0 violations;
- credential scan outside build output: no supplied database or replication
  password found;
- `git diff --check`: clean.

The external role reversal was not repeated after idle shared clients appeared
on `.137`; the new two-node client preflight intentionally prevents that run.
The already completed successful external run used the same functional test
sequence, before only that additional refusal condition was added.
