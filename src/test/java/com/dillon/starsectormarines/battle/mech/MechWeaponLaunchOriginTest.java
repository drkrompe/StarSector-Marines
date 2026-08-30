package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MechWeaponLaunchOriginTest {

    private static final float EPS = 1e-4f;

    @Test
    void dualChaingunsAlternateBetweenThePosedArms() {
        BattleSimulation sim = arena();
        long mech = spawnBulwark(sim, 8, 8);
        long target = sim.spawn(new EntitySpec(
                "target", Faction.DEFENDER, UnitType.MARINE, 8, 20));
        MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
        loadout.torsoFacingDegrees = 0f;
        MechWeaponMount arms = loadout.mount(MechMountSlot.ARMS);

        sim.fireMechWeapon(mech, target, arms, 1f);
        ShotEvent first = sim.getActiveShots().get(0);
        arms.burstRemaining = arms.component.projectilesPerTrigger - 1;
        sim.fireMechWeapon(mech, target, arms, 1f);
        ShotEvent second = sim.getActiveShots().get(1);

        float hullWidth = LayeredMechAppearance.hullWidthCells(
                loadout.variant.renderScale);
        assertEquals(sim.world().renderX(mech) - 0.37f * hullWidth, first.fromX, EPS);
        assertEquals(sim.world().renderX(mech) + 0.37f * hullWidth, second.fromX, EPS);
        assertEquals(sim.world().renderY(mech) + 0.39f * hullWidth, first.fromY, EPS);
        assertEquals(first.fromY, second.fromY, EPS);
    }

    @Test
    void missileShotAndProjectileStartAtTheInstalledShoulderPod() {
        BattleSimulation sim = arena();
        long mech = spawnBulwark(sim, 8, 8);
        long target = sim.spawn(new EntitySpec(
                "target", Faction.DEFENDER, UnitType.MARINE, 8, 20));
        MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
        loadout.torsoFacingDegrees = 0f;
        MechWeaponMount srm = loadout.mount(MechMountSlot.LEFT_SHOULDER);

        sim.fireMechWeapon(mech, target, srm, 1f);

        ShotEvent shot = sim.getActiveShots().get(0);
        Projectile projectile = sim.getActiveProjectiles().get(0);
        float hullWidth = LayeredMechAppearance.hullWidthCells(
                loadout.variant.renderScale);
        float expectedX = sim.world().renderX(mech) - 0.40f * hullWidth;
        float expectedY = sim.world().renderY(mech) + 0.16f * hullWidth;
        assertEquals(expectedX, shot.fromX, EPS);
        assertEquals(expectedY, shot.fromY, EPS);
        assertEquals(shot.fromX, projectile.fromX, EPS);
        assertEquals(shot.fromY, projectile.fromY, EPS);
    }

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(32, 32);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(32, 32), 12345L);
    }

    private static long spawnBulwark(BattleSimulation sim, int x, int y) {
        long mech = sim.spawn(MechVariant.BULWARK.applyTo(new EntitySpec(
                "bulwark", Faction.MARINE, UnitType.HEAVY_MECH, x, y)));
        sim.world().attachMechLoadout(mech,
                MechVariant.BULWARK.createLoadout(MechVariant.BULWARK.defaultRole));
        return mech;
    }
}
