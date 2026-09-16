# G0 Nexus historical artifact inventory

Date: 2026-09-16

Repository: `maven-releases` at
`http://10.169.190.188:18587/repository/maven-releases/`

## Method and closure criteria

The anonymous Nexus REST search API was followed to exhaustion for group
`io.debezium`. The result contained 45 components; 44 matched an internal
`3.0.3-*.Final` coordinate or a custom binlog-client coordinate. Every target
JAR was downloaded to an isolated temporary directory and its SHA-256 was
compared with the Nexus asset metadata. All 44 matched.

All target releases are JAR-only: none has a POM asset. Their embedded
`pom.properties` and manifests normally retain the upstream version, so they do
not identify a source revision. Source mapping therefore used all three of:

1. exact publication time versus the ordered source commits;
2. extracted class/resource deltas between adjacent releases, including unique
   generated parser fields and compressed-event class additions; and
3. RPS `git log --all -S<version>` history across the root and module POMs.

No downloaded production JAR remains without a source boundary. One published
DDL parser (`3.0.3-260706.Final`) has no RPS history match and is classified as
published but not consumed by any reachable RPS ref.

## Source-boundary map

| Key | Exact source boundary | Confirming content |
| --- | --- | --- |
| S01 | `4ffdc2571b` | GoldenDB recycle-bin DROP grammar |
| S02 | `4d1eaab04c` | `lc_time_names` plus GoldenDB grammar |
| S03 | `79a96ac68e` | `DropIndexContext.indexName` and optional `ON tableName` |
| S04 | `3bad97a272` / `5d70eabf7f` / `7abd3bbc2c` | corrected lc-time state, unique-index resolution, SQL handling |
| S05 | `2b9fde9796` | GoldenDB `RENAME` grammar |
| S06 | `aa04cc9dd1` | defensive unique-index resolution |
| S07 | `77f114315e` | compressed row event routing |
| S08 | `7482d01c65` | compressed row decoding and DATETIME compatibility |
| S09 | `7aa7314449` | compressed Query Event routing |
| S10 | `36841110cf` | MariaDB qualified types/functions |
| S11 | `5ef49dce0d` | MariaDB grammar refactor |
| S12 | `2ce501f529` | `ADD PARTITION PARTITIONS` |
| S13 | `e1f85b26bd` | `DROP COLUMN CASCADE` |
| S14 | `f576299f47` | `COMMENT ON COLUMN` grammar and first listeners |
| S15 | `fd9216251b` | complete Comment parser listener; same release tree contains bounded diagnostics through `d4928c1e19` |
| S16 | `747962cf17` | isolated CDC diagnostic loggers |
| S17 | `947f80fc0a` | system-versioned table support |
| S18 | `769833a102` | persisted last-binlog-event timestamp |
| S19 | `bc6d3b0221` | non-regressing event timestamp |
| S20 | `5694e7c59b` | final MariaDB correctness/performance backport set |
| S21 | `bd7cc1feec` | Oracle transaction name |
| S22 | `f838ded6ae` | Oracle 10g compatibility |
| C01 | `e0849caa03` | client Query Event status-variable block |
| C02 | `6c0b6ede63` | initial MariaDB compressed-binlog client support |
| C03 | `28e4b9f616` | decompressor plus custom compressed-row delegates; includes `719d7f7e6a` |

## Nexus asset ledger

Every row below is one JAR asset. `POM` is zero for every historical release.

| Coordinate | Size | SHA-256 | Modified UTC | Source | RPS history evidence | Asset |
| --- | ---: | --- | --- | --- | --- | --- |
| `dataknown-ddl-parser:3.0.3-250815.Final` | 4401839 | `0ea08515d5fdca0afc639fa949dbec963e15ddcb72e4d0eaaf525f364c0c810a` | 2025-08-19 06:33 | S01 | `bbaff87462`, `fe87cebcd5` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-ddl-parser/3.0.3-250815.Final/dataknown-ddl-parser-3.0.3-250815.Final.jar) |
| `dataknown-ddl-parser:3.0.3-250905.Final` | 4402179 | `572d658a336ab1525ba75e049b454dce316179735e9d6b1c25a9a150519df219` | 2025-09-05 09:23 | S02 | `8018713239`, `1515142f7d` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-ddl-parser/3.0.3-250905.Final/dataknown-ddl-parser-3.0.3-250905.Final.jar) |
| `dataknown-debezium-connector-binlog:3.0.3-250905.Final` | 180551 | `2d4e6387e5d80a20f35bbbb97050c1a697b23e153a1118c63da9a2196a08e070` | 2025-09-05 09:25 | S02 | `8018713239` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-debezium-connector-binlog/3.0.3-250905.Final/dataknown-debezium-connector-binlog-3.0.3-250905.Final.jar) |
| `dataknown-debezium-connector-oracle:3.0.3-250909.Final` | 732322 | `af2ea6d10885b49dc4ba607953aa25a976b4e6738965748f9e437a5bf44eb002` | 2025-09-09 07:57 | S21 | `2bc13909a8` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-debezium-connector-oracle/3.0.3-250909.Final/dataknown-debezium-connector-oracle-3.0.3-250909.Final.jar) |
| `dataknown-ddl-parser:3.0.3-250913.Final` | 4402103 | `8b719b13a4d03fa4ea02d3bd331ffa38cbd851512ade5e248883856b4025ac3d` | 2025-09-11 16:49 | S03 | `57f5162988` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-ddl-parser/3.0.3-250913.Final/dataknown-ddl-parser-3.0.3-250913.Final.jar) |
| `dataknown-debezium-connector-binlog:3.0.3-250916.Final` | 180977 | `4383f15eb7c347d4e3fff53d0c3349d5c05bef1f1850e96ae3d83b353267557a` | 2025-09-16 12:18 | S04 | `5e4c10669a`, `b11dad196d` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-debezium-connector-binlog/3.0.3-250916.Final/dataknown-debezium-connector-binlog-3.0.3-250916.Final.jar) |
| `dataknown-debezium-connector-mysql:3.0.3-250916.Final` | 158559 | `7b35f98f295aa6f38acdffeb633cfb10cc334b7af8da65ec9d67cbb1f54a2330` | 2025-09-16 12:19 | S04 | `5e4c10669a`, `fec482fa64` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-debezium-connector-mysql/3.0.3-250916.Final/dataknown-debezium-connector-mysql-3.0.3-250916.Final.jar) |
| `dataknown-ddl-parser:3.0.3-251219.Final` | 4402198 | `bb8d386bcb256c643f16841878d54be1e7ac915c770f70a1e3e263f081574fb6` | 2026-01-21 06:37 | S05 | `e55c7effc6`, tag `V26.1.1.4` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-ddl-parser/3.0.3-251219.Final/dataknown-ddl-parser-3.0.3-251219.Final.jar) |
| `dataknown-debezium-connector-mysql:3.0.3-260112.Final` | 158798 | `0a52f227bc01798f2b20656304eb4651dec572699cf00db5797eee54b7548d47` | 2026-01-21 06:31 | S06 | `3d2a92e766`, `fec482fa64` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-debezium-connector-mysql/3.0.3-260112.Final/dataknown-debezium-connector-mysql-3.0.3-260112.Final.jar) |
| `dataknown-debezium-connector-oracle:3.0.3-20260323.Final` | 731906 | `8138abcd44601f8dd276941e0fa5517ca4fd924ad5f156cac305b8f19c39a15b` | 2026-03-23 09:00 | S22 | `00931ff488`, `89d9bc3d18` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-debezium-connector-oracle/3.0.3-20260323.Final/dataknown-debezium-connector-oracle-3.0.3-20260323.Final.jar) |
| `dataknown-mysql-binlog-connector-java:0.40.2-250905` | 222482 | `86d77645f6300bcf6bcb1af9babf2d90aa7ed9d0d97964fd5c92e10c67ea2e92` | 2025-09-05 09:28 | C01 | `378599fdc3`, `8018713239` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-mysql-binlog-connector-java/0.40.2-250905/dataknown-mysql-binlog-connector-java-0.40.2-250905.jar) |
| `dataknown-mysql-binlog-connector-java:0.40.2-20260423` | 242937 | `81db92350be7cde5c712155adf08012fa77f077988c6375cf5d6b9875c2dce6f` | 2026-04-23 09:16 | C02 | `1126a9a547`, tag `V26.2.0.6` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-mysql-binlog-connector-java/0.40.2-20260423/dataknown-mysql-binlog-connector-java-0.40.2-20260423.jar) |
| `dataknown-debezium-connector-binlog:3.0.3-20260426.Final` | 181189 | `0ce5488dc79e3add92c6e78da5a981fb1cb51d7c2fdf864c5573534f5087f54a` | 2026-04-26 10:55 | S07 | `9ae6b3b89b`, tag `V26.2.0.7` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-debezium-connector-binlog/3.0.3-20260426.Final/dataknown-debezium-connector-binlog-3.0.3-20260426.Final.jar) |
| `dataknown-mysql-binlog-connector-java:0.40.2-20260428` | 243284 | `3130b3dfacf0d49dd1e21663f7b73faa810914a65da7a32e33cdb10c10988956` | 2026-04-28 10:03 | C03 | `0ab3744a0c`, `9ceff77499` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-mysql-binlog-connector-java/0.40.2-20260428/dataknown-mysql-binlog-connector-java-0.40.2-20260428.jar) |
| `dataknown-debezium-connector-binlog:3.0.3-20260428.Final` | 181329 | `1eaf8f27dd5b2a86016e1dc9231754c84663f87d51925d86055a2af077b2361a` | 2026-04-28 10:39 | S08 | `0ab3744a0c`, `9ceff77499` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-debezium-connector-binlog/3.0.3-20260428.Final/dataknown-debezium-connector-binlog-3.0.3-20260428.Final.jar) |
| `dataknown-debezium-connector-binlog:3.0.3-20260626.Final` | 181362 | `3fb7343aceac52516a20502154d4bf83e400155ac3f58a926b0221200e4d4d36` | 2026-06-26 02:43 | S09 | `b326a54576`, `d804340738` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-debezium-connector-binlog/3.0.3-20260626.Final/dataknown-debezium-connector-binlog-3.0.3-20260626.Final.jar) |
| `dataknown-ddl-parser:3.0.3-260702.Final` | 4403535 | `a1220b838818199410a6bd9149f252b8bbbd89a59e6e1715a62ee5e5fba37bf5` | 2026-07-02 04:59 | S10 | `76748a06e4`, `c4eb93c4a6` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-ddl-parser/3.0.3-260702.Final/dataknown-ddl-parser-3.0.3-260702.Final.jar) |
| `dataknown-ddl-parser:3.0.3-260703.Final` | 4404075 | `2e3223a4e666dc0155763af1945653f87ab7f32c7c0160e8497341e9e01776e7` | 2026-07-03 03:23 | S11 | `61eb52d438`, `c515d75fe0` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-ddl-parser/3.0.3-260703.Final/dataknown-ddl-parser-3.0.3-260703.Final.jar) |
| `dataknown-ddl-parser:3.0.3-260706.Final` | 4404133 | `9c58b4dec27099c4655c984fddaf2aa5a1aff1812a022ede1e3e8eb8df2f9eb9` | 2026-07-06 03:00 | S12 | no reachable RPS ref consumes it | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-ddl-parser/3.0.3-260706.Final/dataknown-ddl-parser-3.0.3-260706.Final.jar) |
| `dataknown-ddl-parser:3.0.3-260822.Final` | 4404181 | `e39e0acbabb9bd367632a695ee2c8a1b666fd3d22c86171148991bf3726bf754` | 2026-08-22 05:04 | S13 | `835b37c335`, `581736c63a` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/dataknown-ddl-parser/3.0.3-260822.Final/dataknown-ddl-parser-3.0.3-260822.Final.jar) |
| `debezium-core:3.0.3-260823.Final` | 1281863 | `ba6fb57bd94384bde8ffa5fb7c46f54954a0774295af1cf7baa1da56e64155bb` | 2026-08-23 08:36 | S14 | `22d09b29e0`, `37e62f62ca` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-core/3.0.3-260823.Final/debezium-core-3.0.3-260823.Final.jar) |
| `debezium-connector-binlog:3.0.3-260823.Final` | 181555 | `e1abe36479014140a9b28ce33ffb608b54e04a7c80e90a5e23e5f83623e65085` | 2026-08-23 08:37 | S14 | `22d09b29e0`, `37e62f62ca` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-binlog/3.0.3-260823.Final/debezium-connector-binlog-3.0.3-260823.Final.jar) |
| `debezium-connector-mariadb:3.0.3-260823.Final` | 157705 | `9b40a6690261e386c7a00881c3c8cc2fdad09577e1042276c848d0b5dda604a9` | 2026-08-23 08:39 | S14 | `22d09b29e0`, `37e62f62ca` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-mariadb/3.0.3-260823.Final/debezium-connector-mariadb-3.0.3-260823.Final.jar) |
| `debezium-ddl-parser:3.0.3-260823.Final` | 4407085 | `41e28f32eff7db14de87a1d56b682ef86f8032c2a85f1be88b4c2f3b604554b6` | 2026-08-23 08:40 | S14 | `22d09b29e0`, `37e62f62ca` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-ddl-parser/3.0.3-260823.Final/debezium-ddl-parser-3.0.3-260823.Final.jar) |
| `debezium-core:3.0.3-260824.Final` | 1290099 | `cb00ab5c400b64a6c8dc912cd97ccf0ee586070e34699181c5220ef155548d06` | 2026-08-24 05:57 | S15 | `977aa737bc`, `82986ec68a` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-core/3.0.3-260824.Final/debezium-core-3.0.3-260824.Final.jar) |
| `debezium-ddl-parser:3.0.3-260824.Final` | 4407088 | `e39eedec100389a36ed291c5981739c125ed8594b34ff046c2c46e2312f1ab09` | 2026-08-24 05:57 | S15 | `977aa737bc`, `82986ec68a` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-ddl-parser/3.0.3-260824.Final/debezium-ddl-parser-3.0.3-260824.Final.jar) |
| `debezium-connector-binlog:3.0.3-260824.Final` | 186353 | `9c162d62f03dd97b4c31c740300e36c9c568ce0d9c9fe66af4135426aa9c5ad4` | 2026-08-24 05:57 | S15 | `977aa737bc`, `82986ec68a` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-binlog/3.0.3-260824.Final/debezium-connector-binlog-3.0.3-260824.Final.jar) |
| `debezium-connector-mariadb:3.0.3-260824.Final` | 157705 | `33dc5827ae48d2d596014a832ea48fe2d29b34108daa18372aeadcfd972f157e` | 2026-08-24 05:58 | S15 | `977aa737bc`, `82986ec68a` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-mariadb/3.0.3-260824.Final/debezium-connector-mariadb-3.0.3-260824.Final.jar) |
| `debezium-connector-binlog:3.0.3-260824.1.Final` | 188066 | `37f22a4870ca68cd6c180d3c5f56bac3353c3800ac3d143f6698740d477a27a1` | 2026-08-24 10:33 | S16 | `667ade988c`, `3c1f9e9946` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-binlog/3.0.3-260824.1.Final/debezium-connector-binlog-3.0.3-260824.1.Final.jar) |
| `debezium-ddl-parser:3.0.3-260825.Final` | 4014995 | `f11b451f4b23862bd57412f1aa37a85b9041e8f219335128acc5f74b4531c41d` | 2026-08-25 08:49 | S17 | `f26859da14`, `52a30bff6e` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-ddl-parser/3.0.3-260825.Final/debezium-ddl-parser-3.0.3-260825.Final.jar) |
| `debezium-connector-binlog:3.0.3-260825.Final` | 188153 | `9e88ccd2e1f6acb1e72916871ed7867031b13bac79185b5067a365eb06f169e8` | 2026-08-25 08:49 | S17 | `f26859da14`, `52a30bff6e` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-binlog/3.0.3-260825.Final/debezium-connector-binlog-3.0.3-260825.Final.jar) |
| `debezium-connector-mariadb:3.0.3-260825.Final` | 158661 | `4e17f7c403183dac64fd1c6083f01128e191acc8a8fd363cd7a1dfdaedb5b767` | 2026-08-25 08:49 | S17 | `f26859da14`, `52a30bff6e` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-mariadb/3.0.3-260825.Final/debezium-connector-mariadb-3.0.3-260825.Final.jar) |
| `debezium-connector-binlog:3.0.3-260901.Final` | 188713 | `9afab3b0621c121e9a8b2abe4fdb7eddca76ba450f657ab1f7e63a6654e59ae6` | 2026-09-01 10:46 | S18 | `c7e8a8ef16`, `12894ccfa8` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-binlog/3.0.3-260901.Final/debezium-connector-binlog-3.0.3-260901.Final.jar) |
| `debezium-connector-mariadb:3.0.3-260901.Final` | 158819 | `751007d86b33a9ebf72503b2efc440045a83e1126c9aef066c43627477b30033` | 2026-09-01 10:47 | S18 | `c7e8a8ef16`, `12894ccfa8` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-mariadb/3.0.3-260901.Final/debezium-connector-mariadb-3.0.3-260901.Final.jar) |
| `debezium-connector-mysql:3.0.3-260901.Final` | 158956 | `6c80e842f197b99ffe16e708a143cc540ec892a346c3d7e851ffa3dcc546f506` | 2026-09-01 10:47 | S18 | `c7e8a8ef16`, `12894ccfa8` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-mysql/3.0.3-260901.Final/debezium-connector-mysql-3.0.3-260901.Final.jar) |
| `debezium-connector-binlog:3.0.3-260905.Final` | 189491 | `11ab2df9f6a74c72ee41b06b5ae135bf2d51c984d15690d9226e9f9e1e029ec0` | 2026-09-07 01:53 | S19 | `5e946ec7b3`, `f68066d2df` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-binlog/3.0.3-260905.Final/debezium-connector-binlog-3.0.3-260905.Final.jar) |
| `debezium-connector-mariadb:3.0.3-260905.Final` | 158710 | `97a701f4021fcafd8c6f490d053b7330406f4b999fa137bad51d32c6b4814e17` | 2026-09-07 01:54 | S19 | `5e946ec7b3`, `f68066d2df` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-mariadb/3.0.3-260905.Final/debezium-connector-mariadb-3.0.3-260905.Final.jar) |
| `debezium-connector-mysql:3.0.3-260905.Final` | 158852 | `d903dff367e5bb088ef0abba3011dea862218f725081fcdce4979cc390076ccd` | 2026-09-07 01:54 | S19 | `5e946ec7b3`, `f68066d2df` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-mysql/3.0.3-260905.Final/debezium-connector-mysql-3.0.3-260905.Final.jar) |
| `debezium-core:3.0.3-260913.Final` | 1291537 | `5b21f4229d4b1196dd5a1ab9a3211b735785c1b2ea4b8f22f8ccfe5e8c7d7697` | 2026-09-13 06:12 | S20 | `4ed5f097c8`, `f210d31cec` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-core/3.0.3-260913.Final/debezium-core-3.0.3-260913.Final.jar) |
| `debezium-ddl-parser:3.0.3-260913.Final` | 4378393 | `523a6b1b4aae143db4a6d9f828e73e78083a1177063dd9f1e9670b8394642818` | 2026-09-13 06:13 | S20 | `4ed5f097c8`, `f210d31cec` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-ddl-parser/3.0.3-260913.Final/debezium-ddl-parser-3.0.3-260913.Final.jar) |
| `debezium-storage-kafka:3.0.3-260913.Final` | 16985 | `2c157b88466f0c47930db119a256decab6495c42c138f24c8909469f9c39388b` | 2026-09-13 06:13 | S20 | `4ed5f097c8`, `f210d31cec` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-storage-kafka/3.0.3-260913.Final/debezium-storage-kafka-3.0.3-260913.Final.jar) |
| `debezium-connector-binlog:3.0.3-260913.Final` | 189666 | `beab7977b306a62d1242c7d4975c108283a6634c31d9175d807cb9ed24f57726` | 2026-09-13 06:13 | S20 | `4ed5f097c8`, `f210d31cec` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-binlog/3.0.3-260913.Final/debezium-connector-binlog-3.0.3-260913.Final.jar) |
| `debezium-connector-mariadb:3.0.3-260913.Final` | 160224 | `b814f93927209f4280367cdc9238502919e3f8102e760c161cfdc5b3aaf8ce23` | 2026-09-13 06:13 | S20 | `4ed5f097c8`, `f210d31cec` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-mariadb/3.0.3-260913.Final/debezium-connector-mariadb-3.0.3-260913.Final.jar) |
| `debezium-connector-mysql:3.0.3-260913.Final` | 159480 | `e88b244a336a22a19f525500c0653169ec271836a4ad41da29762f8faa08b83b` | 2026-09-13 06:13 | S20 | `4ed5f097c8`, `f210d31cec` | [JAR](http://10.169.190.188:18587/repository/maven-releases/io/debezium/debezium-connector-mysql/3.0.3-260913.Final/debezium-connector-mysql-3.0.3-260913.Final.jar) |

## G0 result

- Nexus enumeration: complete, 44/44 target JAR assets.
- Download checksum verification: complete, 44/44 match Nexus SHA-256.
- POM presence: 0/44, consistent with the historical JAR-only convention.
- Source mapping: complete, 44/44 assigned to the exact source boundary above.
- RPS history mapping: complete; 43 assets belong to a release coordinate
  referenced by reachable RPS history and one asset is explicitly unconsumed.
- Additional delivery-line scan: all reachable RPS refs were included through
  `git log --all`; no further internal Debezium coordinate was found outside
  this ledger.

G0 is closed. The formal 3.6 release must not repeat the historical JAR-only
practice: it requires POM metadata and an internal BOM.
