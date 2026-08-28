package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.GroundRosterRegistry;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        ConvoyDeploymentPolicy rearBand = request -> new ConvoyDeployment(
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
}
