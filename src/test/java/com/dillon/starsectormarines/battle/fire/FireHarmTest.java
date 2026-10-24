package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.nav.RouteCostField;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.ArmorClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireHarmTest {

    private static final int W = 12, H = 12;

    private static FireField burningAt(int x, int y) {
        int[] fuel = new int[W * H];
        fuel[y * W + x] = FireFuel.CROWN.bit;
        FireField f = new FireField(W, H, 1L, idx -> fuel[idx]);
        f.addHeat(x, y, 5000);
        for (int t = 0; t < (FireField.RAMP_STEPS + 2) * FireField.STEP_TICKS; t++) f.tick(t);
        return f;
    }

    @Test
    void inTheFireIsWorseThanBesideItAndFarIsNothing() {
        FireField f = burningAt(5, 5);
        float in = FireHarm.exposure(f, 5, 5), beside = FireHarm.exposure(f, 6, 6), far = FireHarm.exposure(f, 8, 5);
        assertTrue(in > 0.9f, "a crown at peak is near full exposure: " + in);
        assertEquals(in * FireHarm.BESIDE_SHARE, beside, 1e-4f);
        assertEquals(0f, far);
    }

    @Test
    void burningGroundCostsMoreAndItsEdgeSomewhat() {
        FireField f = burningAt(5, 5);
        float[] cost = FireRouteCost.hazard(f);
        assertTrue(cost[5 * W + 5] > FireRouteCost.BESIDE, "the burning cell is the dearest");
        assertEquals(FireRouteCost.BESIDE, cost[6 * W + 6]);
        assertEquals(1f, cost[0]);
    }

    @Test
    void withNothingBurningASidesOwnCostingPassesThrough() {
        FireField f = new FireField(W, H, 1L, FireTerrain.NONE);
        FireRouteCost routes = new FireRouteCost();
        RouteCostField own = new RouteCostField(new float[W * H], RouteCostField.nextRevision());
        routes.tick(0, f, faction -> own);
        assertSame(own, routes.forSide(Faction.MARINE, own));
    }

    @Test
    void aRefugeBesideFireIsRefused() {
        FireField f = burningAt(5, 5);
        assertTrue(FireEffects.refusedAsRefuge(f, 6, 5));
        assertFalse(FireEffects.refusedAsRefuge(f, 8, 5));
        assertFalse(FireEffects.refusedAsRefuge(null, 5, 5));
    }

    @Test
    void fireBurnsTheSoftThroughTheirArmourAndTheSealedAndHullsOnIt() {
        assertTrue(FireHarm.throughArmor(UnitType.MARINE_BLUE, ArmorClass.STANDARD), "a marine in ordinary armour");
        assertTrue(FireHarm.throughArmor(UnitType.SWARM_BRUTE, ArmorClass.STANDARD), "the swarm");
        assertFalse(FireHarm.throughArmor(UnitType.MARINE_BLUE, ArmorClass.POWER_SUIT), "a sealed suit");
        assertFalse(FireHarm.throughArmor(UnitType.GROUND_VEHICLE, ArmorClass.STANDARD), "a hull");
        assertFalse(FireHarm.throughArmor(UnitType.MACHINE_FRAME, ArmorClass.STANDARD), "a frame");
    }

    @Test
    void theSwarmBurnsHotterAndAHullCooler() {
        assertEquals(1f, FireHarm.bodyShare(UnitType.MARINE_BLUE));
        assertEquals(FireHarm.SWARM_SHARE, FireHarm.bodyShare(UnitType.SWARM_MATRIARCH));
        assertEquals(FireHarm.HULL_SHARE, FireHarm.bodyShare(UnitType.GROUND_VEHICLE));
    }
}
