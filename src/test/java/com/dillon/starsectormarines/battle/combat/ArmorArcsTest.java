package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.combat.ArmorArcs.Arc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmorArcsTest {

    private static final float EPS = 1e-4f;

    @Test
    void facingNorthReadsTheFourQuarters() {
        // 0° faces +Y.
        assertEquals(Arc.FRONT, ArmorArcs.classify(0f, 0f, 10f));
        assertEquals(Arc.REAR, ArmorArcs.classify(0f, 0f, -10f));
        assertEquals(Arc.SIDE, ArmorArcs.classify(0f, 10f, 0f));
        assertEquals(Arc.SIDE, ArmorArcs.classify(0f, -10f, 0f));
    }

    @Test
    void fortyFiveDegreesOffTheNoseIsStillFrontAndOffTheTailStillRear() {
        assertEquals(Arc.FRONT, ArmorArcs.classify(0f, 10f, 10f));
        assertEquals(Arc.SIDE, ArmorArcs.classify(0f, 10.1f, 10f));
        assertEquals(Arc.REAR, ArmorArcs.classify(0f, -10f, -10f));
        assertEquals(Arc.SIDE, ArmorArcs.classify(0f, -10f, -9.9f));
    }

    @Test
    void theFrameTurnsWithTheFacing() {
        // 90° faces -X (counter-clockwise from +Y); its right is +Y, as MechSections.fromLeft reads it.
        assertEquals(Arc.FRONT, ArmorArcs.classify(90f, -10f, 1f));
        assertEquals(Arc.REAR, ArmorArcs.classify(90f, 10f, -1f));
        assertEquals(Arc.SIDE, ArmorArcs.classify(90f, 0f, 10f));
        assertEquals(Arc.FRONT, ArmorArcs.classify(-180f, 0f, -10f));
        assertEquals(Arc.FRONT, ArmorArcs.classify(450f, -10f, 0f));
    }

    @Test
    void noBearingMeetsTheSide() {
        assertEquals(Arc.SIDE, ArmorArcs.classify(37f, 0f, 0f));
    }

    @Test
    void profileReadsEachArc() {
        ArmorArcs.Profile p = new ArmorArcs.Profile(1f, 1.2f, .9f, .8f, .6f, .5f);
        assertEquals(1.2f, p.plate(Arc.FRONT));
        assertEquals(.8f, p.plate(Arc.SIDE));
        assertEquals(.5f, p.plate(Arc.REAR));
        assertEquals(.6f, p.rating(Arc.REAR));
        assertThrows(IllegalArgumentException.class, () -> new ArmorArcs.Profile(1f, 0f, 1f, 1f, 1f, 1f));
    }

    @Test
    void plateOfOneIsTheOrdinaryLaw() {
        DurabilityModel.Resolution plain = new DurabilityModel.Resolution();
        DurabilityModel.Resolution plated = new DurabilityModel.Resolution();
        DurabilityModel.resolveInto(140f, 12f, 100f, 18f, 300f, plain);
        DurabilityModel.resolvePlatedInto(140f, 12f, 100f, 18f, 1f, 300f, plated);
        assertEquals(plain.armorDamage(), plated.armorDamage());
        assertEquals(plain.structureDamage(), plated.structureDamage());
        assertEquals(plain.armorBroken(), plated.armorBroken());
    }

    @Test
    void thickPlateStripsSlowerAndHoldsLonger() {
        DurabilityModel.Resolution out = new DurabilityModel.Resolution();
        // Full penetration: efficiency 1. Plate 2 means 100 points of armor meet the hit as 200.
        DurabilityModel.resolvePlatedInto(50f, 40f, 100f, 18f, 2f, 300f, out);
        assertEquals(25f, out.armorDamage(), EPS);
        assertEquals(0f, out.structureDamage(), EPS);
        DurabilityModel.resolvePlatedInto(300f, 40f, 100f, 18f, 2f, 300f, out);
        assertEquals(100f, out.armorDamage(), EPS);
        assertEquals(100f, out.structureDamage(), EPS);
        assertTrue(out.armorBroken());
    }

    @Test
    void aScreenIsSpentBeforeThePlate() {
        DurabilityModel.Resolution out = new DurabilityModel.Resolution();
        DurabilityModel.resolvePlatedInto(50f, 40f, 30f, 100f, 18f, 2f, 300f, out);
        assertEquals(30f, out.mitigatedDamage(), EPS);
        assertEquals(10f, out.armorDamage(), EPS);
    }

    @Test
    void thinPlateBreaksSoonerAndPassesTheRestToStructure() {
        DurabilityModel.Resolution out = new DurabilityModel.Resolution();
        DurabilityModel.resolvePlatedInto(100f, 40f, 100f, 18f, .5f, 300f, out);
        assertEquals(100f, out.armorDamage(), EPS);
        assertEquals(50f, out.structureDamage(), EPS);
        DurabilityModel.resolvePlatedInto(20f, 40f, 100f, 18f, .5f, 300f, out);
        assertEquals(40f, out.armorDamage(), EPS);
        assertFalse(out.armorBroken());
    }
}
