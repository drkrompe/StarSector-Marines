package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.vehicle.VehicleType.Weight;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Weight decides the shove, the crush, the ram and the mass; hover decides only the ground a hull crosses. */
class VehicleWeightTest {

    @Test void aLightSkirtShovesNothingCrushesNothingAndWeighsLeast() {
        for (VehicleType type : new VehicleType[]{VehicleType.HOVER_SCOUT, VehicleType.DRONE_SKIMMER}) {
            assertTrue(type.isHover(), type.name());
            assertEquals(Weight.LIGHT, type.weight(), type.name());
            assertFalse(type.repelsBodies(), type.name());
            assertFalse(type.crushesUndergrowth(), type.name());
            assertFalse(type.ramsBarricades(), type.name());
            assertFalse(type.crushesWalls(), type.name());
            assertEquals(1.5f, type.massTonnes(), type.name());
            assertEquals(.8f, type.gripAlong(), type.name());
            assertEquals(.8f, type.gripAcross(), type.name());
        }
    }

    @Test void aHeavySkirtShovesAndCrushesAsAnyHullOfItsWeight() {
        for (Weight weight : Weight.values()) {
            boolean light = weight == Weight.LIGHT;
            assertEquals(!light, weight.shoves(true), weight.name());
            assertTrue(weight.shoves(false), "every wheel and track shoves: " + weight);
            assertEquals(!light, weight.crushesUndergrowth(), weight.name());
            if (!light) assertEquals(weight.massTonnes(false), weight.massTonnes(true), weight.name());
        }
    }

    @Test void heavyAndUltraHeavyCrushWallsAndUltraHeavyIsTheHeaviest() {
        for (Weight weight : Weight.values()) {
            assertEquals(weight == Weight.HEAVY || weight == Weight.ULTRA_HEAVY, weight.crushesWalls(), weight.name());
        }
        float last = 0f;
        for (Weight weight : Weight.values()) {
            assertTrue(weight.massTonnes(false) > last, "heavier class, heavier hull: " + weight);
            last = weight.massTonnes(false);
        }
        assertTrue(Weight.ULTRA_HEAVY.atLeast(Weight.HEAVY));
        assertTrue(Weight.HEAVY.atLeast(Weight.HEAVY));
        assertFalse(Weight.MEDIUM.atLeast(Weight.HEAVY));
    }

    @Test void everyHullReadsItsRulesOffItsWeight() {
        for (VehicleType type : VehicleType.values()) {
            Weight weight = type.weight();
            assertEquals(weight.shoves(type.isHover()), type.repelsBodies(), type.name());
            assertEquals(weight.crushesUndergrowth(), type.crushesUndergrowth(), type.name());
            assertEquals(weight.crushesWalls() || type.hasBlade(), type.crushesWalls(), type.name());
            assertEquals(type.crushesUndergrowth() && !type.crushesWalls(), type.ramsBarricades(), type.name());
            assertEquals(weight.massTonnes(type.isHover()), type.massTonnes(), type.name());
        }
    }
}
