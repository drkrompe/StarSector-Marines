package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechVariantTest {

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(40, 40);
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(40, 40));
    }

    @Test
    void catalogBuildsDistinctHardwareFromCompatibleComponents() {
        Set<String> ids = new HashSet<>();
        for (MechVariant variant : MechVariant.values()) assertTrue(ids.add(variant.id));

        assertEquals(LayeredMechAppearance.CHASSIS_CLEAN,
                MechVariant.BULWARK.chassisAppearance);
        assertEquals(LayeredMechAppearance.CHASSIS_HOUND,
                MechVariant.HOUND.chassisAppearance);
        assertEquals(LayeredMechAppearance.CHASSIS_SIROCCO,
                MechVariant.SIROCCO.chassisAppearance);
        assertNotEquals(MechVariant.HOUND.chassisAppearance,
                MechVariant.SIROCCO.chassisAppearance);

        MechLoadoutComponent bulwark = MechVariant.BULWARK.createLoadout(null);
        assertSame(MechWeaponComponent.DUAL_CHAINGUNS,
                bulwark.mount(MechMountSlot.ARMS).component);
        assertSame(MechWeaponComponent.SRM_15,
                bulwark.mount(MechMountSlot.LEFT_SHOULDER).component);
        assertSame(MechWeaponComponent.SRM_15,
                bulwark.mount(MechMountSlot.RIGHT_SHOULDER).component);
        assertEquals(4, bulwark.mount(MechMountSlot.LEFT_SHOULDER)
                .component.projectilesPerTrigger);
        assertEquals(4, bulwark.mount(MechMountSlot.RIGHT_SHOULDER)
                .component.projectilesPerTrigger);
        assertEquals(6, bulwark.mount(MechMountSlot.RIGHT_SHOULDER)
                .component.ammoCapacity);
        assertFalse(bulwark.hasWeapon(WeaponRegistry.MECH_SHOULDER_LASER_ID));

        MechLoadoutComponent hound = MechVariant.HOUND.createLoadout(null);
        assertFalse(hound.hasWeapon(WeaponRegistry.MECH_LRM_ARTILLERY_ID));
        assertSame(MechWeaponComponent.NOSE_CHAINGUN,
                hound.mount(MechMountSlot.ARMS).component);
        assertSame(MechWeaponComponent.SRM_5,
                hound.mount(MechMountSlot.LEFT_SHOULDER).component);
        assertNull(hound.mount(MechMountSlot.RIGHT_SHOULDER));
        assertEquals(6, hound.mount(MechMountSlot.ARMS).component.projectilesPerTrigger);

        MechLoadoutComponent sirocco = MechVariant.SIROCCO.createLoadout(null);
        assertFalse(sirocco.hasWeapon(WeaponRegistry.MECH_SRM_POD_ID));
        assertSame(MechWeaponComponent.SINGLE_HEAVY_CANNON,
                sirocco.mount(MechMountSlot.ARMS).component);
        assertSame(MechWeaponComponent.LRM_5,
                sirocco.mount(MechMountSlot.LEFT_SHOULDER).component);
        assertSame(MechWeaponComponent.LRM_5,
                sirocco.mount(MechMountSlot.RIGHT_SHOULDER).component);
        var heavyCannon = WeaponRegistry.require(WeaponRegistry.MECH_HEAVY_CANNON_ID);
        assertEquals(45f, heavyCannon.damage, 0.001f);
        assertEquals(18f, heavyCannon.penetration, 0.001f);
        assertEquals(26f, heavyCannon.range, 0.001f);
        assertTrue(heavyCannon.fx.hasHeavyImpact());
        assertEquals(1f, heavyCannon.aoeRadius, 0.001f);
        assertEquals(18, heavyCannon.wallDamage);
        assertEquals("graphics/missiles/shell_hellbore.png",
                heavyCannon.projectileSpritePath);

        MechLoadoutComponent custom = new MechLoadoutComponent(MechVariant.HOUND,
                MechWeaponComponent.DUAL_LINEAR_CANNONS,
                MechWeaponComponent.LRM_5, null, MechRole.LR_SUPPORT);
        assertSame(MechWeaponComponent.DUAL_LINEAR_CANNONS,
                custom.mount(MechMountSlot.ARMS).component);
        assertSame(MechWeaponComponent.LRM_5,
                custom.mount(MechMountSlot.LEFT_SHOULDER).component);
        assertNull(custom.mount(MechMountSlot.RIGHT_SHOULDER));
    }

    @Test
    void triTachyonDoctrineReplacesOnlyTheBulwarksRightMissileRack() {
        MechLoadoutComponent triTachyon = FactionMechLoadouts.create(
                MechVariant.BULWARK, null, "tritachyon");
        MechLoadoutComponent independent = FactionMechLoadouts.create(
                MechVariant.BULWARK, null, "independent");

        assertSame(MechWeaponComponent.SRM_15,
                triTachyon.mount(MechMountSlot.LEFT_SHOULDER).component);
        assertSame(MechWeaponComponent.SHOULDER_LASER_CANNON,
                triTachyon.mount(MechMountSlot.RIGHT_SHOULDER).component);
        assertTrue(triTachyon.hasWeapon(WeaponRegistry.MECH_SHOULDER_LASER_ID));
        assertSame(MechWeaponComponent.SRM_15,
                independent.mount(MechMountSlot.RIGHT_SHOULDER).component);
        assertFalse(independent.hasWeapon(WeaponRegistry.MECH_SHOULDER_LASER_ID));

        BattleSimulation sim = arena();
        long bulwark = sim.spawn(MechVariant.BULWARK.applyTo(new EntitySpec(
                "tri-tachyon bulwark", Faction.DEFENDER,
                UnitType.HEAVY_MECH, 10, 10)));
        assertEquals(MechVariant.BULWARK.maxWeaponRange(),
                sim.world().attackRange(bulwark), 0.001f);
        sim.world().attachMechLoadout(bulwark, triTachyon);
        assertEquals(WeaponRegistry.require(WeaponRegistry.MECH_SHOULDER_LASER_ID).range,
                sim.world().attackRange(bulwark), 0.001f,
                "installed faction hardware owns the live targeting envelope");
    }

    @Test
    void profileStatsAppearanceAndPhysicalBodyPersistOnTheEntity() {
        BattleSimulation sim = arena();
        BattleComponents components = sim.getBattleComponents();
        long hound = sim.spawn(new EntitySpec("hound", Faction.DEFENDER,
                UnitType.HEAVY_MECH, 10, 10).mechVariant(MechVariant.HOUND));
        sim.world().attachMechLoadout(hound, MechVariant.HOUND.createLoadout(null));

        assertSame(MechVariant.HOUND, sim.identity().mechVariant(hound));
        assertEquals(MechVariant.HOUND.maxStructure, sim.world().maxHp(hound), 0.001f);
        assertEquals(MechVariant.HOUND.armorCapacity, sim.world().armor(hound), 0.001f);
        assertEquals(MechVariant.HOUND.armorRating, sim.world().armorRating(hound), 0.001f);
        assertEquals(MechVariant.HOUND.moveSpeed, sim.movement().moveSpeed(hound), 0.001f);
        assertEquals(MechVariant.HOUND.radius, sim.getRoster().radius(hound), 0.001f);
        assertEquals(MechVariant.HOUND.hitHalfHeight,
                sim.getRoster().hitHalfHeight(hound), 0.001f);
        assertEquals(MechVariant.HOUND.renderScale,
                sim.getRoster().renderScale(hound), 0.001f);
        assertEquals(MechVariant.HOUND.chassisAppearance,
                sim.getEntityWorld().getInt(hound, components.MECH_LAYERED_ANIMATION,
                        BattleComponents.MECH_LAYERED_CHASSIS));
        assertEquals(MechWeaponComponent.SRM_5.appearanceSelector,
                sim.getEntityWorld().getInt(hound, components.MECH_LAYERED_ANIMATION,
                        BattleComponents.MECH_LAYERED_LEFT_SHOULDER));

        sim.applyDamage(hound, 100_000f, 1f, 0f);
        sim.advance(BattleSimulation.TICK_DT);
        assertSame(MechVariant.HOUND, sim.identity().mechVariant(hound));
        assertEquals(MechVariant.HOUND.renderScale,
                sim.getRoster().renderScale(hound), 0.001f);
    }

    @Test
    void genericFiringHonorsTheComponentsActuallyInstalled() {
        BattleSimulation sim = arena();
        long target = sim.spawn(new EntitySpec("target", Faction.MARINE,
                UnitType.MARINE, 20, 20));

        MechLoadoutComponent hound = MechVariant.HOUND.createLoadout(null);
        long houndId = sim.spawn(new EntitySpec("hound", Faction.DEFENDER,
                UnitType.HEAVY_MECH, 10, 20).mechVariant(MechVariant.HOUND));
        sim.world().attachMechLoadout(houndId, hound);
        hound.torsoAimTargetId = target;
        hound.torsoOnTarget = true;
        MechCombatantBehavior.tryFireMechWeapons(houndId, hound, target, 10f, sim, true);

        assertTrue(hound.mount(MechMountSlot.ARMS).cooldown > 0f);
        assertTrue(hound.mount(MechMountSlot.LEFT_SHOULDER).cooldown > 0f);
        assertNull(hound.mount(MechMountSlot.RIGHT_SHOULDER));
        assertFalse(hound.hasWeapon(WeaponRegistry.MECH_LRM_ARTILLERY_ID));

        MechLoadoutComponent sirocco = MechVariant.SIROCCO.createLoadout(null);
        long siroccoId = sim.spawn(new EntitySpec("sirocco", Faction.DEFENDER,
                UnitType.HEAVY_MECH, 5, 20).mechVariant(MechVariant.SIROCCO));
        sim.world().attachMechLoadout(siroccoId, sirocco);
        sirocco.torsoAimTargetId = target;
        sirocco.torsoOnTarget = true;
        MechCombatantBehavior.tryFireMechWeapons(siroccoId, sirocco, target, 35f, sim, false);

        assertEquals(0f, sirocco.mount(MechMountSlot.ARMS).cooldown, 0.001f);
        assertTrue(sirocco.mount(MechMountSlot.LEFT_SHOULDER).cooldown > 0f);
        assertTrue(sirocco.mount(MechMountSlot.RIGHT_SHOULDER).cooldown > 0f);
        assertFalse(sirocco.hasWeapon(WeaponRegistry.MECH_SRM_POD_ID));
    }

    @Test
    void debugActionFixtureSpawnsTheWholeFamilyInStableOrder() {
        BattleSimulation sim = arena();
        long[] ids = MechFamilyDebugSpawner.spawn(sim);

        assertEquals(3, ids.length);
        assertSame(MechVariant.BULWARK, sim.identity().mechVariant(ids[0]));
        assertSame(MechVariant.HOUND, sim.identity().mechVariant(ids[1]));
        assertSame(MechVariant.SIROCCO, sim.identity().mechVariant(ids[2]));
        assertSame(MechVariant.HOUND, sim.world().mechLoadout(ids[1]).variant);
        assertSame(MechVariant.SIROCCO, sim.world().mechLoadout(ids[2]).variant);
        assertNotEquals(sim.world().cellY(ids[0]), sim.world().cellY(ids[1]));
        assertEquals(sim.squad().squadId(ids[0]), sim.squad().squadId(ids[1]));
        assertEquals(sim.squad().squadId(ids[1]), sim.squad().squadId(ids[2]));
    }
}
