package com.dillon.starsectormarines.campaign;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the primitive long→int map that backs every campaign-tier
 * O(1) id-to-row-index lookup. Bugs here would corrupt
 * {@link CampaignState}'s lookup paths silently — promotion targeting the
 * wrong house, rep credited to the wrong patron, etc.
 */
public class LongIntMapTest {

    @Test
    public void putAndGetRoundTrip() {
        LongIntMap m = new LongIntMap();
        m.put(1L, 100);
        m.put(2L, 200);
        m.put(42L, 42);
        assertEquals(100, m.get(1L));
        assertEquals(200, m.get(2L));
        assertEquals(42, m.get(42L));
        assertEquals(3, m.size());
    }

    @Test
    public void missingKeyReturnsSentinel() {
        LongIntMap m = new LongIntMap();
        m.put(1L, 10);
        assertEquals(LongIntMap.NOT_FOUND, m.get(999L));
        assertFalse(m.containsKey(999L));
        assertTrue(m.containsKey(1L));
    }

    @Test
    public void zeroKeyIsRejected() {
        LongIntMap m = new LongIntMap();
        assertThrows(IllegalArgumentException.class, () -> m.put(0L, 0));
        // get(0) is silent — returns NOT_FOUND, doesn't throw
        assertEquals(LongIntMap.NOT_FOUND, m.get(0L));
    }

    @Test
    public void putOverwritesAndReturnsPrevious() {
        LongIntMap m = new LongIntMap();
        assertEquals(LongIntMap.NOT_FOUND, m.put(7L, 1));
        assertEquals(1, m.put(7L, 2));
        assertEquals(2, m.get(7L));
        assertEquals(1, m.size()); // overwrite doesn't grow
    }

    @Test
    public void growBeyondInitialCapacityPreservesEntries() {
        LongIntMap m = new LongIntMap(4); // small to force growth
        for (int i = 1; i <= 1000; i++) {
            m.put(i, i * 10);
        }
        assertEquals(1000, m.size());
        for (int i = 1; i <= 1000; i++) {
            assertEquals(i * 10, m.get(i), "key " + i + " should round-trip after growth");
        }
    }

    @Test
    public void negativeKeysWork() {
        LongIntMap m = new LongIntMap();
        m.put(-1L, 1);
        m.put(Long.MIN_VALUE, 2);
        m.put(-12345L, 3);
        assertEquals(1, m.get(-1L));
        assertEquals(2, m.get(Long.MIN_VALUE));
        assertEquals(3, m.get(-12345L));
    }

    @Test
    public void clearEmptiesMapButKeepsCapacity() {
        LongIntMap m = new LongIntMap();
        for (int i = 1; i <= 50; i++) m.put(i, i);
        m.clear();
        assertEquals(0, m.size());
        for (int i = 1; i <= 50; i++) {
            assertEquals(LongIntMap.NOT_FOUND, m.get(i));
        }
        // Should still accept new puts after clear
        m.put(99L, 7);
        assertEquals(7, m.get(99L));
    }

    @Test
    public void collisionsResolveViaLinearProbe() {
        // Keys that mod into the same slot at small capacity exercise probing.
        // Mixer is splitmix64; we don't predict which keys collide, but
        // inserting many keys into a small map provokes a high collision rate.
        LongIntMap m = new LongIntMap(4);
        long[] keys = {1L, 5L, 9L, 13L, 17L, 21L, 25L, 29L};
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], i + 1);
        }
        for (int i = 0; i < keys.length; i++) {
            assertEquals(i + 1, m.get(keys[i]));
        }
    }
}
