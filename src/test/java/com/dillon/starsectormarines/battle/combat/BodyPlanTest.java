package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyPlanTest {

    private static final float[] NO_COVER = {0f, 0f, 0f, 0f};

    private static BodyPart part(String name, int index, float share) {
        return new BodyPart(name, index, share, 1f, BodyPart.Consequence.NONE, NO_COVER, NO_COVER, NO_COVER);
    }

    @Test
    void infantryKeepsItsSharesMultipliersAndConsequences() {
        BodyPlan plan = BodyPlan.INFANTRY;
        assertEquals(4, plan.size());
        assertSame(BodyPart.HEAD, plan.parts().get(0));
        assertSame(BodyPart.ARMS, plan.parts().get(1));
        assertSame(BodyPart.TORSO, plan.parts().get(2));
        assertSame(BodyPart.LEGS, plan.parts().get(3));
        assertEquals(0.10f, BodyPart.HEAD.share);
        assertEquals(1.60f, BodyPart.HEAD.damageMultiplier);
        assertEquals(0.20f, BodyPart.ARMS.share);
        assertEquals(0.75f, BodyPart.ARMS.damageMultiplier);
        assertEquals(0.40f, BodyPart.TORSO.share);
        assertEquals(1.10f, BodyPart.TORSO.damageMultiplier);
        assertEquals(0.30f, BodyPart.LEGS.share);
        assertEquals(0.85f, BodyPart.LEGS.damageMultiplier);
        assertTrue(BodyPart.LEGS.wounds());
        assertTrue(BodyPart.ARMS.wounds());
        assertFalse(BodyPart.HEAD.wounds());
        assertFalse(BodyPart.TORSO.wounds());
    }

    @Test
    void infantryBandsMatchTheStaticLookupAtEveryHeight() {
        int n = 10_000;
        for (int i = 0; i <= n; i++) {
            float h = i / (float) n;
            assertSame(BodyPart.atHeight(h), BodyPlan.INFANTRY.atHeight(h));
        }
    }

    @Test
    void bandsStackInBandOrderNotPartOrder() {
        BodyPart top = part("TOP", 0, 0.25f);
        BodyPart bottom = part("BOTTOM", 1, 0.75f);
        BodyPlan plan = new BodyPlan("test", new BodyPart[] {top, bottom}, new BodyPart[] {bottom, top});
        assertSame(bottom, plan.atHeight(0f));
        assertSame(bottom, plan.atHeight(0.74f));
        assertSame(top, plan.atHeight(0.76f));
        assertSame(top, plan.atHeight(1f));
    }

    @Test
    void aHeightPastEveryBandIsTheTopPart() {
        assertSame(BodyPart.HEAD, BodyPlan.INFANTRY.atHeight(1.5f));
        assertSame(BodyPart.HEAD, BodyPlan.INFANTRY.atHeight(Float.NaN));
        assertSame(BodyPart.LEGS, BodyPlan.INFANTRY.atHeight(-0.5f));
    }

    @Test
    void ownsOnlyItsOwnParts() {
        assertTrue(BodyPlan.INFANTRY.owns(BodyPart.TORSO));
        assertFalse(BodyPlan.INFANTRY.owns(part("TORSO", 2, 0.4f)));
        assertFalse(BodyPlan.INFANTRY.owns(part("FAR", 9, 0.4f)));
    }

    @Test
    void aPlanRefusesPartsOutOfIndexOrMissingABand() {
        BodyPart a = part("A", 0, 0.5f);
        BodyPart b = part("B", 1, 0.5f);
        assertThrows(IllegalArgumentException.class,
                () -> new BodyPlan("bad", new BodyPart[] {b, a}, new BodyPart[] {a, b}));
        assertThrows(IllegalArgumentException.class,
                () -> new BodyPlan("bad", new BodyPart[] {a, b}, new BodyPart[] {a, a}));
    }

    @Test
    void peopleHaveTheInfantryPlanAndMachinesNone() {
        assertSame(BodyPlan.INFANTRY, UnitType.MARINE.bodyPlan());
        assertSame(BodyPlan.INFANTRY, UnitType.CIVILIAN.bodyPlan());
        assertNull(UnitType.HEAVY_MECH.bodyPlan());
        assertNull(UnitType.GROUND_VEHICLE.bodyPlan());
        assertFalse(UnitType.HEAVY_MECH.hasBodyParts());
    }
}
