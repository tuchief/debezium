/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.binlog;

import java.util.OptionalInt;

/**
 * Reads status variables retained from binlog query events.
 */
final class BinlogQueryEventStatusVariables {

    private static final int FLAGS2_CODE = 0;
    private static final int SQL_MODE_CODE = 1;
    private static final int CATALOG_CODE = 2;
    private static final int AUTO_INCREMENT_CODE = 3;
    private static final int CHARSET_CODE = 4;
    private static final int TIME_ZONE_CODE = 5;
    private static final int CATALOG_NZ_CODE = 6;
    private static final int LC_TIME_NAMES_CODE = 7;
    private static final int CHARSET_DATABASE_CODE = 8;
    private static final int TABLE_MAP_FOR_UPDATE_CODE = 9;
    private static final int MASTER_DATA_WRITTEN_CODE = 10;
    private static final int INVOKER_CODE = 11;
    private static final int UPDATED_DB_NAMES_CODE = 12;
    private static final int MICROSECONDS_CODE = 13;

    private BinlogQueryEventStatusVariables() {
    }

    static OptionalInt getLcTimeNames(byte[] statusVariables) {
        int index = 0;
        while (statusVariables != null && index < statusVariables.length) {
            int type = statusVariables[index++] & 0xff;
            switch (type) {
                case FLAGS2_CODE:
                    index = skip(statusVariables, index, 4);
                    break;
                case SQL_MODE_CODE:
                    index = skip(statusVariables, index, 8);
                    break;
                case CATALOG_CODE:
                    index = skipLengthEncoded(statusVariables, index, true);
                    break;
                case AUTO_INCREMENT_CODE:
                    index = skip(statusVariables, index, 4);
                    break;
                case CHARSET_CODE:
                    index = skip(statusVariables, index, 6);
                    break;
                case TIME_ZONE_CODE:
                case CATALOG_NZ_CODE:
                    index = skipLengthEncoded(statusVariables, index, false);
                    break;
                case LC_TIME_NAMES_CODE:
                    if (!hasBytes(statusVariables, index, 2)) {
                        return OptionalInt.empty();
                    }
                    return OptionalInt.of((statusVariables[index] & 0xff) | ((statusVariables[index + 1] & 0xff) << 8));
                case CHARSET_DATABASE_CODE:
                    index = skip(statusVariables, index, 2);
                    break;
                case TABLE_MAP_FOR_UPDATE_CODE:
                    index = skip(statusVariables, index, 8);
                    break;
                case MASTER_DATA_WRITTEN_CODE:
                    index = skip(statusVariables, index, 4);
                    break;
                case INVOKER_CODE:
                    index = skipLengthEncoded(statusVariables, index, false);
                    index = skipLengthEncoded(statusVariables, index, false);
                    break;
                case UPDATED_DB_NAMES_CODE:
                    index = skipUpdatedDatabaseNames(statusVariables, index);
                    break;
                case MICROSECONDS_CODE:
                    index = skip(statusVariables, index, 3);
                    break;
                default:
                    return OptionalInt.empty();
            }
            if (index < 0) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }

    private static int skipLengthEncoded(byte[] statusVariables, int index, boolean hasTerminator) {
        if (!hasBytes(statusVariables, index, 1)) {
            return -1;
        }
        int length = statusVariables[index] & 0xff;
        return skip(statusVariables, index + 1, length + (hasTerminator ? 1 : 0));
    }

    private static int skip(byte[] statusVariables, int index, int length) {
        return hasBytes(statusVariables, index, length) ? index + length : -1;
    }

    private static int skipUpdatedDatabaseNames(byte[] statusVariables, int index) {
        if (!hasBytes(statusVariables, index, 1)) {
            return -1;
        }
        int databaseCount = statusVariables[index++] & 0xff;
        if (databaseCount == 254) {
            return index;
        }
        for (int i = 0; i < databaseCount; i++) {
            while (hasBytes(statusVariables, index, 1) && statusVariables[index++] != 0) {
                // Database names are zero terminated.
            }
            if (index == 0 || statusVariables[index - 1] != 0) {
                return -1;
            }
        }
        return index;
    }

    private static boolean hasBytes(byte[] statusVariables, int index, int length) {
        return index >= 0 && length >= 0 && index <= statusVariables.length - length;
    }
}
