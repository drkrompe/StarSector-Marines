package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The sick bay brings a share of the would-be dead home wounded and moves nobody else. */
class CasualtyFateSickBayTest {

    @Test
    void onlyTheKilledMoveAndOnlyToWounded() {
        int killed = 0, saved = 0;
        for (boolean victory : new boolean[]{true, false}) {
            for (int i = 0; i < 2_000; i++) {
                long seed = CasualtyFate.seed("relief".hashCode(), String.format("Marine %03d", i));
                MarineSoldierStatus without = CasualtyFate.roll(seed, victory);
                assertEquals(without, CasualtyFate.roll(seed, victory, 0f));
                MarineSoldierStatus with = CasualtyFate.roll(seed, victory, 0.40f);
                if (without != MarineSoldierStatus.KIA) {
                    assertEquals(without, with, "seed " + seed);
                } else {
                    killed++;
                    if (with == MarineSoldierStatus.WIA) saved++;
                    else assertEquals(MarineSoldierStatus.KIA, with);
                }
            }
        }
        assertTrue(killed > 200, "the sample holds the dead it is about");
        assertEquals(0.40, saved / (double) killed, 0.04);
    }

    @Test
    void aBetterBaySavesEveryoneALesserOneDid() {
        for (int i = 0; i < 500; i++) {
            long seed = CasualtyFate.seed("jangala".hashCode(), String.format("Marine %03d", i));
            if (CasualtyFate.roll(seed, true, 0.10f) == MarineSoldierStatus.WIA)
                assertEquals(MarineSoldierStatus.WIA, CasualtyFate.roll(seed, true, 0.25f));
        }
    }
}
