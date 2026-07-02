package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Coverage for {@link TurretBehavior}'s ferry between a turret's
 * {@code TURRET_STATE} component and the shared {@link TurretAim.State}
 * carrier — the behavior-relocation half of slice B2 (the {@link MapTurret}
 * dissolution). Both cases are behavior-preserving reads/writes that used to
 * live on the dissolved subclass's fields.
 */
public class TurretBehaviorTest {

    private static BattleSimulation openArena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        CellTopology topology = new CellTopology(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid, topology);
    }

    @Test
    public void recoilTimerAgesEachUpdateWhenNoTargetInRange() {
        BattleSimulation sim = openArena(20, 20);
        Entity turret = MapTurret.create("t0", Faction.DEFENDER, TurretKind.VULCAN, 10, 10).toEntity();
        sim.addUnit(turret);

        TurretBehavior.INSTANCE.update(turret, sim);

        // Seeded to 1f (past the renderer's recoil window); one update ages it
        // by exactly one TICK_DT since nothing fired to reset it to 0.
        assertEquals(1f + BattleSimulation.TICK_DT, sim.turretState().recoilTimer(turret.entityId), 1e-4f,
                "no target in range → recoil timer just ages, doesn't reset");
    }

    @Test
    public void burstKindLatchesRemainingRoundsIntoTurretStateOnFire() {
        BattleSimulation sim = openArena(40, 40);
        Entity turret = MapTurret.create("t0", Faction.DEFENDER, TurretKind.VULCAN, 10, 10).toEntity();
        sim.addUnit(turret);
        // Due north of the turret (same cellX): bearing-to-target is exactly 0°,
        // matching the turret's zero-init facingDegrees, so the fire-arc gate
        // passes on the very first update with no slew needed. Well within
        // VULCAN's 22-cell range.
        Entity enemy = new Entity("m0", Faction.MARINE, UnitType.MARINE, 10, 20);
        sim.addUnit(enemy);

        TurretBehavior.INSTANCE.update(turret, sim);

        long id = turret.entityId;
        assertEquals(TurretKind.VULCAN.burstCount - 1, sim.turretState().burstRemaining(id),
                "the trigger pull fires round 1; the burst pump latches the remaining rounds");
        assertEquals(TurretKind.VULCAN.burstSpacing, sim.turretState().burstTimer(id), 1e-4f);
        assertEquals(enemy.entityId, sim.turretState().burstTargetId(id),
                "the burst locks onto the acquired target's id");
        assertEquals(0f, sim.turretState().recoilTimer(id), 1e-4f,
                "firing resets the recoil timer so the renderer's slide restarts");
        assertEquals(0f, sim.turretState().facingDegrees(id), 1e-4f,
                "already aligned with the due-north bearing — no slew needed to fire");
    }
}
