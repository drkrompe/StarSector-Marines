package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A casualty comes home or dies; a lost field kills more, and the sick bay moves only the dead. */
class CasualtyFateSickBayTest {

    private static long seed(String event, int marine) {
        String id = UUID.nameUUIDFromBytes(("marine-" + marine).getBytes(StandardCharsets.UTF_8)).toString();
        return CasualtyFate.seed(event.hashCode(), id);
    }

    @Test
    void aCasualtyIsEitherWoundedOrKilledAndALostFieldKillsMore() {
        int wonDead = 0, lostDead = 0;
        for (int i = 0; i < 1_000; i++) {
            for (boolean victory : new boolean[]{true, false}) {
                MarineSoldierStatus fate = CasualtyFate.roll(seed("relief", i), victory);
                assertTrue(fate == MarineSoldierStatus.WIA || fate == MarineSoldierStatus.KIA, fate.name());
                if (fate == MarineSoldierStatus.KIA) {
                    if (victory) wonDead++;
                    else lostDead++;
                }
            }
        }
        assertTrue(lostDead > wonDead, lostDead + " against " + wonDead);
    }

    @Test
    void onlyTheKilledMoveAndOnlyToWounded() {
        int killed = 0, saved = 0;
        for (boolean victory : new boolean[]{true, false}) {
            for (int i = 0; i < 2_000; i++) {
                long seed = seed("relief", i);
                MarineSoldierStatus without = CasualtyFate.roll(seed, victory);
                assertEquals(without, CasualtyFate.roll(seed, victory, 0f));
                MarineSoldierStatus with = CasualtyFate.roll(seed, victory, 0.40f);
                if (without != MarineSoldierStatus.KIA) {
                    assertEquals(without, with, "seed " + seed);
                } else {
                    killed++;
                    if (with == MarineSoldierStatus.WIA) saved++;
                }
            }
        }
        assertTrue(killed > 200, "the sample holds the dead it is about");
        assertEquals(0.40, saved / (double) killed, 0.04);
    }

    @Test
    void aBetterBaySavesEveryoneALesserOneDid() {
        for (int i = 0; i < 500; i++) {
            long seed = seed("jangala", i);
            if (CasualtyFate.roll(seed, true, 0.10f) == MarineSoldierStatus.WIA)
                assertEquals(MarineSoldierStatus.WIA, CasualtyFate.roll(seed, true, 0.25f));
        }
    }

    @Test
    void aWoundedFateLeavesTheMarineOnDutyWithTheWoundOnRecord() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(1);
        MarineSoldier marine = roster.soldiers().get(0);
        roster.applySoldierOutcome(Map.of(marine.id(), MarineSoldierStatus.WIA));
        assertEquals(MarineSoldierStatus.ACTIVE, marine.status());
        assertEquals(1, marine.career().timesWounded());
    }
}
