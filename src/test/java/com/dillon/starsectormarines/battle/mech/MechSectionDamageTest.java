package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The damage path writing sections, and crippled legs reading back as no walking speed. */
class MechSectionDamageTest {

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(16, 16));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static long hound(BattleSimulation sim) {
        long id = sim.spawn(new EntitySpec("hound", Faction.DEFENDER, UnitType.HEAVY_MECH, 8, 8)
                .mechVariant(MechVariant.HOUND));
        sim.world().attachMechLoadout(id, MechVariant.HOUND.createLoadout(null));
        return id;
    }

    @Test
    void anIntactMechWalksAndCrippledLegsStopItWalking() {
        try (BattleSimulation sim = arena()) {
            long id = hound(sim);
            MechSectionModifier modifier = new MechSectionModifier(sim.getRoster());
            MechLoadoutComponent loadout = sim.world().mechLoadout(id);

            assertFalse(MechSectionModifier.crippled(sim.world(), id), "no sections yet: intact");
            assertFalse(modifier.appliesTo(id));

            MechVariant v = MechVariant.HOUND;
            loadout.sectionsFor(v.maxStructure, v.maxStructure, v.armorCapacity, v.armorCapacity);
            assertFalse(modifier.appliesTo(id));

            MechSections.Hit hit = new MechSections.Hit();
            loadout.sections().resolve(MechSection.LEGS, 100_000f, 14f, 0f, v.armorRating,
                    new DurabilityModel.Resolution(), hit);

            assertTrue(modifier.appliesTo(id));
            assertEquals(0f, modifier.multiplier(id, UnitStat.MOVE_SPEED));
            assertEquals(1f, modifier.multiplier(id, UnitStat.ACCURACY));
        }
    }

    @Test
    void shoulderHitsEventuallyDestroyTheLeftPodAndLeaveTheMechStanding() {
        try (BattleSimulation sim = arena()) {
            long id = hound(sim);
            MechLoadoutComponent loadout = sim.world().mechLoadout(id);

            // Unattributed hits strike at a random height; the Hound's lone pod takes every
            // shoulder-height hit from either side, so it goes before the torso does.
            for (int i = 0; i < 400; i++) {
                sim.applyDamage(id, 3f, 14f);
                MechSections sections = loadout.sections();
                if (sections != null && sections.destroyed(MechSection.LEFT_SHOULDER)) break;
            }

            MechSections sections = loadout.sections();
            assertNotNull(sections);
            assertTrue(sections.destroyed(MechSection.LEFT_SHOULDER));
            assertFalse(sections.torsoDestroyed());
            assertTrue(sim.world().isAlive(id));
            assertNull(loadout.mount(MechMountSlot.LEFT_SHOULDER));
            assertSame(MechWeaponComponent.SRM_5, sections.lostMount(MechMountSlot.LEFT_SHOULDER));
            assertEquals(sections.totalStructure(), sim.world().hp(id), 1e-3f);
        }
    }
}
