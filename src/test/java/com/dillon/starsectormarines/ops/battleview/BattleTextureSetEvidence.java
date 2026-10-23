package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A battle's texture set is what is on its field, not what the catalogs ship
 * ({@code battle-textures.md}). It stands a small battle up to ask, so it is evidence rather
 * than a unit test.
 */
@Tag("generated-space-evidence")
class BattleTextureSetEvidence {

    @Test
    void aSmallBattleNamesTheFamiliesAndSidesOnItsFieldAndNoVehicles() {
        var world = SceneBuilder.openGround(40, 30)
                .squad("infantry").size(4).inFile(4, 4, 1, 0).done()
                .squad("lance").size(1).mech(MechVariant.HOUND, MechRole.BALANCED).inFile(10, 10, 1, 0).done()
                .unit("enemy", Faction.DEFENDER, UnitType.MARINE, 36, 26).build();
        try (BattleSimulation sim = world.sim()) {
            BattleTextureSet set = BattleTextureSet.of(sim);

            assertFalse(set.families().isEmpty(), "the infantry's armour is named");
            assertTrue(set.families().size() < EnumSet.allOf(LayeredArmorFamily.class).size(),
                    "not every family the catalogs ship: " + set.families());
            assertFalse(set.families().contains(LayeredArmorFamily.XENO), "no swarm on this field");
            assertFalse(set.ravRigs(), "no Rav Bloom on this field");
            assertTrue(set.mechSides().contains(Faction.MARINE), "the marines field a mech");
            assertEquals(EnumSet.noneOf(VehicleType.class),
                    set.vehicles(), "no vehicle line on this field");
        }
    }
}
