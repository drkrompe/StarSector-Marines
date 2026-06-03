package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.command.compound.CompoundCaptureSystem;
import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.TestUnits;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice-3 coverage: each reinforcement means rejects its requests once the
 * supply structure it's tied to has flipped to {@code MARINE_HELD}, and
 * {@link GarrisonDepletedTrigger} skips marine-held compounds. Tests share
 * a small synthetic open-grid sim built per-test (no shared state).
 *
 * <p>Each means tests the same shape: build a sim, register one compound
 * of the relevant kind, assert {@code canFulfill = true} initially, walk
 * the compound through the capture state machine to MARINE_HELD via
 * {@link CompoundCaptureSystem} ticks, assert {@code canFulfill = false}
 * afterwards. The gate is the only thing exercised — full dispatch paths
 * stay covered by playtest + existing per-means construction tests.
 */
public class CompoundSupplyGatingTest {

    private static final int W = 10;
    private static final int H = 10;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /**
     * 10x10 grid split by a wall at column 5 with a doorway at (5, 5). The
     * two halves are distinct zones; used by the multi-compound test so a
     * single marine in one half doesn't propagate "captured" presence to a
     * compound in the other half (which it does on the open-zone helper).
     */
    private static BattleSimulation splitSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (x == 5) continue;
                grid.setWalkableFloor(x, y);
            }
        }
        grid.setWalkableFloor(5, 5);
        grid.setDoorway(5, 5, true);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static TacticalNode compoundAt(TacticalNode.Kind kind, int x, int y) {
        return new TacticalNode(kind, x, y,
                x - 1, y - 1, x + 1, y + 1,
                Faction.DEFENDER, 50, 4);
    }

    /** Drives the compound to MARINE_HELD by adding a marine to the compound zone and ticking the capture system. */
    private static void captureCompound(BattleSimulation sim, CompoundService service,
                                        CompoundCaptureSystem system, int marineX, int marineY) {
        sim.addUnit(new Entity("cap-marine", Faction.MARINE, UnitType.MARINE, marineX, marineY));
        int ticks = 2 + (int) Math.ceil(
                CompoundService.MARINE_HOLD_TIME / CompoundCaptureSystem.CAPTURE_TICK_PERIOD);
        for (int i = 0; i < ticks; i++) {
            system.tick(CompoundCaptureSystem.CAPTURE_TICK_PERIOD, sim, service);
        }
    }

    private static ReinforcementRequest defenderRequest(int x, int y) {
        return new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL,
                x, y);
    }

    @Test
    public void walkInRejectsWhenBarracksCaptured() {
        BattleSimulation sim = openSim();
        WalkInMeans means = new WalkInMeans(TraversalAxis.SOUTH_TO_NORTH);
        CompoundService service = sim.getCompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        service.register(compoundAt(TacticalNode.Kind.BARRACKS, 5, 5));

        ReinforcementRequest req = defenderRequest(5, 5);
        assertTrue(means.canFulfill(sim, req),
                "BARRACKS defender-held should let walk-in fulfil");

        captureCompound(sim, service, system, 5, 5);

        assertFalse(means.canFulfill(sim, req),
                "BARRACKS marine-held should retire walk-in");
    }

    @Test
    public void shuttleRejectsWhenCommandPostCaptured() {
        BattleSimulation sim = openSim();
        ShuttleMeans means = new ShuttleMeans(TraversalAxis.SOUTH_TO_NORTH);
        CompoundService service = sim.getCompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        service.register(compoundAt(TacticalNode.Kind.COMMAND_POST, 5, 5));

        ReinforcementRequest req = defenderRequest(5, 5);
        assertTrue(means.canFulfill(sim, req),
                "COMMAND_POST defender-held should let shuttle fulfil");

        captureCompound(sim, service, system, 5, 5);

        assertFalse(means.canFulfill(sim, req),
                "COMMAND_POST marine-held should retire shuttle");
    }

    @Test
    public void convoyRejectsWhenArmoryCaptured() {
        BattleSimulation sim = openSim();
        // ConvoyMeans needs a road graph with at least one perimeter node so
        // the other (non-compound) feasibility checks pass; otherwise the
        // gate-under-test is shadowed by the road-graph guard. Synthesize a
        // minimal graph by hand: one perimeter node on the south edge.
        RoadGraph graph = singlePerimeterNodeGraph();
        ConvoyMeans means = new ConvoyMeans(graph, null);
        CompoundService service = sim.getCompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        service.register(compoundAt(TacticalNode.Kind.ARMORY, 5, 5));

        ReinforcementRequest req = defenderRequest(5, 5);
        assertTrue(means.canFulfill(sim, req),
                "ARMORY defender-held should let convoy fulfil");

        captureCompound(sim, service, system, 5, 5);

        assertFalse(means.canFulfill(sim, req),
                "ARMORY marine-held should retire convoy");
    }

    @Test
    public void walkInStaysFulfillableWhileAnyBarracksAlive() {
        // hasAliveCompound is an OR across records of the kind. Two
        // BARRACKS in distinct zones, capture only the left one, walk-in
        // should still fulfil — the right BARRACKS sustains supply.
        // Split-zone sim (wall at col 5) so the captured marine in the
        // left half doesn't propagate "marines-present" to the right
        // compound's zone; the open-zone helper has one zone covering
        // every cell and would flip both.
        BattleSimulation sim = splitSim();
        WalkInMeans means = new WalkInMeans(TraversalAxis.SOUTH_TO_NORTH);
        CompoundService service = sim.getCompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        TacticalNode b1 = compoundAt(TacticalNode.Kind.BARRACKS, 3, 5);
        TacticalNode b2 = compoundAt(TacticalNode.Kind.BARRACKS, 7, 5);
        service.register(b1);
        service.register(b2);

        ReinforcementRequest req = defenderRequest(3, 5);
        assertTrue(means.canFulfill(sim, req),
                "two defender-held BARRACKS → walk-in fulfils");

        // Capture only b1 (left half).
        captureCompound(sim, service, system, 3, 5);
        assertEquals(CompoundService.CompoundState.MARINE_HELD,
                service.getRecord(b1).state);
        assertEquals(CompoundService.CompoundState.DEFENDER_HELD,
                service.getRecord(b2).state);

        assertTrue(means.canFulfill(sim, req),
                "one BARRACKS captured, one still defender-held → walk-in still fulfils");
    }

    @Test
    public void triggerSkipsMarineHeldCompound() {
        // GarrisonDepletedTrigger normally posts when alive/originalSize <
        // 50%. Stand up a defender squad assigned to a compound with all
        // members dead, walk the compound to MARINE_HELD, and assert the
        // trigger emits zero requests — supply is dead, no point feeding
        // the corpse.
        BattleSimulation sim = openSim();
        CompoundService service = sim.getCompoundService();
        CompoundCaptureSystem system = new CompoundCaptureSystem();
        TacticalNode compound = compoundAt(TacticalNode.Kind.BARRACKS, 5, 5);
        service.register(compound);

        Entity defender = new Entity("d1", Faction.DEFENDER, UnitType.MILITIA, 5, 5);
        int sid = sim.mintSquad(Faction.DEFENDER, defender);
        Squad squad = sim.getSquad(sid);
        squad.assignedNode = compound;
        squad.originalSize = 4;
        squad.aliveMembers = 1; // 25% — below the 50% threshold
        sim.addUnit(defender);
        TestUnits.kill(sim, defender); // kill the leader so MARINE_HELD reads cleanly

        captureCompound(sim, service, system, 5, 5);

        GarrisonDepletedTrigger trigger = new GarrisonDepletedTrigger();
        List<ReinforcementRequest> emitted = new ArrayList<>();
        trigger.check(sim, emitted::add);
        assertTrue(emitted.isEmpty(),
                "trigger must not post for a marine-held compound, even when the assigned squad is depleted");
    }

    /** Synthesizes a {@link RoadGraph} with one perimeter node + one interior node + a connecting edge so {@code ConvoyMeans.canFulfill}'s non-compound guards pass. Intentionally minimal so the test is about the supply gate, not routing geometry. */
    private static RoadGraph singlePerimeterNodeGraph() {
        RoadGraph.Node perim = new RoadGraph.Node(0, 5, 0, true);
        RoadGraph.Node interior = new RoadGraph.Node(1, 5, 4, false);
        RoadGraph.Edge edge = new RoadGraph.Edge(0, perim, interior,
                new int[]{5, 5, 5, 5, 5},
                new int[]{0, 1, 2, 3, 4});
        return new RoadGraph(List.of(perim, interior), List.of(edge));
    }
}
