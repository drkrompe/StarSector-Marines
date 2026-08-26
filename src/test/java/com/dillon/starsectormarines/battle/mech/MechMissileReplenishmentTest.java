package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MechMissileReplenishmentTest {

    @Test
    void standardComponentOwnsSrmAndLrmCadence() {
        MissileReplenisherComponent standard = MissileReplenisherComponent.STANDARD;

        assertEquals(12f, standard.replenishmentSeconds(WeaponRegistry.MECH_SRM_POD_ID));
        assertEquals(18f, standard.replenishmentSeconds(WeaponRegistry.MECH_LRM_ARTILLERY_ID));
        assertEquals(Float.POSITIVE_INFINITY,
                standard.replenishmentSeconds(WeaponRegistry.MECH_CHAINGUN_ID));
    }

    @Test
    void replenishmentRepeatsWithoutALifetimeLimit() {
        MechWeaponMount mount = new MechWeaponMount(
                MechMountSlot.LEFT_SHOULDER, MechWeaponComponent.SRM_5);
        mount.ammo = 0;

        for (int cycle = 0; cycle < 20; cycle++) {
            mount.advanceReplenishment(12f, MissileReplenisherComponent.STANDARD);
            assertEquals(1, mount.ammo, "cycle " + cycle + " should restore a trigger");
            mount.consumeTrigger();
        }

        assertEquals(0, mount.ammo);
    }

    @Test
    void installedUpgradeComponentControlsLiveTickSpeed() {
        BattleSimulation sim = openSimulation();
        long mech = sim.spawn(MechVariant.HOUND.applyTo(new EntitySpec(
                "hound", Faction.MARINE, UnitType.HEAVY_MECH, 5, 5)));
        sim.spawn(new EntitySpec(
                "battle_anchor", Faction.DEFENDER, UnitType.MARINE, 15, 15));
        MechLoadoutComponent loadout = MechVariant.HOUND.createLoadout(MechRole.ASSAULT);
        MissileReplenisherComponent upgraded = new MissileReplenisherComponent(
                "test_fast_replenisher", "Test fast replenisher", 1f, 2f);
        loadout.installMissileReplenisher(upgraded);
        loadout.mount(MechMountSlot.LEFT_SHOULDER).ammo = 0;
        sim.world().attachMechLoadout(mech, loadout);

        advanceTicks(sim, 29);
        assertEquals(0, loadout.mount(MechMountSlot.LEFT_SHOULDER).ammo);
        advanceTicks(sim, 2);

        assertEquals(1, loadout.mount(MechMountSlot.LEFT_SHOULDER).ammo);
        assertSame(upgraded, loadout.missileReplenisher());
    }

    private static void advanceTicks(BattleSimulation sim, int ticks) {
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    private static BattleSimulation openSimulation() {
        NavigationGrid grid = new NavigationGrid(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(16, 16));
    }
}
