package com.dillon.starsectormarines.battle.warship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarshipHitFxTest {

    @Test
    void damageTypesReadAsTheirCharacter() {
        assertEquals(WarshipHitFx.Character.KINETIC, WarshipHitFx.Character.of("KINETIC"));
        assertEquals(WarshipHitFx.Character.HIGH_EXPLOSIVE, WarshipHitFx.Character.of("HIGH_EXPLOSIVE"));
        assertEquals(WarshipHitFx.Character.ENERGY, WarshipHitFx.Character.of("ENERGY"));
        assertEquals(WarshipHitFx.Character.FRAGMENTATION, WarshipHitFx.Character.of("FRAGMENTATION"));
        assertEquals(WarshipHitFx.Character.OTHER, WarshipHitFx.Character.of(null));
        assertEquals(WarshipHitFx.Character.OTHER, WarshipHitFx.Character.of("OTHER"));
    }

    @Test
    void aRoundWithNoWeaponIsSizedByItsDamageWithinBounds() {
        float small = WarshipHitFx.sizeCells(null, 0f);
        float mid = WarshipHitFx.sizeCells(null, 200f);
        float huge = WarshipHitFx.sizeCells(null, 1e7f);
        assertTrue(small >= WarshipHitFx.MIN_SIZE_CELLS);
        assertTrue(mid > small);
        assertEquals(WarshipHitFx.MAX_SIZE_CELLS, huge, 1e-6f);
    }

    @Test
    void aBreachShowsLongerThanAPlainHitAndABeamPulseShorter() {
        float hit = WarshipHitFx.lifeOf(WarshipHitFx.Kind.HULL, WarshipHitFx.Character.KINETIC, false);
        float breach = WarshipHitFx.lifeOf(WarshipHitFx.Kind.BREACH, WarshipHitFx.Character.KINETIC, false);
        float pulse = WarshipHitFx.lifeOf(WarshipHitFx.Kind.HULL, WarshipHitFx.Character.ENERGY, true);
        assertTrue(breach > hit);
        assertTrue(pulse < hit);
    }

    @Test
    void jitterIsDeterministicAndInRange() {
        for (int i = -50; i < 50; i++) {
            float u = WarshipHitFx.unit(i * 7919);
            assertTrue(u >= 0f && u <= 1f);
            assertEquals(u, WarshipHitFx.unit(i * 7919));
        }
    }
}
