package com.dillon.starsectormarines.battle.unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RosterSlotIndexTest {

    @Test
    void directIdsCanHaveGapsAndGrowWithoutCorruptingEarlierSlots() {
        RosterSlotIndex index = new RosterSlotIndex();

        index.put(1L, 0);
        index.put(200L, 7);

        assertEquals(0, index.get(1L));
        assertEquals(UnitRosterService.INVALID_INDEX, index.get(2L));
        assertEquals(UnitRosterService.INVALID_INDEX, index.get(199L));
        assertEquals(7, index.get(200L));
        assertEquals(UnitRosterService.INVALID_INDEX, index.get(201L));
        assertEquals(UnitRosterService.INVALID_INDEX, index.get(0L));
        assertEquals(UnitRosterService.INVALID_INDEX, index.get(-1L));
    }

    @Test
    void unusuallyLargeIdUsesSparseFallbackAndRemovesWithTheSameSentinel() {
        RosterSlotIndex index = new RosterSlotIndex();
        long highId = RosterSlotIndex.MAX_DENSE_ID + 17L;

        index.put(highId, 23);

        assertEquals(23, index.get(highId));
        assertEquals(UnitRosterService.INVALID_INDEX, index.get(highId + 1L));
        assertEquals(23, index.remove(highId));
        assertEquals(UnitRosterService.INVALID_INDEX, index.get(highId));
        assertEquals(UnitRosterService.INVALID_INDEX, index.remove(highId));
    }
}
