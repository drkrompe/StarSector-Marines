package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.infantry.InfantryWeapons;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.turret.DefensePostKind;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurretTargetabilityTest {

    private static final int WIDTH = 20;
    private static final int HEIGHT = 10;
    private static final int FIRING_ROW = 5;

    @Test
    void pulseRoundDamagesTurretMountedOnAnOpaqueMapCell() {
        BattleSimulation sim = openArena();
        NavigationGrid grid = sim.getGrid();
        int turretX = 10;
        grid.setWalkable(turretX, FIRING_ROW, false);
        grid.setSeeThrough(turretX, FIRING_ROW, false);

        DefensePost post = new DefensePost(DefensePostKind.LIGHT,
                turretX, FIRING_ROW, List.of(new DefensePost.TurretSpec(
                TurretCatalogRegistry.VULCAN_STRUCTURE_ID, turretX, FIRING_ROW)));
        long turret = BattleSetup.spawnDefensePostTurrets(sim, List.of(post)).getLong(0);

        assertTrue(grid.isSeeThrough(turretX, FIRING_ROW),
                "the live turret body must own interception instead of its mount cell acting as a wall");

        long shooter = sim.spawn(new EntitySpec("pulse-marine", Faction.MARINE,
                UnitType.MARINE, 2, FIRING_ROW)
                .primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID)));
        sim.world().setAccuracy(shooter, 1f);
        sim.getUnitIndex().rebuild(sim.getRoster());

        ShotService shots = sim.getShots();
        BallisticResolver resolver = new BallisticResolver(grid,
                new DoodadService(grid), sim.getUnitIndex(), sim.getRoster());
        InfantryWeapons weapons = new InfantryWeapons(sim.getRoster(), resolver,
                shots, grid, new QueueRandom(0f, 0.5f, 0.5f));

        float armorBefore = sim.world().armor(turret);
        weapons.fireShot(shooter, turret, FireStance.STANCED);

        assertEquals(1, shots.snapshotActiveImpacts().size(),
                "the pulse should resolve a physical turret contact");
        shots.tickImpacts(1f, impact -> sim.applyDamage(impact.victimId,
                impact.damage, impact.penetration, 0f));
        assertTrue(sim.world().armor(turret) < armorBefore,
                "the contacted pulse should remove turret armor");
    }

    private static BattleSimulation openArena() {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, new CellTopology(WIDTH, HEIGHT));
    }

    private static final class QueueRandom extends Random {
        private final ArrayDeque<Float> values = new ArrayDeque<>();

        QueueRandom(float... values) {
            for (float value : values) this.values.add(value);
        }

        @Override
        public float nextFloat() {
            if (values.isEmpty()) throw new IllegalStateException("QueueRandom exhausted");
            return values.removeFirst();
        }
    }
}
