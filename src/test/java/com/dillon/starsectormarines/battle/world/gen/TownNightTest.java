package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.pool.StoryMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownNightTest {

    /** About the night share of town battles fall at night, each by its seed; a story map keeping to the day never. */
    @Test
    void someTownBattlesFallAtNight() {
        int n = 4000, dark = 0, raidDark = 0;
        for (long seed = 0; seed < n; seed++) {
            boolean falls = TownNight.falls(seed, null);
            assertEquals(falls, TownNight.falls(seed, null));
            if (falls) dark++;
            if (TownNight.falls(seed, StoryMap.RAID_MINING)) raidDark++;
            assertFalse(TownNight.falls(seed, StoryMap.CORDON_LINE), "a Cordon keeps to the day");
        }
        assertEquals(TownNight.SHARE, dark / (float) n, .03f);
        assertEquals(dark, raidDark, "a Raid's town falls as any town does");
        assertTrue(StoryMap.RAID.nightFalls());
    }
}
