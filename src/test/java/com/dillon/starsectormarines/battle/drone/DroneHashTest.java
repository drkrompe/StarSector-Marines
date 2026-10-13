package com.dillon.starsectormarines.battle.drone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DroneHashTest {

    @Test
    void theSameIdAndSaltAlwaysGiveTheSameValue() {
        assertEquals(DroneHash.unit(42L, 3), DroneHash.unit(42L, 3));
    }

    @Test
    void differentIdsAndDifferentSaltsGiveDifferentValues() {
        assertNotEquals(DroneHash.unit(42L, 3), DroneHash.unit(43L, 3));
        assertNotEquals(DroneHash.unit(42L, 3), DroneHash.unit(42L, 4));
    }

    @Test
    void valuesStayInTheUnitIntervalAndSpreadAcrossIt() {
        float low = 1f;
        float high = 0f;
        for (long id = 0; id < 2000; id++) {
            float v = DroneHash.unit(id, 9);
            assertTrue(v >= 0f && v < 1f, "out of range: " + v);
            low = Math.min(low, v);
            high = Math.max(high, v);
        }
        assertTrue(low < 0.05f && high > 0.95f, "range covered was " + low + ".." + high);
    }
}
