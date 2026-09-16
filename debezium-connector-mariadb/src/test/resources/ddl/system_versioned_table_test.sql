CREATE TABLE `test_system_versioned` (
    `id` int NOT NULL,
    `value` varchar(32) NOT NULL,
    `row_start` timestamp(6) GENERATED ALWAYS AS ROW START,
    `row_end` timestamp(6) GENERATED ALWAYS AS ROW END,
    PERIOD FOR SYSTEM_TIME(`row_start`, `row_end`),
    PRIMARY KEY (`id`, `row_end`)
) WITH SYSTEM VERSIONING;

INSERT INTO `test_system_versioned` (`id`, `value`) VALUES (1, 'before');

CREATE TABLE `test_implicit_system_versioned` (
    `id` int NOT NULL PRIMARY KEY,
    `value` varchar(32) NOT NULL
) WITH SYSTEM VERSIONING;

INSERT INTO `test_implicit_system_versioned` (`id`, `value`) VALUES (1, 'before');
