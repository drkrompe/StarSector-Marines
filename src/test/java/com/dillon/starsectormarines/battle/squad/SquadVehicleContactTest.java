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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadVehicleContactTest {

    @Test
    void aSightedVehicleIsAnOrdinaryContact() {
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
        // The aggregates the alert pass would have published this tick; the
        // picture reads them and answers for an empty squad without them.
        squad.aliveMembers = 1;
        squad.centroidX = sim.world().x(marine);
        squad.centroidY = sim.world().y(marine);

        // An APC bearing down is a contact like any other. The chassis carries
        // its own IDENTITY, so nothing about the picture has to know it is a
        // vehicle: it counts, it makes the contact actionable, and the squad
        // can reason about pursuing it.
        assertTrue(sim.identity().has(apc));
        assertEquals(1, sim.getTacticalScoring()
                .assessContactPicture(squad, sim.getSimTickIndex())
                .contactCount());
        assertTrue(WorldStateBuilder.hasActionableContact(squad, sim));
        assertDoesNotThrow(() -> sim.getTacticalScoring()
                .assessPursuit(marine, apc, squad));
    }

    /**
     * The end-to-end version: nobody hands the squad the contact. A chassis
     * drives into a marine's line of sight and the ordinary awareness pass
     * picks it up, because the spatial index it scans is over bodies rather
     * than over the dense infantry roster.
     */
    @Test
    void aSquadNoticesAChassisDrivingIntoView() {
        NavigationGrid grid = new NavigationGrid(80, 20);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        EntitySpec spec = new EntitySpec("marine", Faction.MARINE,
                UnitType.MARINE, 5, 10).squad(squadId);
        spec.moveSpeed = 0f;
        long marine = sim.spawn(spec);
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = marine;
        // Far enough that the squad cannot see him — he exists only so the
        // battle has two sides and keeps ticking.
        EntitySpec distant = new EntitySpec("far", Faction.DEFENDER,
                UnitType.MILITIA, 75, 10);
        distant.moveSpeed = 0f;
        sim.spawn(distant);

        VehicleMission mission = new VehicleMission(
                new float[]{12.5f, 13.5f}, new float[]{10.5f, 10.5f},
                new float[]{13.5f, 12.5f}, new float[]{10.5f, 10.5f},
                0f, VehicleType.HEAVY_APC.capacity);
        mission.state = VehicleState.INCOMING;
        long apc = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.DEFENDER, mission);

        sim.advance(BattleSimulation.TICK_DT);

        BelievedContact contact = squad.believedContact(apc);
        assertNotNull(contact, "the squad believes in the APC it can plainly see");
        assertEquals(BeliefSource.DIRECT, contact.source());
        assertEquals(SquadAlertLevel.ENGAGED, squad.alertLevel,
                "an APC bearing down raises the alarm like any other enemy");
    }
}
