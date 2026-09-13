/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.connector.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import io.debezium.connector.mariadb.gtid.MariaDbGtidSet;
import io.debezium.doc.FixFor;

/**
 * MariaDB-specific global transaction identifier tests.
 *
 * @author Chris Cranford
 */
public class MariaDbGtidSetTest {

    private static final String DOMAIN_SERVER_ID = "1-2";

    private MariaDbGtidSet gtids;

    @Test
    public void shouldParseGtid() {
        gtids = new MariaDbGtidSet(DOMAIN_SERVER_ID + "-3");
        assertThat(gtids.forStreamId(new MariaDbGtidSet.MariaDbGtidStreamId(1, 2)).hasSequence(3)).isTrue();
    }

    @Test
    public void shouldBeContainedWithinWhenSequenceIsBehindOnSameStream() {
        assertThat(new MariaDbGtidSet("0-1-3").isContainedWithin(new MariaDbGtidSet("0-1-5"))).isTrue();
        assertThat(new MariaDbGtidSet("0-1-5").isContainedWithin(new MariaDbGtidSet("0-1-5"))).isTrue();
    }

    @Test
    public void shouldNotBeContainedWithinWhenSequenceIsAheadOnSameStream() {
        assertThat(new MariaDbGtidSet("0-1-5").isContainedWithin(new MariaDbGtidSet("0-1-3"))).isFalse();
    }

    @Test
    @FixFor("debezium/dbz#1672")
    public void shouldBeContainedWithinAcrossServerIdChangeWithinSameDomain() {
        MariaDbGtidSet history = new MariaDbGtidSet("0-2-892554529");
        MariaDbGtidSet offset = new MariaDbGtidSet("0-1-964871206");
        assertThat(history.isContainedWithin(offset)).isTrue();
    }

    @Test
    @FixFor("debezium/dbz#1672")
    public void shouldNotBeContainedWithinWhenSequenceIsAheadAcrossServerIdChange() {
        MariaDbGtidSet ahead = new MariaDbGtidSet("0-1-964871206");
        MariaDbGtidSet behind = new MariaDbGtidSet("0-2-892554529");
        assertThat(ahead.isContainedWithin(behind)).isFalse();
    }

    @Test
    public void shouldNotBeContainedWithinWhenDomainIsAbsentInOther() {
        assertThat(new MariaDbGtidSet("0-1-100").isContainedWithin(new MariaDbGtidSet("1-1-100"))).isFalse();
    }

    @Test
    public void shouldBeContainedWithinAcrossMultipleDomainsIgnoringServerId() {
        MariaDbGtidSet history = new MariaDbGtidSet("0-2-100,1-2-50");
        MariaDbGtidSet offset = new MariaDbGtidSet("0-1-200,1-3-80");
        assertThat(history.isContainedWithin(offset)).isTrue();
    }

    @Test
    public void shouldNotBeContainedWithinWhenAnyDomainIsAhead() {
        MariaDbGtidSet history = new MariaDbGtidSet("0-2-100,1-2-90");
        MariaDbGtidSet offset = new MariaDbGtidSet("0-1-200,1-3-80");
        assertThat(history.isContainedWithin(offset)).isFalse();
    }

    @Test
    public void shouldMergeServersOfSameDomainWhenLookingUpByDomain() {
        MariaDbGtidSet gtidSet = new MariaDbGtidSet("0-1-100,0-2-200");
        MariaDbGtidSet.MariaDbStreamSet domain0 = gtidSet.forDomain(0);
        assertThat(domain0).isNotNull();
        assertThat(domain0.hasSequence(100)).isTrue();
        assertThat(domain0.hasSequence(200)).isTrue();
        assertThat(gtidSet.forDomain(9)).isNull();
    }

}
