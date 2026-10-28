package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.Crippled;
import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Crippled legs as the movement modifier reads them. Asked of the sections and the modifier
 * directly; that a crippled chassis in a battle limps on is the
 * {@code mech-locational-damage} scene's verdict, not this test's.
 */
class MechSectionDamageTest {

    @Test
    void legsAtZeroCrippleTheSectionsAndTheModifierSlowsWalkingToACrawl() {
        MechVariant v = MechVariant.HOUND;
        MechLoadoutComponent loadout = v.createLoadout(null);
        MechSections sections = loadout.sectionsFor(v.maxStructure, v.maxStructure, v.armorCapacity, v.armorCapacity);
        assertFalse(sections.crippled(), "intact legs walk");

        sections.resolve(MechSection.LEGS, 400f, 14f, 0f, v.armorRating,
                new DurabilityModel.Resolution(), new MechSections.Hit());

        assertTrue(sections.crippled());
        MechSectionModifier modifier = new MechSectionModifier(null);
        assertEquals(Crippled.SPEED_SHARE, modifier.multiplier(1L, UnitStat.MOVE_SPEED), "slowed, never stranded");
        assertEquals(1f, modifier.multiplier(1L, UnitStat.ACCURACY), "turning and firing are untouched");
    }
}
