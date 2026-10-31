package com.dillon.starsectormarines.battle.command.allied;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenedAdvancePaceTest {

    private static final int LATER = ScreenedAdvance.STRAGGLE_TICKS;

    @Test
    void aMemberStuckOffItsSlotFallsOutOfTheBodyUntilItGainsAgain() {
        // The hull of Broad Front's lane 2 (2026-10-24): fifteen cells short of its slot and going nowhere.
        ScreenedAdvance.Pace hull = new ScreenedAdvance.Pace(true, 0);
        hull.observe(0, 198f, 131f, 20f, 15f, false);
        hull.observe(LATER - 1, 198.4f, 131.2f, 20.3f, 14.8f, false);
        assertTrue(hull.keepsUp(LATER - 1), "still within the grace it is given");
        hull.observe(LATER, 198.4f, 131.2f, 20.3f, 14.8f, false);
        assertFalse(hull.keepsUp(LATER), "it stood off its slot without gaining a cell");
        hull.observe(LATER + 1, 200f, 132f, 21.5f, 13.5f, false);
        assertTrue(hull.keepsUp(LATER + 1), "gaining again, it is waited for again");
    }

    @Test
    void aMemberRockingWhereItStandsIsNotGaining() {
        // Broad Front frozen 5's lane 1 hull (2026-10-26): forty cells behind its slot, rocking a cell or two.
        ScreenedAdvance.Pace hull = new ScreenedAdvance.Pace(true, 0);
        float[][] rocking = {{55f, 193f, -21.8f, 42f}, {55f, 195f, -21.0f, 41.5f}, {57f, 198f, -18.4f, 41.2f},
                {54f, 195f, -21.6f, 42.3f}, {56f, 197f, -19.2f, 41.8f}};
        for (int i = 0; i < rocking.length; i++) {
            float[] at = rocking[i];
            hull.observe(i * LATER / 2, at[0], at[1], at[2], at[3], false);
        }
        assertFalse(hull.keepsUp(2 * LATER), "moving, but no further along and no nearer its slot");
        ScreenedAdvance.Pace sidestep = new ScreenedAdvance.Pace(true, 0);
        sidestep.observe(0, 100f, 100f, 50f, 8f, false);
        sidestep.observe(LATER - 1, 100f, 106f, 50f, 2.5f, false);
        assertTrue(sidestep.keepsUp(LATER + 1), "walking across the route up to its slot, it gains");
    }

    @Test
    void theSupportTrailsTheMainAlongTheRouteAndBackFromAThreat() {
        AdvanceRoute route = AdvanceRoute.straight(0, 0, 0, 100);
        float[] advancing = ScreenedAdvance.supportPoint(route, 50f, null, 12f);
        float[] main = route.pointAt(50f);
        assertTrue(Math.abs(route.along(advancing[0], advancing[1]) - (route.along(main[0], main[1]) - 12f)) < 1e-3f,
                "twelve cells behind the main along the route");
        float[] atStart = ScreenedAdvance.supportPoint(route, 5f, null, 12f);
        assertTrue(route.along(atStart[0], atStart[1]) >= -1e-3f, "never behind the route's start");
        float[] engaged = ScreenedAdvance.supportPoint(route, 50f, new float[]{1f, 0f}, 12f);
        assertTrue(Math.abs(engaged[0] - (main[0] - 12f)) < 1e-3f && Math.abs(engaged[1] - main[1]) < 1e-3f,
                "straight back from the main, away from a threat on its flank");
    }

    @Test
    void aPushedAdvanceGoesOnThroughItsContactUntilThePushRunsOut() {
        // Pushed on tick 1000 with a 1200-tick hold: the swarm still in range does not hold it again at once.
        assertFalse(ScreenedAdvance.engages(true, 1030, 2200));
        assertTrue(ScreenedAdvance.engages(true, 2200, 2200), "the push spent, a contact holds it again");
        assertFalse(ScreenedAdvance.engages(false, 2200, 2200));
        assertTrue(ScreenedAdvance.engages(true, 30, 0), "never pushed, any contact holds it");
    }

    @Test
    void aMemberHaltedInPlaceShortOfItsSlotCountsAtTheSlotForTheKeepUp() {
        // Broad Front frozen 5's lane 1 hull: in place, 64.6 along the route, its slot at 70.6.
        ScreenedAdvance.Pace hull = new ScreenedAdvance.Pace(true, 0);
        hull.slotAt(70.6f);
        hull.observe(0, 149f, 208f, 64.6f, 1f, true);
        assertEquals(64.6f, hull.keepUpAlong(64.6f, 0), 1e-4f, "just arrived, it is waited for where it stands");
        hull.observe(LATER, 149.3f, 208.2f, 64.6f, 1f, true);
        assertEquals(70.6f, hull.keepUpAlong(64.6f, LATER), 1e-4f, "halted, it no longer holds the anchor");
        hull.observe(LATER + 15, 149.3f, 208.2f, 64.6f, 6f, false);
        assertEquals(64.6f, hull.keepUpAlong(64.6f, LATER + 15), 1e-4f, "off its slot, it counts where it stands");
    }

    @Test
    void aMemberOnItsSlotKeepsUpStandingStill() {
        ScreenedAdvance.Pace mech = new ScreenedAdvance.Pace(true, 0);
        for (int tick = 0; tick <= 3 * LATER; tick += 15) mech.observe(tick, 203f, 143f, 80f, .5f, true);
        assertTrue(mech.keepsUp(3 * LATER));
    }

    @Test
    void aLateJoinerIsNotTheBodyUntilItHasWalkedUpToItsSlot() {
        ScreenedAdvance.Pace walker = new ScreenedAdvance.Pace(false, 0);
        walker.observe(0, 213f, 54f, 0f, 100f, false);
        walker.observe(15, 213f, 56f, 2f, 98f, false);
        assertFalse(walker.keepsUp(15), "walking up from far behind, it does not hold the formation");
        walker.observe(30, 196f, 154f, 100f, 1f, true);
        assertTrue(walker.keepsUp(30));
    }

    @Test
    void aFormationGivenAWaitRalliesAtItsEndWithAMemberOffItsSlot() {
        int wait = Math.round(15f / BattleSimulation.TICK_DT);
        assertFalse(ScreenedAdvance.rallies(false, 15f, -1, 1000), "not at its end yet");
        assertTrue(ScreenedAdvance.rallies(true, 0f, 100, 100), "in place");
        assertFalse(ScreenedAdvance.rallies(false, 0f, 100, 100 + 10 * wait), "with no wait it waits for its slots");
        assertFalse(ScreenedAdvance.rallies(false, 15f, 100, 99 + wait));
        assertTrue(ScreenedAdvance.rallies(false, 15f, 100, 100 + wait));
    }
}
