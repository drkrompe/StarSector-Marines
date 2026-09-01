package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.command.BattleResources;
import com.dillon.starsectormarines.battle.command.ResourceType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.model.FrontDepth;
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
 * Slice-3 coverage for {@link FrontLineReinforcementTrigger}: shallowest-band
 * selection, in-band round-robin, the dispatched/conceded
 * eligibility filters (inherited from {@link RecaptureTargetService}), the
 * rally shift toward the objective, and provisional dispatch reservation/release wiring
 * on a full {@link FrontLineReinforcementTrigger#check} pass. Fixture
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

    private static FrontDepth frontDepth() {
        return FrontDepth.fromBiomes(
                new BiomeMap(W, H, TraversalAxis.SOUTH_TO_NORTH, new Random(42)));
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
        reg.setContested(target.band, true);
    }

    @Test
    public void nearestToDefenderSliceWinsOverFartherSlice() {
        FrontDepth front = frontDepth();
        TacticalNode port = node(10, 25);
        TacticalNode fort = node(10, 87);
        int ps = front.bandAt(port.anchorX, port.anchorY);
        int fs = front.bandAt(fort.anchorX, fort.anchorY);
        assertNotEquals(ps, fs, "precondition: port and fortress anchors sit in distinct slices");

        RecaptureTargetService reg = new RecaptureTargetService(new TacticalMap(List.of(port, fort)), front);
        makeEligible(reg, targetFor(reg, port));
        makeEligible(reg, targetFor(reg, fort));

        FrontLineReinforcementTrigger trigger =
                new FrontLineReinforcementTrigger(reg, front);
        RecaptureTarget picked = trigger.selectDispatchTarget();

        assertEquals(fort, picked.node, "fortress (nearest-to-defender) slice wins over port");
    }

    @Test
    public void roundRobinsWithinASlice() {
        FrontDepth front = frontDepth();
        TacticalNode a = node(8, 90);
        TacticalNode b = node(12, 92);
        int sliceA = front.bandAt(a.anchorX, a.anchorY);
        int sliceB = front.bandAt(b.anchorX, b.anchorY);
        assertEquals(sliceA, sliceB, "precondition: both anchors sit in the same slice");

        RecaptureTargetService reg = new RecaptureTargetService(new TacticalMap(List.of(a, b)), front);
        makeEligible(reg, targetFor(reg, a));
        makeEligible(reg, targetFor(reg, b));

        FrontLineReinforcementTrigger trigger =
                new FrontLineReinforcementTrigger(reg, front);

        TacticalNode first = trigger.selectDispatchTarget().node;
        TacticalNode second = trigger.selectDispatchTarget().node;
        TacticalNode third = trigger.selectDispatchTarget().node;

        assertNotEquals(first, second, "round-robin visits the other target next");
        assertEquals(first, third, "round-robin wraps back to the first after two targets");
    }

    @Test
    public void dispatchedAndConcededTargetsAreSkipped() {
        FrontDepth front = frontDepth();
        TacticalNode dispatched = node(10, 55);
        TacticalNode concededSliceTarget = node(10, 30);
        int heldSlice = front.bandAt(dispatched.anchorX, dispatched.anchorY);
        int concededSlice = front.bandAt(concededSliceTarget.anchorX, concededSliceTarget.anchorY);
        assertNotEquals(heldSlice, concededSlice, "precondition: distinct slices");

        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(dispatched, concededSliceTarget)), front);

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
                new FrontLineReinforcementTrigger(reg, front);

        assertNull(trigger.selectDispatchTarget(),
                "both targets ineligible — one dispatched, the other's slice conceded");
    }

    @Test
    public void checkPostsRequestAndMarksTargetDispatched() {
        NavigationGrid grid = openGrid();
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        FrontDepth front = frontDepth();
        TacticalNode fort = node(10, 87);
        RecaptureTargetService reg = new RecaptureTargetService(new TacticalMap(List.of(fort)), front);
        makeEligible(reg, targetFor(reg, fort));

        FrontLineReinforcementTrigger trigger =
                new FrontLineReinforcementTrigger(reg, front);

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
    public void terminalDispatchRejectionImmediatelyReopensReservedTarget() {
        NavigationGrid grid = openGrid();
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        FrontDepth front = frontDepth();
        TacticalNode fort = node(10, 87);
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(fort)), front);
        makeEligible(reg, targetFor(reg, fort));
        ReinforcementService service = new ReinforcementService();
        new FrontLineReinforcementTrigger(reg, front)
                .check(sim, service::post);
        service.addMeans(new ReinforcementMeans() {
            @Override
            public boolean canFulfill(BattleView view,
                                      ReinforcementRequest request) {
                return true;
            }

            @Override
            public float arrivalSeconds(BattleView view,
                                        ReinforcementRequest request) {
                return 1f;
            }

            @Override
            public ReinforcementDispatchResult dispatch(
                    BattleControl control, ReinforcementRequest request) {
                return ReinforcementDispatchResult.REJECTED;
            }
        });
        BattleResources resources = new BattleResources();
        resources.produce(Faction.DEFENDER,
                ResourceType.REINFORCEMENT, 1f);

        new ReinforcementSystem(service, resources).tick(1f, sim);

        assertEquals(1f, resources.getBalance(
                Faction.DEFENDER, ResourceType.REINFORCEMENT), 0.0001f);
        assertTrue(service.isPendingEmpty());
        assertEquals(1, reg.eligibleTargets().size(),
                "terminal rejection releases provisional dispatch immediately");
    }

    /**
     * The rally is placed toward the objective rather than up an axis, and on
     * the stock front those are the same answer where the axis is the whole of
     * the vector — which is what makes the change safe on a map that already
     * worked. Off that line it is the answer the axis could not give: a target
     * deeper than the objective's own centre is shifted <em>back</em> toward
     * it, where the old rule walked it further into the map edge.
     */
    @Test
    public void rallyShiftsTowardTheObjectiveWhicheverSideOfItTheTargetIsOn() {
        FrontDepth front = frontDepth();
        int[] centre = front.objectiveCentre();

        int[] fromPort = FrontLineReinforcementTrigger.rallyRearShift(centre[0], 30, front);
        assertEquals(centre[0], fromPort[0], "the vector is all y, so the step is all y");
        assertEquals(38, fromPort[1], "eight cells toward the fortress end - the old +y shift exactly");

        int[] fromBeyond = FrontLineReinforcementTrigger.rallyRearShift(centre[0], H - 1, front);
        assertEquals(H - 1 - 8, fromBeyond[1],
                "past the objective the step turns round: the rear is a place, not a direction");
    }
}
