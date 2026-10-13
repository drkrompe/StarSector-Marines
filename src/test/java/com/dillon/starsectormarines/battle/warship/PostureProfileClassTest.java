package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.warship.ShipClass.HullSize;
import com.dillon.starsectormarines.battle.warship.ShipClass.Role;
import com.dillon.starsectormarines.battle.warship.ShipClass.Shield;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostureProfileClassTest {

    private static final ShipClass CARRIER = new ShipClass(HullSize.CAPITAL, Role.CARRIER, 4, Shield.FRONT);
    private static final ShipClass SUPPORT = new ShipClass(HullSize.CRUISER, Role.SUPPORT, 0, Shield.NONE);

    @Test
    void combatAndSupportHullsKeepThePosturesOwnProfile() {
        for (WarshipPosture posture : WarshipPosture.values()) {
            assertSame(PostureProfile.of(posture), PostureProfile.of(posture, ShipClass.DEFAULT));
            assertSame(PostureProfile.of(posture), PostureProfile.of(posture, SUPPORT));
        }
    }

    @Test
    void aCarrierConservesMoreFluxInAFightThanACombatHull() {
        PostureProfile air = PostureProfile.of(WarshipPosture.AIR_SUPERIORITY, CARRIER);
        assertEquals(0.40f, air.softLimit());
        assertEquals(0.25f, air.resumeLevel());
        assertTrue(air.softLimit() < PostureProfile.AIR_SUPERIORITY.softLimit());
        for (WarshipPosture posture : new WarshipPosture[]{WarshipPosture.SUPPORT, WarshipPosture.INTERCEPT}) {
            PostureProfile p = PostureProfile.of(posture, CARRIER);
            assertEquals(0.60f, p.softLimit());
            assertEquals(0.40f, p.resumeLevel());
        }
    }

    @Test
    void aCarrierWithNoFightToSaveForKeepsTheShippedFluxPair() {
        assertEquals(WarshipFlux.SOFT_LIMIT, PostureProfile.of(WarshipPosture.BOMBARD, CARRIER).softLimit());
        assertEquals(WarshipFlux.RESUME_LEVEL, PostureProfile.of(WarshipPosture.WITHDRAW, CARRIER).resumeLevel());
    }

    @Test
    void aCarrierHangsBackInEveryPostureButWithdrawAndChangesNothingElse() {
        for (WarshipPosture posture : WarshipPosture.values()) {
            PostureProfile base = PostureProfile.of(posture);
            PostureProfile p = PostureProfile.of(posture, CARRIER);
            assertTrue(p.resumeLevel() < p.softLimit() && p.softLimit() <= 1f, posture + " flux pair");
            assertEquals(base.maxAreas(), p.maxAreas());
            assertEquals(base.radiusScale(), p.radiusScale());
            assertEquals(base.opportunityShare(), p.opportunityShare());
            assertEquals(base.maxBurstShare(), p.maxBurstShare());
            assertEquals(base.weighted(), p.weighted());
            assertEquals(base.denyLandings(), p.denyLandings());
            if (posture == WarshipPosture.WITHDRAW) continue;
            assertTrue(p.standoff(), posture + " standoff");
            assertEquals(PostureProfile.CARRIER_STANDOFF_SCALE, p.standoffScale());
        }
    }

    @Test
    void standoffClearanceGrowsWithTheMarginScaleAndKeepsHalfTheHull() {
        assertEquals(5f + 10f, WarshipSystem.standoffClearance(1f, 20f));
        assertEquals(15f + 10f, WarshipSystem.standoffClearance(PostureProfile.CARRIER_STANDOFF_SCALE, 20f));
    }
}
