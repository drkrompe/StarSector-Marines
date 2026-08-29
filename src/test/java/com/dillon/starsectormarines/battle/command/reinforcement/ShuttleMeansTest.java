package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.GroundRosterRegistry;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice-4 retrofit coverage for {@link ShuttleMeans}: its LZ selection now
 * routes through {@link LandingZoneScorer}, so an air drop never sets down
 * inside a building even when the rally hint lands squarely on one.
 */
public class ShuttleMeansTest {

    private static final int W = 12;
    private static final int H = 12;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static TacticalNode commandPost(int x, int y) {
        return new TacticalNode(TacticalNode.Kind.COMMAND_POST, x, y,
                x - 1, y - 1, x + 1, y + 1, Faction.DEFENDER, 50, 4);
    }

    @Test
    public void shuttleLandsOutsideBuildingsWhenRallyIsInside() {
        BattleSimulation sim = openSim();
        // Alive COMMAND_POST so the shuttle supply gate passes.
        sim.getCompoundService().register(commandPost(2, 2));
        // Building footprint straddling the rally hint.
        CellTopology topo = sim.getTopology();
        for (int y = 4; y <= 6; y++) {
            for (int x = 4; x <= 6; x++) topo.setBuildingId(x, y, 1);
        }

        ShuttleMeans means = new ShuttleMeans(TraversalAxis.SOUTH_TO_NORTH);
        ReinforcementRequest req = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, 5, 5); // rally inside the building

        assertTrue(means.canFulfill(sim, req), "open ground exists outside the building near the rally");
        means.dispatch(sim, req);

        long[] airIds = sim.getAirEntityIds();
        assertEquals(1, airIds.length, "one shuttle dispatched");
        ShuttleMission mission = sim.world().mission(airIds[0]);
        assertEquals(CommandAuthority.REINFORCEMENT,
                mission.commandClaim.authority());
        assertEquals("reinforcement", mission.commandClaim.issuer());
        assertEquals(ReinforcementRequest.Reason.GARRISON_DEPLETED.name(),
                mission.commandClaim.reason());
        int lzX = (int) mission.lzX;
        int lzY = (int) mission.lzY;
        assertEquals(0, topo.getBuildingId(lzX, lzY), "LZ must be outside any building footprint");
        assertTrue(sim.getGrid().isWalkable(lzX, lzY), "LZ must be walkable");
    }

    @Test
    public void factionalShuttleCarriesEliteProfileLoadouts() {
        BattleSimulation sim = openSim();
        sim.getCompoundService().register(commandPost(2, 2));
        GroundRosterProfile roster = GroundRosterRegistry.resolve("tritachyon");
        ShuttleMeans means = new ShuttleMeans(
                TraversalAxis.SOUTH_TO_NORTH, roster, RiskLevel.HIGH);
        ReinforcementRequest req = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, 5, 5);

        means.dispatch(sim, req);

        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        assertEquals(roster.unitType(GroundRosterProfile.ForceTier.ELITE),
                mission.deboardUnitType);
        assertEquals(ShuttleType.AEROSHUTTLE.capacity,
                mission.marineLoadout.length);
        for (int i = 0; i < mission.marineLoadout.length; i++) {
            assertTrue(mission.marineLoadout[i].primaryDef() != null);
            assertTrue(mission.marineLoadout[i].armorFamily != null);
        }
    }

    /**
     * A sortie flies off the garrison's own field when it has one.
     *
     * <p>Entry and exit are both a hardstand: the craft lifts from the field,
     * delivers, and comes home to it, instead of materialising past the edge of
     * the world and vanishing back over it.
     */
    @Test
    public void theSortieFliesFromTheAirfieldWhenThereIsOne() {
        BattleSimulation sim = openSim();
        sim.getCompoundService().register(commandPost(2, 2));
        LandingPad pad = LandingPad.garrison(3, 9, LandingPad.Approach.SOUTH);
        ShuttleMeans means = new ShuttleMeans(TraversalAxis.SOUTH_TO_NORTH,
                null, RiskLevel.LOW, null, List.of(pad));
        ReinforcementRequest req = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, 8, 3);

        means.dispatch(sim, req);

        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        assertEquals(pad.centerX + 0.5f, mission.entryX, 0.001f,
                "the sortie starts on its hardstand, not off the map edge");
        assertEquals(pad.centerY + 0.5f, mission.entryY, 0.001f);
        assertEquals(pad.centerX + 0.5f, mission.exitX, 0.001f,
                "and comes home to the field rather than leaving the world");
        assertEquals(pad.centerY + 0.5f, mission.exitY, 0.001f);
    }

    /**
     * With no field, the sortie still arrives from off the map — a battle
     * without a garrison airfield behaves exactly as it did.
     */
    @Test
    public void withoutAnAirfieldTheSortieStillComesFromOffMap() {
        BattleSimulation sim = openSim();
        sim.getCompoundService().register(commandPost(2, 2));
        ShuttleMeans means = new ShuttleMeans(TraversalAxis.SOUTH_TO_NORTH);
        ReinforcementRequest req = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, 8, 3);

        means.dispatch(sim, req);

        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        assertTrue(mission.entryY > H || mission.entryY < 0,
                "a defender sortie with no field enters over the rear map edge");
    }

    /**
     * The landing zone is chosen around the deployment policy's safe band, not
     * around the rally.
     *
     * <p>The rally is where force is needed, which on a losing track is where
     * the marines are. Landing on it deboards a squad into whoever just took
     * the position, which is the one thing an air drop must not do.
     */
    @Test
    public void theLandingZoneFollowsTheSafeBandRatherThanTheRally() {
        BattleSimulation sim = openSim();
        sim.getCompoundService().register(commandPost(2, 2));
        DeliveryDeploymentPolicy rearBand = request -> new DeliveryDeployment(
                2, 10, 0, true, false, null);
        ShuttleMeans means = new ShuttleMeans(TraversalAxis.SOUTH_TO_NORTH,
                null, RiskLevel.LOW, rearBand, List.of());
        ReinforcementRequest req = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.OBJECTIVE_LOST,
                ReinforcementRequest.Strength.SMALL, 9, 1);

        means.dispatch(sim, req);

        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        assertTrue(mission.lzY > 5,
                "the drop belongs in the safe band at y=10, not on the rally at y=1;"
                        + " landed at " + mission.lzX + "," + mission.lzY);
    }

    private static TacticalNode airbase(int x, int y) {
        return new TacticalNode(TacticalNode.Kind.AIRBASE, x, y,
                x - 2, y - 2, x + 2, y + 2, Faction.DEFENDER, 65, 3);
    }

    /**
     * A sortie flown off an authored field is loaded on the ground: it waits on
     * its pad while a squad marches out from the defender rear to board it.
     */
    @Test
    public void anAirfieldSortieWaitsOnItsPadForASquadToMarchOut() {
        BattleSimulation sim = openSim();
        sim.setTacticalMap(new TacticalMap(List.of(commandPost(2, 2), airbase(3, 9))));
        LandingPad pad = LandingPad.garrison(3, 9, LandingPad.Approach.SOUTH);
        ShuttleMeans means = new ShuttleMeans(TraversalAxis.SOUTH_TO_NORTH,
                null, RiskLevel.LOW, null, List.of(pad));
        ReinforcementRequest req = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, 3, 3);

        means.dispatch(sim, req);

        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        assertEquals(ShuttleState.LOADING, mission.state,
                "the craft holds on its hardstand rather than flying in loaded");
        assertEquals(0, mission.marinesRemaining, "nobody is aboard yet");
        assertTrue(mission.embarkSquadId != com.dillon.starsectormarines.battle.squad.Squad.NO_SQUAD,
                "the sortie knows which squad it is waiting for");
        assertTrue(sim.getSquad(mission.embarkSquadId) != null,
                "and that squad was actually put on the map");
    }

    /**
     * Once the squad reaches the ramp it is taken aboard and the sortie lifts.
     *
     * <p>The pad sits on the defender rear edge here, so the marching squad
     * spawns within reach on the first tick — the walk itself is ordinary
     * squad movement and is not what this is testing.
     */
    @Test
    public void reachingTheRampLoadsTheSortieAndItLifts() {
        BattleSimulation sim = openSim();
        sim.setTacticalMap(new TacticalMap(List.of(
                commandPost(2, 2), airbase(6, H - 2))));
        LandingPad pad = LandingPad.garrison(6, H - 2, LandingPad.Approach.SOUTH);
        ShuttleMeans means = new ShuttleMeans(TraversalAxis.SOUTH_TO_NORTH,
                null, RiskLevel.LOW, null, List.of(pad));
        ReinforcementRequest req = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, 6, 2);

        means.dispatch(sim, req);
        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        assertEquals(ShuttleState.LOADING, mission.state);

        for (int i = 0; i < 30 && mission.state == ShuttleState.LOADING; i++) {
            sim.advance(1f / 30f);
        }

        assertTrue(mission.marinesRemaining > 0,
                "somebody got aboard: " + mission.marinesRemaining);
        assertFalse(mission.state == ShuttleState.LOADING,
                "a loaded sortie leaves the pad");
    }

    /**
     * Taking the field ends air delivery, whoever still holds the headquarters.
     */
    @Test
    public void losingTheAirfieldEndsAirDelivery() {
        BattleSimulation sim = openSim();
        sim.setTacticalMap(new TacticalMap(List.of(commandPost(2, 2))));
        LandingPad pad = LandingPad.garrison(3, 9, LandingPad.Approach.SOUTH);
        ShuttleMeans means = new ShuttleMeans(TraversalAxis.SOUTH_TO_NORTH,
                null, RiskLevel.LOW, null, List.of(pad));
        ReinforcementRequest req = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, 5, 5);

        assertFalse(means.canFulfill(sim, req),
                "no defender-held airbase on a map that has an airfield means no lift");

        sim.getCompoundService().register(airbase(3, 9));
        assertTrue(means.canFulfill(sim, req),
                "and holding the field restores it");
    }

    /**
     * A wide field with the ramp off to one side, placed at the far end of the
     * map from the defender rear so the crew has an actual walk. The node
     * anchors at the middle of the apron, which is what a real one does.
     */
    private static TacticalNode wideAirbase(int centreX, int centreY) {
        return new TacticalNode(TacticalNode.Kind.AIRBASE, centreX, centreY,
                centreX - 5, centreY - 2, centreX + 5, centreY + 2,
                Faction.DEFENDER, 65, 3);
    }

    /** A commander that owns the squads its deliveries bring, the way the Conquest defender does. */
    private static DeliveryDeploymentPolicy commanderOwning(String issuer) {
        return req -> new DeliveryDeployment(req.rallyX, req.rallyY, -1,
                false, false, SquadCommandClaim.mission(issuer, "relief"));
    }

    /**
     * The crew is sent to the ramp, not to the middle of the field.
     *
     * <p>An apron is wide and its tactical node anchors at the centre of it, so
     * "go to the airfield" puts a crew down several cells from the aircraft —
     * outside the reach the sortie loads from. They then stand there until the
     * sortie times out, and the next sortie marches four more out to join them,
     * which is the pile that appears beside a working airfield.
     */
    @Test
    public void theCrewIsSentToTheRampRatherThanTheMiddleOfTheField() {
        BattleSimulation sim = openSim();
        sim.setTacticalMap(new TacticalMap(List.of(
                commandPost(2, 2), wideAirbase(6, 2))));
        LandingPad pad = LandingPad.garrison(1, 2, LandingPad.Approach.SOUTH);
        ShuttleMeans means = new ShuttleMeans(TraversalAxis.SOUTH_TO_NORTH,
                null, RiskLevel.LOW, null, List.of(pad));
        ReinforcementRequest req = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, 3, 3);

        means.dispatch(sim, req);

        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        Squad crew = sim.getSquad(mission.embarkSquadId);
        assertNotNull(crew, "the crew was put on the map");
        ObjectiveAssignment task = crew.assignedObjective;
        assertNotNull(task, "a crew with no task is a crew that mills");
        assertEquals(AssignmentKind.DEFEND_SITE, task.kind(),
                "waiting on a lift is holding a place, which the infantry layer"
                        + " already knows how to do");
        assertEquals(pad.centerX, task.targetCellX(), "sent to the ramp");
        assertEquals(pad.centerY, task.targetCellY(), "sent to the ramp");
    }

    /**
     * A sortie borrows its crew and gives back whoever it did not take.
     *
     * <p>Held at reinforcement authority the survivors outrank the mission
     * commander, which is correct while the aircraft is waiting for them and
     * wrong the moment it is not: they would stand on the pad for the rest of
     * the battle, unusable, while the field kept marching out replacements.
     */
    @Test
    public void aClosedSortieHandsItsCrewToTheCommander() {
        BattleSimulation sim = openSim();
        sim.setTacticalMap(new TacticalMap(List.of(
                commandPost(2, 2), wideAirbase(6, 2))));
        LandingPad pad = LandingPad.garrison(1, 2, LandingPad.Approach.SOUTH);
        ShuttleMeans means = new ShuttleMeans(TraversalAxis.SOUTH_TO_NORTH,
                null, RiskLevel.LOW, commanderOwning("defender-command"),
                List.of(pad));
        ReinforcementRequest req = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, 3, 3);

        means.dispatch(sim, req);
        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        int crewId = mission.embarkSquadId;
        assertEquals(CommandAuthority.REINFORCEMENT,
                sim.getSquadCommandDirective(crewId).authority(),
                "nothing outbids the lift while it is still loading");

        // Out of time with nobody aboard: the sortie is scrubbed.
        mission.boardingPatience = 0.001f;
        sim.advance(1f / 30f);

        CommandDirective owner = sim.getSquadCommandDirective(crewId);
        assertNotNull(owner, "the crew is somebody's");
        assertEquals(CommandAuthority.MISSION_COMMAND, owner.authority());
        assertEquals("defender-command", owner.issuer(),
                "handed to the commander, which is the pool it draws from");
    }

    /**
     * Running out of time does not throw away the people who made it aboard.
     *
     * <p>Boarding takes a marine off the roster, so scrubbing a sortie that had
     * already loaded somebody deleted them rather than cancelling a delivery.
     */
    @Test
    public void aSortieThatLoadedAnybodyFliesWhenTimeRunsOut() {
        BattleSimulation sim = openSim();
        sim.setTacticalMap(new TacticalMap(List.of(
                commandPost(2, 2), wideAirbase(6, 2))));
        LandingPad pad = LandingPad.garrison(1, 2, LandingPad.Approach.SOUTH);
        ShuttleMeans means = new ShuttleMeans(TraversalAxis.SOUTH_TO_NORTH,
                null, RiskLevel.LOW, null, List.of(pad));
        ReinforcementRequest req = new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL, 3, 3);

        means.dispatch(sim, req);
        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        // Two up the ramp, the rest still out on the field, and the clock gone.
        mission.marinesRemaining = 2;
        mission.boardingPatience = 0.001f;
        sim.advance(1f / 30f);

        assertFalse(mission.state == ShuttleState.GONE,
                "the two who boarded were carried off the map, not deleted");
        assertEquals(2, mission.marinesRemaining, "and they are still aboard");
    }
}
