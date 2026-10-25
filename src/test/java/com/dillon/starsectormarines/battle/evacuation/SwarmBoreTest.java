package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A bore's growth clock, its biomass seam and its surge clock, asked of the bore itself. */
class SwarmBoreTest {

    private static final float DT = BattleSimulation.TICK_DT;

    private static SwarmBore fissure() {
        return new SwarmBore(1, false, 7L, 10, 10, 0, 1f);
    }

    /** Ticks of the base clock at {@code integrity} until the bore reaches {@code tier}. */
    private static int ticksTo(SwarmBore bore, BoreTier tier, float integrity) {
        int tick = 0;
        while (bore.tier().ordinal() < tier.ordinal()) bore.grow(DT, integrity, ++tick);
        return tick;
    }

    @Test
    void anUnfedBoreGrowsSlowlyThroughEveryTier() {
        SwarmBore bore = fissure();
        int toBore = ticksTo(bore, BoreTier.BORE, 1f);
        float expected = BoreTier.BORE.growthThreshold / SwarmBore.BASE_GROWTH_PER_SECOND / DT;
        assertEquals(expected, toBore, 2f);
        assertEquals(toBore, bore.tierTick(BoreTier.BORE));
        ticksTo(bore, BoreTier.MAW, 1f);
        assertEquals(BoreTier.MAW, bore.tier());
        assertNull(bore.grow(DT, 1f, 1), "maw is the last tier");
    }

    @Test
    void damageSlowsGrowthDownToItsFloor() {
        int whole = ticksTo(fissure(), BoreTier.BORE, 1f);
        int halved = ticksTo(fissure(), BoreTier.BORE, 0.5f);
        int wrecked = ticksTo(fissure(), BoreTier.BORE, 0.05f);
        assertEquals(2f * whole, halved, 2f);
        assertEquals(whole / SwarmBore.MIN_DAMAGED_GROWTH, wrecked, 4f);
    }

    @Test
    void biomassIsTheFastPathToGrowth() {
        SwarmBore bore = fissure();
        assertEquals(BoreTier.BORE, bore.feed(4f, 5), "four bodies take a fissure to bore at once");
        assertEquals(5, bore.tierTick(BoreTier.BORE));
        assertEquals(BoreTier.MAW, bore.feed(5f, 6));
        assertEquals(9f, bore.biomassFed());
        assertEquals(2, bore.feeds());
        assertNull(bore.feed(0f, 7), "nothing is not a feed");
        assertEquals(2, bore.feeds());
    }

    @Test
    void aFedBoreAnswersWithABiggerSurgeSooner() {
        SwarmBore bore = fissure();
        int unfedSurge = bore.nextSurgeTick();
        bore.surged(unfedSurge, 0f);
        int scheduled = bore.nextSurgeTick();
        assertEquals(BoreTier.FISSURE.surgeSize, bore.surgeSize());
        bore.feed(2f, unfedSurge + 1);
        assertEquals(BoreTier.FISSURE.surgeSize + Math.round(2f * SwarmBore.BODIES_PER_BIOMASS), bore.surgeSize());
        assertTrue(bore.nextSurgeTick() < scheduled, "a feed brings the next surge forward");
        bore.surged(bore.nextSurgeTick(), 0f);
        assertEquals(0f, bore.freshBiomass(), "a surge spends the fresh biomass");
        assertEquals(BoreTier.FISSURE.surgeSize, bore.surgeSize());
    }

    @Test
    void surgesAreTimedPulsesWithQuietBetween() {
        SwarmBore bore = fissure();
        int first = bore.nextSurgeTick();
        assertFalse(bore.surgeDue(first - 1));
        assertTrue(bore.surgeDue(first));
        bore.surged(first, 0f);
        int gap = bore.nextSurgeTick() - first;
        assertEquals(BoreTier.FISSURE.surgeIntervalSeconds / DT, gap, 1f);
        bore.surged(bore.nextSurgeTick(), 1f);
        assertEquals(BoreTier.FISSURE.surgeIntervalSeconds * (1f + SwarmBore.SURGE_JITTER) / DT,
                bore.nextSurgeTick() - first - gap, 1f);
    }

    @Test
    void aCollapsedBoreTakesNoBiomassAndNeverSurges() {
        SwarmBore bore = fissure();
        bore.collapse(40);
        assertNull(bore.feed(10f, 41));
        assertNull(bore.grow(DT, 1f, 41));
        assertFalse(bore.surgeDue(Integer.MAX_VALUE));
        assertEquals(BoreTier.FISSURE, bore.tier());
    }

    @Test
    void theMotherOpensAtBoreTier() {
        SwarmBore mother = new SwarmBore(0, true, 9L, 3, 3, 100, 1f);
        assertEquals(BoreTier.BORE, mother.tier());
        assertEquals(100, mother.tierTick(BoreTier.BORE));
        assertEquals("mother", mother.beatKey());
    }

    @Test
    void theTargetIsTheLivingBoresCapacities() {
        SwarmBore a = fissure();
        SwarmBore b = new SwarmBore(2, false, 8L, 20, 20, 0, 1f);
        b.feed(4f, 1);
        assertEquals(BoreTier.FISSURE.capacity + BoreTier.BORE.capacity,
                SwarmReinforcementSystem.borePopulationTarget(List.of(a, b)));
        b.collapse(2);
        a.collapse(3);
        assertEquals(0, SwarmReinforcementSystem.borePopulationTarget(List.of(a, b)),
                "collapsing every bore leaves nothing to spawn toward");
    }

    @Test
    void tierFollowsTheThresholds() {
        assertEquals(BoreTier.FISSURE, BoreTier.forGrowth(BoreTier.BORE.growthThreshold - 0.01f));
        assertEquals(BoreTier.BORE, BoreTier.forGrowth(BoreTier.BORE.growthThreshold));
        assertEquals(BoreTier.MAW, BoreTier.forGrowth(1e6f));
        assertTrue(BoreTier.MAW.sporeColonies > BoreTier.BORE.sporeColonies
                && BoreTier.BORE.sporeColonies > BoreTier.FISSURE.sporeColonies, "a bore sprouts more colonies as it grows");
    }

    @Test
    void aHeldBoreSendsNoSurgeUntilWoken() {
        SwarmBore bore = fissure();
        int due = bore.nextSurgeTick();
        bore.hold();
        assertFalse(bore.surgeDue(due + 1000), "a sleeping bore never surges on its own clock");
        bore.feed(10f, due + 1000);
        assertFalse(bore.surgeDue(due + 1000), "feeding does not wake it");
        bore.surgeAt(due + 1001);
        assertTrue(bore.surgeDue(due + 1001), "woken, it surges at once");
        assertFalse(bore.dormant());
    }

    @Test
    void aGrowingColonysRootComesOverItsLeadAndOnlyThenMayItRise() {
        SwarmBore.Growing g = new SwarmBore.Growing(BoreTier.BORE, 4, 5, 100, 150);
        assertEquals(0f, g.progress(100), 1e-6f);
        assertEquals(0.5f, g.progress(175), 1e-6f);
        assertFalse(g.arrived(249), "its root has not come");
        assertTrue(g.arrived(250));
        assertEquals(1f, g.progress(400), 1e-6f);
    }
}
