package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartGunshipTest {

    @Test
    void aShootIsSizedToKillItsTargetUpToItsCap() {
        // A fresh outpost: 320 structure behind 140 armour takes six 80-damage shells to strip, and one over.
        assertEquals(7, HeartGunship.shellsFor(320f, 140f));
        assertEquals(2, HeartGunship.shellsFor(40f, 0f));
        assertEquals(HeartGunship.MOST_SHELLS, HeartGunship.shellsFor(2000f, 500f));
    }

    @Test
    void aDefenceCoversTheWayWhenItsReachMeetsTheSegment() {
        assertEquals(5f, HeartGunship.segmentDistance(5f, 5f, 0f, 0f, 10f, 0f), 1e-4f);
        assertEquals(5f, HeartGunship.segmentDistance(13f, 4f, 0f, 0f, 10f, 0f), 1e-4f, "past the end it is the distance to the end");
    }

    @Test
    void theBaseShellsOnlyAntiAirTheFieldHasSeenWithinItsGuns() {
        int now = 10_000;
        assertTrue(HeartGunship.shellable(now, now - 30, 120f), "seen a second ago, in range");
        assertTrue(HeartGunship.shellable(now, now - HeartGunship.KNOWN_TICKS, HeartGunship.GUN_RANGE));
        assertFalse(HeartGunship.shellable(now, now - HeartGunship.KNOWN_TICKS - 1, 120f), "not seen for too long: unknown");
        assertFalse(HeartGunship.shellable(now, now, HeartGunship.GUN_RANGE + 1f), "past the guns: a risk the sortie takes");
    }
}
