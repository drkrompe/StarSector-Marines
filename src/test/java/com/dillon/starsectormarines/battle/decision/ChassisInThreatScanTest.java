package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A convoy chassis reaches every spatial-index scan on {@code IDENTITY} alone
 * and its archetype is a combatant, so the combatant type guard admits it —
 * but it carries no {@code COMBAT} component. A threat scan that reads
 * {@code World.attackRange} off whatever the index hands back is therefore
 * fail-loud on an ordinary APC driving past, which took the whole battle down.
 */
class ChassisInThreatScanTest {

    private static BattleSimulation arena(int w, int h) {
        NavigationGrid grid = new NavigationGrid(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(w, h));
    }

    /** An APC standing two cells from a marine, on the far faction. */
    private static long chassisNear(BattleSimulation sim, float x, float y) {
        ConvoyService convoy = sim.convoy();
        VehicleMission mission = new VehicleMission(
                new float[]{x - 4f, x}, new float[]{y, y},
                new float[]{x, x - 4f}, new float[]{y, y}, 0f, 4);
        long id = convoy.spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, mission);
        convoy.body(id).teleport(x, y, 0f);
        // A PENDING chassis is not yet on the map; INCOMING is the state it
        // spends its whole drive in, and the one the crash was reported from.
        mission.state = VehicleState.INCOMING;
        return id;
    }

    @Test
    void aChassisInRangeDoesNotCrashTheHiddenScan() {
        BattleSimulation sim = arena(24, 24);
        long marine = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 10, 10));
        chassisNear(sim, 12.5f, 10.5f);
        // The spatial index is rebuilt on tick, so a body spawned outside one
        // is not yet an answer to any query.
        sim.advance(BattleSimulation.TICK_DT);

        assertDoesNotThrow(() -> sim.getTacticalScoring().isHiddenFromAllEnemies(marine, 10, 10),
                "a scan asking who can shoot this cell must tolerate a body with no COMBAT");
        assertFalse(sim.getTacticalScoring().isHiddenFromAllEnemies(marine, 10, 10),
                "the APC's turret reaches the cell, so the marine is not hidden from it");
    }

    @Test
    void aChassisIsAThreatThatReaches() {
        BattleSimulation sim = arena(24, 24);
        long chassis = chassisNear(sim, 12.5f, 10.5f);

        assertDoesNotThrow(() -> sim.getTacticalScoring().threatReaches(chassis, 10.5f, 10.5f, 0f));
        assertTrue(sim.getTacticalScoring().threatReaches(chassis, 10.5f, 10.5f, 0f),
                "an armed chassis two cells away reaches, through its turret's range");
    }

    @Test
    void aDistantChassisDoesNotReach() {
        BattleSimulation sim = arena(64, 24);
        long chassis = chassisNear(sim, 60.5f, 10.5f);

        assertFalse(sim.getTacticalScoring().threatReaches(chassis, 2.5f, 10.5f, 0f),
                "tolerating the read must not make every chassis threaten every cell");
    }
}
