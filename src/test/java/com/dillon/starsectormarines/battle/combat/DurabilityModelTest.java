package com.dillon.starsectormarines.battle.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurabilityModelTest {

    private static final float EPSILON = 1e-5f;

    private final DurabilityModel.Resolution result = new DurabilityModel.Resolution();

    @Test
    void unarmoredTargetTakesClampedStructureDamage() {
        DurabilityModel.resolveInto(12f, 0f, 0f, 0f, 10f, result);

        assertEquals(0f, result.armorDamage(), EPSILON);
        assertEquals(10f, result.structureDamage(), EPSILON);
        assertFalse(result.armorBroken());
    }

    @Test
    void zeroPenetrationStillChipsArmorAtFloorEfficiency() {
        DurabilityModel.resolveInto(20f, 0f, 5f, 10f, 25f, result);

        assertEquals(2f, result.armorDamage(), EPSILON);
        assertEquals(0f, result.structureDamage(), EPSILON);
        assertFalse(result.armorBroken());
    }

    @Test
    void matchingAndOvermatchingPenetrationRemoveArmorOneForOne() {
        DurabilityModel.resolveInto(3f, 10f, 5f, 10f, 25f, result);
        assertEquals(3f, result.armorDamage(), EPSILON);
        assertFalse(result.armorBroken());

        DurabilityModel.resolveInto(3f, 50f, 5f, 10f, 25f, result);
        assertEquals(3f, result.armorDamage(), EPSILON);
        assertFalse(result.armorBroken());
    }

    @Test
    void breakingHitSpendsResistedDamageBeforeOverflowingToStructure() {
        DurabilityModel.resolveInto(12f, 5f, 5f, 10f, 25f, result);

        assertEquals(5f, result.armorDamage(), EPSILON);
        assertEquals(12f - 5f / 0.55f, result.structureDamage(), EPSILON);
        assertTrue(result.armorBroken());
    }

    @Test
    void exactBreakEmitsBreakWithoutStructureDamage() {
        DurabilityModel.resolveInto(10f, 10f, 10f, 10f, 25f, result);

        assertEquals(10f, result.armorDamage(), EPSILON);
        assertEquals(0f, result.structureDamage(), EPSILON);
        assertTrue(result.armorBroken());
    }

    @Test
    void reusedOutputDoesNotLeakPreviousResolution() {
        DurabilityModel.resolveInto(20f, 10f, 5f, 10f, 25f, result);
        assertTrue(result.armorBroken());

        DurabilityModel.resolveInto(0f, 0f, 0f, 0f, 25f, result);
        assertEquals(0f, result.armorDamage(), EPSILON);
        assertEquals(0f, result.structureDamage(), EPSILON);
        assertFalse(result.armorBroken());
    }

    @Test
    void positiveArmorRequiresPositiveRating() {
        assertThrows(IllegalArgumentException.class,
                () -> DurabilityModel.resolveInto(1f, 1f, 1f, 0f, 1f, result));
    }
}

