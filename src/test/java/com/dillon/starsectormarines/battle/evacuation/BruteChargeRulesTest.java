package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.evacuation.BruteChargeRules.Kind;
import com.dillon.starsectormarines.battle.evacuation.BruteChargeRules.Outcome;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BruteChargeRulesTest {

    @Test
    void chargesOnlyWithTargetInBandOpenLineAndCooldownSpent() {
        float mid = (BruteChargeRules.MIN_BAND + BruteChargeRules.MAX_BAND) / 2f;
        assertTrue(BruteChargeRules.shouldCharge(mid, true, 100, 100));
        assertFalse(BruteChargeRules.shouldCharge(BruteChargeRules.MIN_BAND - .1f, true, 100, 0), "too close: strike");
        assertFalse(BruteChargeRules.shouldCharge(BruteChargeRules.MAX_BAND + .1f, true, 100, 0), "too far: walk");
        assertFalse(BruteChargeRules.shouldCharge(mid, false, 100, 0), "line closed");
        assertFalse(BruteChargeRules.shouldCharge(mid, true, 99, 100), "cooldown running");
    }

    @Test
    void aBodyIsThrownToItsOwnSideOfTheLineAndOnward() {
        // Running east; a body north of the line (+y) goes north, one south goes south.
        float[] north = BruteChargeRules.throwDirection(1f, 0f, .5f, .2f);
        float[] south = BruteChargeRules.throwDirection(1f, 0f, .5f, -.2f);
        assertTrue(north[1] > 0f && north[0] > 0f);
        assertTrue(south[1] < 0f && south[0] > 0f);
        assertEquals(1f, (float) Math.hypot(north[0], north[1]), 1e-5f);
        // Dead on the line: always the heading's left, never a coin toss.
        float[] ahead = BruteChargeRules.throwDirection(1f, 0f, .5f, 0f);
        assertTrue(ahead[1] > 0f);
    }

    @Test
    void aRunningBruteTurnsNoFasterThanItsLimit() {
        assertEquals(10f, BruteChargeRules.turnToward(0f, 90f, 10f), 1e-5f);
        assertEquals(-10f, BruteChargeRules.turnToward(0f, -90f, 10f), 1e-5f);
        assertEquals(185f, BruteChargeRules.turnToward(175f, -170f, 10f), 1e-5f, "turns the short way across 180");
        assertEquals(5f, BruteChargeRules.turnToward(0f, 5f, 10f), 1e-5f);
    }

    @Test
    void outcomeByWhatIsHit() {
        Outcome infantry = BruteChargeRules.outcome(Kind.INFANTRY);
        assertTrue(infantry.goesOn() && infantry.throwCells() > 0f && infantry.holdSeconds() > 0f);
        for (Kind kind : Kind.values()) {
            if (kind != Kind.INFANTRY) assertFalse(BruteChargeRules.outcome(kind).goesOn(), kind + " stops it");
        }
        assertTrue(BruteChargeRules.outcome(Kind.HEAVY_VEHICLE).bruteStunSeconds() > 0f, "a tank stuns the Brute");
        assertTrue(BruteChargeRules.outcome(Kind.MEDIUM_MECH).legs());
        assertTrue(BruteChargeRules.outcome(Kind.MEDIUM_MECH).holdSeconds() > 0f);
        assertEquals(0f, BruteChargeRules.outcome(Kind.HEAVY_MECH).holdSeconds(), "a heavy mech keeps its feet");

        assertEquals(Kind.HEAVY_VEHICLE, BruteChargeRules.vehicleKind(VehicleType.BREAKTHROUGH_TANK.weight()));
        assertEquals(Kind.MEDIUM_VEHICLE, BruteChargeRules.vehicleKind(VehicleType.DOZER_BREACHER.weight()));
        assertEquals(Kind.LIGHT_VEHICLE, BruteChargeRules.vehicleKind(VehicleType.MILITIA_TECHNICAL.weight()));
        assertEquals(Kind.LIGHT_MECH, BruteChargeRules.mechKind(.8f));
        assertEquals(Kind.MEDIUM_MECH, BruteChargeRules.mechKind(1.2f));
        assertEquals(Kind.HEAVY_MECH, BruteChargeRules.mechKind(1.8f));
    }

    @Test
    void aBrutesStageShiftsTheWeightItMeets() {
        assertEquals(Kind.HEAVY_VEHICLE, BruteChargeRules.felt(Kind.MEDIUM_VEHICLE, SwarmStage.WHELP),
                "a whelp meets an APC as an adult meets a tank");
        assertTrue(BruteChargeRules.outcome(Kind.MEDIUM_VEHICLE, SwarmStage.WHELP).bruteStunSeconds() > 0f);
        assertEquals(Kind.MEDIUM_MECH, BruteChargeRules.felt(Kind.HEAVY_MECH, SwarmStage.ELDER),
                "an elder staggers a heavy mech");
        assertEquals(Kind.LIGHT_MECH, BruteChargeRules.felt(Kind.LIGHT_MECH, SwarmStage.ELDER), "no lighter class");
        for (Kind kind : Kind.values()) {
            assertEquals(kind, BruteChargeRules.felt(kind, SwarmStage.ADULT));
            assertEquals(BruteChargeRules.outcome(kind), BruteChargeRules.outcome(kind, SwarmStage.ADULT));
        }
        assertEquals(Kind.INFANTRY, BruteChargeRules.felt(Kind.INFANTRY, SwarmStage.WHELP));
    }

    @Test
    void anOlderChargeHitsHarderAndGoesThroughMore() {
        assertTrue(BruteChargeRules.maxBodies(SwarmStage.WHELP) < BruteChargeRules.maxBodies(SwarmStage.ADULT));
        assertTrue(BruteChargeRules.maxBodies(SwarmStage.ELDER) > BruteChargeRules.maxBodies(SwarmStage.ADULT));
        assertEquals(BruteChargeRules.WALL_BLOW, BruteChargeRules.wallBlow(SwarmStage.ADULT));
        assertTrue(BruteChargeRules.wallBlow(SwarmStage.ELDER) > BruteChargeRules.WALL_BLOW);
        assertTrue(BruteChargeRules.outcome(Kind.INFANTRY, SwarmStage.ELDER).throwCells()
                > BruteChargeRules.outcome(Kind.INFANTRY).throwCells());
        assertTrue(BruteChargeRules.pointPush(4f, 5.4f, 10f) > BruteChargeRules.pointPush(4f, 10f),
                "a heavier Brute moves a mech further");
    }
}
