package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SquadVehicleContactTest {

    @Test
    void vehicleBeliefDoesNotMasqueradeAsInfantryIdentity() {
        NavigationGrid grid = new NavigationGrid(24, 14);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long marine = sim.spawn(new EntitySpec("marine", Faction.MARINE,
                UnitType.MARINE, 2, 5).squad(squadId));
        squad.leaderId = marine;

        VehicleMission mission = new VehicleMission(
                new float[]{8.5f, 9.5f}, new float[]{5.5f, 5.5f},
                new float[]{9.5f, 8.5f}, new float[]{5.5f, 5.5f},
                0f, VehicleType.HEAVY_APC.capacity);
        mission.state = VehicleState.LANDED;
        long apc = sim.convoy().spawn(
                VehicleType.HEAVY_APC, Faction.DEFENDER, mission);
        squad.observeDirectContact(apc, 8, 5, sim.getSimTickIndex());
        squad.publishBeliefSnapshot();

        assertFalse(sim.identity().has(apc));
        assertEquals(0, sim.getTacticalScoring()
                .assessContactPicture(squad, sim.getSimTickIndex())
                .contactCount());
        assertFalse(WorldStateBuilder.hasActionableContact(squad, sim));
        assertDoesNotThrow(() -> sim.getTacticalScoring()
                .assessPursuit(marine, apc, squad));
    }
}
