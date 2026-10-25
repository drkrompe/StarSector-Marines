package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The material law and the one table of what the environment is made of. */
class EnvironmentMaterialTest {

    // Catalog penetrations the table is set against.
    private static final float SMG = 1f;
    private static final float PULSE = 5f;
    private static final float RIFLE = 7f;
    private static final float DMR = 11f;
    private static final float ANTI_MATERIEL = 18f;

    @Test
    void aMaterialIgnoresRoundsUnderHalfItsRatingAndTakesFullDamageAtIt() {
        assertEquals(0f, DurabilityModel.materialEfficiency(0f, 10f));
        assertEquals(0f, DurabilityModel.materialEfficiency(5f, 10f));
        assertEquals(0.5f, DurabilityModel.materialEfficiency(7.5f, 10f), 1e-6f);
        assertEquals(1f, DurabilityModel.materialEfficiency(10f, 10f));
        assertEquals(1f, DurabilityModel.materialEfficiency(40f, 10f));
    }

    @Test
    void armourKeepsItsChipFloorOnTheSameRatio() {
        assertEquals(DurabilityModel.MIN_ARMOR_EFFICIENCY, DurabilityModel.armorEfficiency(0f, 10f), 1e-6f);
        assertEquals(0.55f, DurabilityModel.armorEfficiency(5f, 10f), 1e-6f);
        assertEquals(1f, DurabilityModel.armorEfficiency(12f, 10f));
    }

    @Test
    void aBurningRoundCharsTimberWhateverItsPenetrationButNotStone() {
        assertEquals(0, EnvironmentMaterial.TIMBER.structuralDamage(16f, 3f), "a low-penetration round bounces off a trunk");
        assertEquals(16, EnvironmentMaterial.TIMBER.structuralDamage(16f, 3f, true));
        assertEquals(0, EnvironmentMaterial.STONE.structuralDamage(16f, 3f, true));
    }

    @Test
    void everyDamageablePropFamilyHasItsMaterial() {
        assertEquals(EnvironmentMaterial.WOOD, EnvironmentMaterial.forProp("doodad.crate"));
        assertEquals(EnvironmentMaterial.WOOD, EnvironmentMaterial.forProp("doodad.box-dam"));
        assertEquals(EnvironmentMaterial.WOOD, EnvironmentMaterial.forProp("doodad.desk-1"));
        assertEquals(EnvironmentMaterial.WIRE, EnvironmentMaterial.forProp("doodad.industrial-fence-straight-h"));
        assertEquals(EnvironmentMaterial.SANDBAG, EnvironmentMaterial.forProp("doodad.sandbag-straight-n"));
        assertEquals(EnvironmentMaterial.SANDBAG, EnvironmentMaterial.forProp("doodad.fort-sandbag-n"));
        assertEquals(EnvironmentMaterial.DRUM, EnvironmentMaterial.forProp("doodad.industrial-drum-cluster"));
        assertEquals(EnvironmentMaterial.MACHINERY, EnvironmentMaterial.forProp("doodad.industrial-generator"));
        assertEquals(EnvironmentMaterial.STONE, EnvironmentMaterial.forProp("nature.rock-large-1"));
        assertEquals(EnvironmentMaterial.TIMBER, EnvironmentMaterial.forProp("forest.oak"));
        assertNull(EnvironmentMaterial.forProp("doodad.not-a-family"));
        assertNull(EnvironmentMaterial.forProp(null));
        assertEquals(EnvironmentMaterial.MASONRY, EnvironmentMaterial.forBarrier(SharedEdgeBarrier.Kind.WINDOW));
        assertEquals(EnvironmentMaterial.SANDBAG, EnvironmentMaterial.forBarrier(SharedEdgeBarrier.Kind.REVETMENT));
    }

    @Test
    void lightCoverFallsToSmallArmsAndHeavyCoverOnlyToHeavierRounds() {
        assertTrue(EnvironmentMaterial.WOOD.efficiency(SMG) > 0f, "an SMG chews a crate");
        assertEquals(1f, EnvironmentMaterial.WOOD.efficiency(PULSE));
        assertEquals(1f, EnvironmentMaterial.WIRE.efficiency(RIFLE));
        float sandbagRifle = EnvironmentMaterial.SANDBAG.efficiency(RIFLE);
        assertTrue(sandbagRifle > 0.3f && sandbagRifle < 1f, "a rifle wears sandbags slowly: " + sandbagRifle);
        assertTrue(EnvironmentMaterial.SANDBAG.efficiency(PULSE) < 0.2f, "pulse fire does not bite packed earth");
        for (EnvironmentMaterial heavy : new EnvironmentMaterial[] {EnvironmentMaterial.MACHINERY,
                EnvironmentMaterial.TIMBER, EnvironmentMaterial.STONE, EnvironmentMaterial.MASONRY}) {
            assertEquals(0f, heavy.efficiency(RIFLE), heavy + " ignores a rifle");
            assertTrue(heavy.efficiency(ANTI_MATERIEL) > 0.5f, heavy + " yields to anti-materiel rounds");
        }
        assertEquals(0f, EnvironmentMaterial.MASONRY.efficiency(DMR), "a DMR does not wear a wall");
    }

    @Test
    void structuralDamageIsThePayloadScaledAndRoundedToWholePoints() {
        assertEquals(14, EnvironmentMaterial.WOOD.structuralDamage(14f, RIFLE));
        assertEquals(0, EnvironmentMaterial.MASONRY.structuralDamage(14f, RIFLE));
        assertEquals(27, EnvironmentMaterial.MASONRY.structuralDamage(42f, ANTI_MATERIEL));
        assertEquals(0, EnvironmentMaterial.WOOD.structuralDamage(0f, RIFLE));
    }
}
