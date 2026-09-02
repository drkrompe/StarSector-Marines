package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PolityDefenceMissionKeyTest {

    @Test
    void roundTripsMarketSlotAndEventKey() {
        PolityDefenceMissionKey key =
                PolityDefenceMissionKey.parse(PolityDefenceMissionKey.encode(7, -42L));

        assertNotNull(key);
        assertEquals(7, key.marketSlot);
        assertEquals(-42L, key.eventKey);
    }

    @Test
    void encodesUnderItsOwnPrefix() {
        assertEquals("polity-defence:7:44", PolityDefenceMissionKey.encode(7, 44L));
    }

    /** A Garrison defence's id must not parse here, or the two would settle each other. */
    @Test
    void rejectsAnotherKindOfMissionId() {
        assertNull(PolityDefenceMissionKey.parse("garrison-defense:1:44"));
        assertNull(PolityDefenceMissionKey.parse(null));
        assertNull(PolityDefenceMissionKey.parse("polity-defence:7"));
        assertNull(PolityDefenceMissionKey.parse("polity-defence:7:44:1"));
        assertNull(PolityDefenceMissionKey.parse("polity-defence:seven:44"));
        assertNull(PolityDefenceMissionKey.parse("polity-defence:7:forty-four"));
    }

    /** Zero is the "no event" sentinel the raid readers never emit. */
    @Test
    void rejectsTheZeroEventKey() {
        assertNull(PolityDefenceMissionKey.parse("polity-defence:7:0"));
    }
}
