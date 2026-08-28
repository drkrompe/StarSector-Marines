package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleDurabilityIntegrationTest {

    private static BattleSimulation openArena() {
        NavigationGrid grid = new NavigationGrid(24, 14);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(grid.getWidth(), grid.getHeight()), 7L);
    }

    private static long spawnVisibleApc(BattleSimulation sim) {
        VehicleMission mission = new VehicleMission(
                new float[]{8.5f, 9.5f}, new float[]{5.5f, 5.5f},
                new float[]{9.5f, 8.5f}, new float[]{5.5f, 5.5f},
                0f, VehicleType.HEAVY_APC.capacity);
        mission.state = VehicleState.LANDED;
        return sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, mission);
    }

    @Test
    void hostileApcIsSelectedAndPhysicallyHitByDirectFire() {
        BattleSimulation sim = openArena();
        long shooter = sim.spawn(new EntitySpec("marine", Faction.MARINE,
                UnitType.MARINE, 2, 5));
        long apc = spawnVisibleApc(sim);

        assertEquals(apc, sim.getTacticalScoring().findBestTarget(shooter));
        assertTrue(sim.isCombatTarget(apc));
        assertTrue(sim.isHardenedTarget(apc));

        BallisticResolver resolver = new BallisticResolver(sim.getGrid(),
                new DoodadService(sim.getGrid()), sim.getUnitIndex(), sim.getRoster());
        BallisticResolver.Resolution hit = resolver.resolve(shooter, apc,
                1f, 0f, 48f, new MidRollRandom());
        assertEquals(BallisticResolver.StopKind.UNIT_HIT, hit.kind());
        assertEquals(apc, hit.victimId());
        assertTrue(hit.hitIntended());
    }

    @Test
    void armorMakesRiflesWeakAndRocketsCreateOnePersistentWreck() {
        BattleSimulation sim = openArena();
        long apc = spawnVisibleApc(sim);
        VehicleMission mission = sim.convoy().mission(apc);
        mission.routeClearance = VehicleClearance.erode(sim.getGrid(), 1);
        VehicleClearance clearanceBeforeWreck = mission.routeClearance;

        // Field-rifle profile: 14 damage / 7 penetration. It only chips armor.
        sim.applyDamage(apc, 14f, 7f);
        assertEquals(220f, sim.convoy().structure(apc), 1e-3f);
        assertEquals(153.7f, sim.convoy().armor(apc), 1e-3f);

        // Annihilator profile: 162 damage / 18 penetration. Three hits kill.
        sim.applyDamage(apc, 162f, 18f);
        sim.applyDamage(apc, 162f, 18f);
        sim.applyDamage(apc, 162f, 18f);

        assertEquals(VehicleState.WRECKED, mission.state);
        assertEquals(0f, sim.convoy().structure(apc), 1e-3f);
        assertFalse(sim.convoy().isTargetable(apc));
        assertFalse(sim.getGrid().isWalkable(8, 5),
                "the persistent chassis footprint becomes a real navigation obstacle");
        assertTrue(mission.routeClearance != clearanceBeforeWreck,
                "wreck creation refreshes active vehicles' immutable clearance snapshots");
        assertFalse(mission.routeClearance.isPassable(8, 5),
                "recovery routing sees the new wreck instead of its stale spawn-time mask");
        assertEquals(1, sim.getSmokingWrecks().size());
        assertEquals(0, mission.marinesRemaining, "onboard passengers resolve once at destruction");
        assertTrue(sim.liveUnitCount() >= 1 && sim.liveUnitCount() <= 2,
                "one or two badly wounded passengers eject");
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long survivor = sim.liveUnitAt(i);
            assertEquals(Faction.DEFENDER, sim.identity().faction(survivor));
            assertTrue(sim.world().hp(survivor) <= sim.world().maxHp(survivor) * 0.25f + 1e-3f);
        }

        int survivors = sim.liveUnitCount();
        sim.applyDamage(apc, 999f, 999f);
        assertEquals(survivors, sim.liveUnitCount());
        assertEquals(1, sim.getSmokingWrecks().size(),
                "stacked/late damage cannot repeat passenger or wreck side effects");
    }

    private static final class MidRollRandom extends Random {
        @Override public float nextFloat() { return 0.5f; }
    }
}
