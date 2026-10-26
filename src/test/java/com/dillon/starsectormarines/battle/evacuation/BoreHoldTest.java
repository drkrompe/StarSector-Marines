package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.evacuation.BoreStrainTable.Slot;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A bore's hold (`colony-breach-nouns.md`, Hold), asked of the table, the bore and the surge plan directly. */
class BoreHoldTest {

    /** A bore grown to {@code tier} by feeding. */
    private static SwarmBore boreAt(BoreTier tier) {
        SwarmBore bore = new SwarmBore(1, false, 1L, 0, 0, 0, 1f);
        bore.feed(tier.growthThreshold / SwarmBore.GROWTH_PER_BIOMASS + 0.01f, 0);
        assertEquals(tier, bore.tier());
        return bore;
    }

    @Test
    void eachTierHoldsAtLeastWhatTheOneBelowDidAndHeaviesJoinAtBoreTier() {
        for (Slot slot : Slot.values()) {
            assertTrue(BoreTier.BORE.hold(slot) >= BoreTier.FISSURE.hold(slot), slot + " at bore");
            assertTrue(BoreTier.MAW.hold(slot) >= BoreTier.BORE.hold(slot), slot + " at maw");
        }
        assertEquals(0, BoreTier.FISSURE.hold(Slot.BRUTE), "a fissure holds Runners only");
        assertTrue(BoreTier.BORE.hold(Slot.BRUTE) > 0, "a bore holds Brutes");
    }

    @Test
    void aFullStrainIsOutOfTheDrawAndTheRollFallsOnTheOthers() {
        for (long roll = 0; roll < 200; roll++) {
            assertEquals(Slot.RUNNER, BoreStrainTable.pick(BoreTier.MAW, roll, false,
                    EnumSet.of(Slot.BRUTE, Slot.SPITTER, Slot.BOMBARD)));
        }
        assertNull(BoreStrainTable.pick(BoreTier.FISSURE, 7L, false, EnumSet.of(Slot.RUNNER)),
                "nothing to draw when every strain is out");
    }

    @Test
    void aBoreForgetsItsDeadAndItsHoldFreesUp() {
        SwarmBore bore = boreAt(BoreTier.BORE);
        int hold = BoreTier.BORE.hold(Slot.BRUTE);
        for (long body = 100; body < 100 + hold; body++) bore.adopt(Slot.BRUTE, body);
        assertEquals(0, bore.freeHold(Slot.BRUTE), "its Brutes fill its hold");
        bore.forgetDead(body -> body != 100L);
        assertEquals(hold - 1, bore.held(Slot.BRUTE));
        assertEquals(1, bore.freeHold(Slot.BRUTE), "one dead frees one place");
    }

    @Test
    void aHeldSurgeSendsNoMoreOfAStrainThanItsFreeHold() {
        SwarmBore bore = boreAt(BoreTier.MAW);
        for (long body = 100; body < 100 + BoreTier.MAW.hold(Slot.BRUTE); body++) bore.adopt(Slot.BRUTE, body);
        SwarmReinforcementSystem director = new SwarmReinforcementSystem(new CivilianEvacuationTracker());
        List<SwarmReinforcementSystem.Planned> pulse = director.plan(bore, 0, 1000f, true, true);
        Map<Slot, Integer> counts = new EnumMap<>(Slot.class);
        for (SwarmReinforcementSystem.Planned body : pulse) counts.merge(body.slot(), 1, Integer::sum);
        assertEquals(0, counts.getOrDefault(Slot.BRUTE, 0), "a full strain sends none");
        for (Slot slot : Slot.values()) {
            assertTrue(counts.getOrDefault(slot, 0) <= BoreTier.MAW.hold(slot), slot + " within its hold");
        }
        assertEquals(bore.freeHold(), pulse.size(), "a budget past the hold ends at the hold");
    }
}
