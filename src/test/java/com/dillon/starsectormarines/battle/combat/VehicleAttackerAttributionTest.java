package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Damage credited to an attacker that carries no {@code IDENTITY}.
 *
 * <p>An armed convoy vehicle (today: {@link VehicleType#HEAVY_APC} with its
 * {@code HEAVY_MG}) fires with the <em>vehicle</em> entity as the attacker, and
 * a vehicle's archetype is {@code GROUND_IDENTITY}, not {@code IDENTITY}. The
 * Heavy MG has a splash radius, so every round that lands near a body reaches
 * {@link DamageResolver#resolve} with that attacker id. Reading the attacker's
 * faction there is fail-loud on an entity with no {@code IDENTITY}, which took
 * the whole sim down mid-battle. Attribution must skip an attacker that has
 * nowhere to be credited rather than crash on it.
 */
public class VehicleAttackerAttributionTest {

    private static final int W = 16;
    private static final int H = 16;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static VehicleMission mission() {
        return new VehicleMission(
                new float[]{0.5f, 1.5f}, new float[]{0.5f, 0.5f},
                new float[]{1.5f, 0.5f}, new float[]{0.5f, 0.5f},
                0f, VehicleType.HEAVY_APC.capacity);
    }

    @Test
    public void vehicleTurretDamageDoesNotCrashOnTheMissingIdentity() {
        BattleSimulation sim = openSim();
        long apc = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.MARINE, mission());
        long target = sim.spawn(new EntitySpec("d0", Faction.DEFENDER, UnitType.MARINE, 6, 6));
        float before = sim.world().hp(target);

        sim.applyDamage(target, apc, 5f, 1f, 1f);

        assertEquals(before - 5f, sim.world().hp(target), 1e-4f,
                "the hit lands exactly as it would from any other shooter");
        assertFalse(sim.telemetry().isRecorded(apc),
                "a convoy vehicle carries no TELEMETRY row to credit");
    }

    @Test
    public void vehicleTurretKillDoesNotCrashOnTheMissingIdentity() {
        BattleSimulation sim = openSim();
        long apc = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.MARINE, mission());
        long target = sim.spawn(new EntitySpec("d0", Faction.DEFENDER, UnitType.MARINE, 6, 6));

        sim.applyDamage(target, apc, sim.world().hp(target) + 10f, 1f, 1f);

        assertFalse(sim.world().isAlive(target), "an unattributable killing blow still kills");
    }

    @Test
    public void unitAttackerIsStillCredited() {
        BattleSimulation sim = openSim();
        long shooter = sim.spawn(new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, 4, 4));
        long target = sim.spawn(new EntitySpec("d0", Faction.DEFENDER, UnitType.MARINE, 6, 6));

        sim.applyDamage(target, shooter, 5f, 1f, 1f);

        assertTrue(sim.telemetry().isRecorded(shooter));
        assertEquals(5f, sim.telemetry().damageDealt(shooter), 1e-4f,
                "the tolerant gate must not cost a real combatant its credit");
    }
}
