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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechMissileReplenishmentTest {

    @Test
    void standardComponentRechargesRacksAndNothingElse() {
        MissileReplenisherComponent standard = MissileReplenisherComponent.STANDARD;

        assertTrue(Float.isFinite(standard.replenishmentSeconds(WeaponRegistry.MECH_SRM_ID)));
        assertTrue(Float.isFinite(standard.replenishmentSeconds(WeaponRegistry.MECH_LRM_ID)));
        assertEquals(standard.replenishmentSeconds(WeaponRegistry.MECH_SRM_ID),
                standard.replenishmentSeconds(WeaponRegistry.MECH_SRM_3_QS_ID), "the QS rack is an SRM rack");
        assertEquals(Float.POSITIVE_INFINITY,
                standard.replenishmentSeconds(WeaponRegistry.MECH_CHAINGUN_ID));
    }

    @Test
    void rechargeRepeatsWithoutALifetimeLimit() {
        MechWeaponMount mount = new MechWeaponMount(
                MechMountSlot.LEFT_SHOULDER, MechWeaponComponent.SRM_5);
        float recharge = MissileReplenisherComponent.STANDARD.replenishmentSeconds(mount.weaponId());
        mount.ammo = 0;

        for (int cycle = 0; cycle < 20; cycle++) {
            mount.advanceReplenishment(recharge, MissileReplenisherComponent.STANDARD);
            assertEquals(mount.component.ammoCapacity, mount.ammo, "cycle " + cycle + " should recharge the rack");
            mount.ammo = 0;
        }
    }

    @Test
    void aRackRechargesWholeOnceItsLastSalvoIsARechargeBehindIt() {
        MissileReplenisherComponent loader = MissileReplenisherComponent.STANDARD;
        MechWeaponMount mount = new MechWeaponMount(MechMountSlot.LEFT_SHOULDER, MechWeaponComponent.LRM_15);
        float recharge = loader.replenishmentSeconds(mount.weaponId());
        mount.ammo = mount.component.ammoCapacity - 1;

        mount.advanceReplenishment(recharge * .6f, loader);
        assertEquals(mount.component.ammoCapacity - 1, mount.ammo);
        mount.commitTrigger(0L, null);
        mount.advanceReplenishment(recharge * .6f, loader);
        assertEquals(mount.component.ammoCapacity - 2, mount.ammo, "a salvo restarts the recharge");
        assertEquals(recharge * .4f, mount.rechargeRemaining(), 1e-3f);
        mount.advanceReplenishment(recharge * .4f, loader);
        assertEquals(mount.component.ammoCapacity, mount.ammo, "every spent salvo comes back at once");
        assertEquals(0f, mount.rechargeRemaining());
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
