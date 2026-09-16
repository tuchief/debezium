# Clean patch-replay build

Date: 2026-09-15 to 2026-09-16

## Objective

Prove that the uncommitted RPS binlog-client and Debezium changes can be
replayed onto their exact upstream tags and built without relying on the
developer's normal Maven cache. This exercise did not commit, publish, deploy,
or modify RPS.

## Isolated inputs

- Binlog client base: `v0.41.2` at
  `d01d65d957f905e7cc32c29cb6ce9218e78eb949`.
- Debezium base: `v3.6.2.Final` at
  `02810e25b19c04e5095b2b6fbbdcbae549a69f19`.
- Temporary root: `/tmp/rps-repro.FpeIHT`.
- Empty Maven repository: `/tmp/rps-repro.FpeIHT/m2`.
- Build runtime: Azul JDK 21.0.12.1; connector bytecode target remained Java
  17.

Tracked diffs were streamed into detached temporary worktrees and the explicit
untracked-file manifests were copied separately. Before the final source-tree
documentation updates, the replayed Debezium code tree was identical to the
source code tree when `.git`, `target`, and `docs` were excluded. The client
trees were identical with only `.git` and `target` excluded.

## Commands

The client was built and installed first so the Debezium reactor could resolve
the aligned local coordinate:

```bash
JAVA_HOME=/Users/tuchief/Library/Java/JavaVirtualMachines/azul-21.0.12.1/Contents/Home \
./mvnw -Dmaven.repo.local=/tmp/rps-repro.FpeIHT/m2 \
  -DskipITs=true -Dgpg.skip=true install
```

The affected Debezium reactor was then built from the clean replay:

```bash
JAVA_HOME=/Users/tuchief/Library/Java/JavaVirtualMachines/azul-21.0.12.1/Contents/Home \
./mvnw -Dmaven.repo.local=/tmp/rps-repro.FpeIHT/m2 \
  -pl debezium-util,debezium-connector-common,debezium-connector-binlog,\
debezium-connector-mysql,debezium-connector-mariadb,debezium-connector-oracle \
  -am -Dquick -Dmaven.test.skip=true -DskipITs \
  -Dcheckstyle.skip=true -Drevapi.skip=true install
```

The first Debezium run completed modules 1 through 17, then failed while
downloading
`io.quarkus:quarkus-bootstrap-gradle-resolver:3.33.2`: Central closed a
Content-Length body after 966,656 of 2,939,347 bytes and the Gradle mirror then
terminated its TLS handshake. This was not a compilation failure. The same
repository was resumed without deleting any successful artifacts:

```bash
JAVA_HOME=/Users/tuchief/Library/Java/JavaVirtualMachines/azul-21.0.12.1/Contents/Home \
./mvnw -Dmaven.repo.local=/tmp/rps-repro.FpeIHT/m2 \
  -pl debezium-util,debezium-connector-common,debezium-connector-binlog,\
debezium-connector-mysql,debezium-connector-mariadb,debezium-connector-oracle \
  -am -Dquick -Dmaven.test.skip=true -DskipITs \
  -Dcheckstyle.skip=true -Drevapi.skip=true install \
  -rf :debezium-testing-testcontainers
```

The interrupted artifact downloaded completely on retry. The resumed five
modules all succeeded: testing-testcontainers, binlog, MySQL, Oracle, and
MariaDB. Together with the first run, all 22 selected reactor modules built and
installed successfully. Tests were intentionally skipped in this packaging
run; the client 127-test suite, MySQL 8.4 real integration tests, MariaDB real
compressed-event test, and affected Debezium unit suites are separate evidence
recorded in the Task 5 report.

## Dependency proof

The clean repository dependency tree resolved exactly one custom client:

```text
debezium-connector-binlog
\- io.debezium:dataknown-mysql-binlog-connector-java:0.41.2-rps.1-SNAPSHOT

debezium-connector-mysql
\- io.debezium:dataknown-mysql-binlog-connector-java:0.41.2-rps.1-SNAPSHOT

debezium-connector-mariadb
\- debezium-connector-binlog
   \- io.debezium:dataknown-mysql-binlog-connector-java:0.41.2-rps.1-SNAPSHOT
```

No upstream `mysql-binlog-connector-java` coordinate appeared in those three
filtered trees.

## Artifact checksums

SHA-256 values from the clean replay worktrees were:

| Artifact | SHA-256 |
| --- | --- |
| `dataknown-mysql-binlog-connector-java-0.41.2-rps.1-SNAPSHOT.jar` | `bfe75be892ee4b0074dc0600ebbe7a144949b963a488f82017d7a0aa779300d4` |
| `debezium-connector-binlog-3.6.2.Final.jar` | `71b6a0d7a0830bb75120b0e0e033a2c81627789aec3380dbaca312e03aac221b` |
| `debezium-connector-mysql-3.6.2.Final.jar` | `d34897a7c98fc083198b8db6d8cf3eb5d043cc633cdcfbdd96b7d6737c127e2a` |
| `debezium-connector-mariadb-3.6.2.Final.jar` | `be6e3f8a7aa0b624715436a5fdf97c437ac44b21fb49c63a54ee357b1473324c` |
| `debezium-connector-oracle-3.6.2.Final.jar` | `16069bf2481eede8ed64062c2e0a43dcf585dab6023346132b1dc944b5792021` |

The source client was rebuilt with the same JDK and clean repository and again
passed 127/127 tests. Its archive SHA-256 was
`79e7351b68102b70de6375fc416f2c1bb2a0e7ef2ce408fae7ead96556e48e1f`.
The archive hash differs from the replay JAR because Maven did not normalize
ZIP entry timestamps. After extraction, `diff -qr` returned zero differences.
The evidence therefore proves source and class/resource content
reproducibility, not byte-for-byte JAR reproducibility.

## Reproducibility boundary

The isolated Maven repository grew to 564 MiB. A clean run resolved several
current versions through upstream BOMs/ranges, including JUnit 6.0.3, Jackson
2.21.2, Quarkus 3.33.2, Infinispan 16.1.3, and Oracle JDBC 23.26.1.0.0. These
versions can change after the date of this run. A release build must therefore
add one of the following before claiming cross-date reproducibility:

1. publish and consume an internal BOM that pins the complete resolved graph;
2. archive a verified Maven repository or dependency lock manifest; and
3. normalize archive timestamps if byte-identical JARs are a release
   requirement.

Until then, the supported claim is: the exact patch sets replay cleanly and
their affected modules build from an initially empty repository on the stated
date and toolchain.

## Cleanup

After the paths were validated, both temporary detached worktrees were removed
through their owning Git repositories and `/tmp/rps-repro.FpeIHT`, including
the 564 MiB Maven repository, was deleted. A final existence and worktree-list
check found no `rps-repro` entry. The two source worktrees, their uncommitted
changes, and all unrelated user files remained outside that cleanup scope.
