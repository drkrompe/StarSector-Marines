package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice-3 coverage for {@link FrontLineReinforcementTrigger}: nearest-to-
 * defender slice selection, in-slice round-robin, the dispatched/conceded
 * eligibility filters (inherited from {@link RecaptureTargetService}), the
 * rear-shift rally clamp, and the {@link RecaptureTargetService#markDispatched}
 * wiring on a full {@link FrontLineReinforcementTrigger#check} pass. Fixture
 * mirrors {@link RecaptureTargetServiceTest}.
 */
public class FrontLineReinforcementTriggerTest {

    private static final int W = 20;
    private static final int H = 100;

    private static NavigationGrid openGrid() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }

    private static BiomeMap biomeMap() {
        return new BiomeMap(W, H, TraversalAxis.SOUTH_TO_NORTH, new Random(42));
    }

    private static TacticalNode node(int x, int y) {
        return new TacticalNode(TacticalNode.Kind.HEAVY_TOWER, x, y, x - 1, y - 1, x + 1, y + 1,
                Faction.DEFENDER, 50, 4);
    }

    private static RecaptureTarget targetFor(RecaptureTargetService reg, TacticalNode node) {
        for (RecaptureTarget t : reg.allTargets()) {
            if (t.node == node) return t;
        }
        throw new IllegalStateException("no target for node " + node);
    }

    /** Mans then opens {@code target} and marks its slice contested — the state {@link RecaptureTargetService#eligibleTargets()} requires. */
    private static void makeEligible(RecaptureTargetService reg, RecaptureTarget target) {
        target.manned = true;
        target.open = true;
        reg.setContested(target.slice, true);
    }

    @Test
    public void nearestToDefenderSliceWinsOverFartherSlice() {
        BiomeMap biomes = biomeMap();
        TacticalNode port = node(10, 25);
        TacticalNode fort = node(10, 87);
        BiomeKind ps = biomes.biomeAt(port.anchorX, port.anchorY);
        BiomeKind fs = biomes.biomeAt(fort.anchorX, fort.anchorY);
        assertNotEquals(ps, fs, "precondition: port and fortress anchors sit in distinct slices");

        RecaptureTargetService reg = new RecaptureTargetService(new TacticalMap(List.of(port, fort)), biomes);
        makeEligible(reg, targetFor(reg, port));
        makeEligible(reg, targetFor(reg, fort));

        FrontLineReinforcementTrigger trigger =
                new FrontLineReinforcementTrigger(reg, TraversalAxis.SOUTH_TO_NORTH);
        RecaptureTarget picked = trigger.selectDispatchTarget();

        assertEquals(fort, picked.node, "fortress (nearest-to-defender) slice wins over port");
    }

    @Test
    public void roundRobinsWithinASlice() {
        BiomeMap biomes = biomeMap();
        TacticalNode a = node(8, 90);
        TacticalNode b = node(12, 92);
        BiomeKind sliceA = biomes.biomeAt(a.anchorX, a.anchorY);
        BiomeKind sliceB = biomes.biomeAt(b.anchorX, b.anchorY);
        assertEquals(sliceA, sliceB, "precondition: both anchors sit in the same slice");

        RecaptureTargetService reg = new RecaptureTargetService(new TacticalMap(List.of(a, b)), biomes);
        makeEligible(reg, targetFor(reg, a));
        makeEligible(reg, targetFor(reg, b));

        FrontLineReinforcementTrigger trigger =
                new FrontLineReinforcementTrigger(reg, TraversalAxis.SOUTH_TO_NORTH);

        TacticalNode first = trigger.selectDispatchTarget().node;
        TacticalNode second = trigger.selectDispatchTarget().node;
        TacticalNode third = trigger.selectDispatchTarget().node;

        assertNotEquals(first, second, "round-robin visits the other target next");
        assertEquals(first, third, "round-robin wraps back to the first after two targets");
    }

    @Test
    public void dispatchedAndConcededTargetsAreSkipped() {
        BiomeMap biomes = biomeMap();
        TacticalNode dispatched = node(10, 55);
        TacticalNode concededSliceTarget = node(10, 30);
        BiomeKind heldSlice = biomes.biomeAt(dispatched.anchorX, dispatched.anchorY);
        BiomeKind concededSlice = biomes.biomeAt(concededSliceTarget.anchorX, concededSliceTarget.anchorY);
        assertNotEquals(heldSlice, concededSlice, "precondition: distinct slices");

        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(dispatched, concededSliceTarget)), biomes);

        RecaptureTarget dispatchedTarget = targetFor(reg, dispatched);
        dispatchedTarget.manned = true;
        dispatchedTarget.open = true;
        dispatchedTarget.dispatched = true;
        reg.setContested(heldSlice, true);

        RecaptureTarget concededTarget = targetFor(reg, concededSliceTarget);
        concededTarget.manned = true;
        concededTarget.open = true;
        // concededSlice is left un-contested (default false) — the target
        // stays filtered by RecaptureTargetService.eligibleTargets() regardless of open.

        FrontLineReinforcementTrigger trigger =
                new FrontLineReinforcementTrigger(reg, TraversalAxis.SOUTH_TO_NORTH);

        assertNull(trigger.selectDispatchTarget(),
                "both targets ineligible — one dispatched, the other's slice conceded");
    }

    @Test
    public void checkPostsRequestAndMarksTargetDispatched() {
        NavigationGrid grid = openGrid();
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        BiomeMap biomes = biomeMap();
        TacticalNode fort = node(10, 87);
        RecaptureTargetService reg = new RecaptureTargetService(new TacticalMap(List.of(fort)), biomes);
        makeEligible(reg, targetFor(reg, fort));

        FrontLineReinforcementTrigger trigger =
                new FrontLineReinforcementTrigger(reg, TraversalAxis.SOUTH_TO_NORTH);

        List<ReinforcementRequest> posted = new ArrayList<>();
        trigger.check(sim, posted::add);

        assertEquals(1, posted.size());
        ReinforcementRequest req = posted.get(0);
        assertEquals(fort.anchorX, req.objectiveX, "objective = the recapture target's anchor");
        assertEquals(fort.anchorY, req.objectiveY);
        assertTrue(targetFor(reg, fort).isDispatched(), "check() marks the picked target dispatched");
        assertTrue(reg.eligibleTargets().isEmpty(), "dispatched target drops out of eligibility");
    }

    @Test
    public void rallyRearShiftMovesTowardDefenderRearAndClampsToGridBounds() {
        NavigationGrid grid = new NavigationGrid(W, H);

        int[] southToNorth = FrontLineReinforcementTrigger.rallyRearShift(10, 95, TraversalAxis.SOUTH_TO_NORTH, grid);
        assertEquals(10, southToNorth[0]);
        assertEquals(H - 1, southToNorth[1], "+y shift past the top edge clamps to grid height - 1");

        int[] westToEast = FrontLineReinforcementTrigger.rallyRearShift(5, 5, TraversalAxis.WEST_TO_EAST, grid);
        assertEquals(13, westToEast[0], "WEST_TO_EAST shifts +x toward the defender rear");
        assertEquals(5, westToEast[1]);

        int[] nullAxis = FrontLineReinforcementTrigger.rallyRearShift(5, 5, null, grid);
        assertEquals(5, nullAxis[0]);
        assertEquals(13, nullAxis[1], "null axis defaults to the +y shift");
    }
}
