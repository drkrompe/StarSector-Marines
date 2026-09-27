package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.model.FrontDepth;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.command.ConquestLaneChain;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.TestUnits;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.precinct.LaneRoute;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice-2 coverage for {@link RecaptureTargetService} and its driver
 * {@link RecaptureTargetSystem}: node bucketing by biome (Service), the
 * open/held derivation from squad assignment, the debounced contested-slice
 * (frontline) detection, and the dispatch-dedup / reopen lifecycle (all driven
 * by the System ticking the Service).
 *
 * <p>Bands are derived from {@link FrontDepth#bandAt} rather than hard-coded,
 * so the tests stay robust to the map's per-column boundary jitter — they only
 * assume the three band-center anchors land in three distinct bands.
 */
public class RecaptureTargetServiceTest {

    private static final int W = 20;
    private static final int H = 100;
    private static final float TICK = ReinforcementService.REINFORCEMENT_TICK_PERIOD;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static FrontDepth frontDepth() {
        return FrontDepth.fromBiomes(
                new BiomeMap(W, H, TraversalAxis.SOUTH_TO_NORTH, new Random(42)));
    }

    private static TacticalNode node(TacticalNode.Kind kind, int x, int y, Faction guard, int garrison) {
        return new TacticalNode(kind, x, y, x - 1, y - 1, x + 1, y + 1, guard, 50, garrison);
    }

    /** A defender garrison squad assigned to {@code node} with {@code alive} live members. The leader unit is killed so it never counts toward biome presence. */
    private static Squad garrison(BattleSimulation sim, TacticalNode node, int alive, int original) {
        int sid = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        Squad squad = sim.getSquad(sid);
        squad.assignedNode = node;
        squad.originalSize = original;
        squad.aliveMembers = alive;
        long leader = sim.spawn(new EntitySpec("garr-" + node.anchorX + "-" + node.anchorY,
                Faction.DEFENDER, UnitType.MILITIA, node.anchorX, node.anchorY).squad(sid));
        TestUnits.kill(sim, leader);
        return squad;
    }

    /** A lone alive defender at {@code (x,y)} — contributes biome presence only (no squad/node assignment). */
    private static long presence(BattleSimulation sim, String id, int x, int y) {
        return sim.spawn(new EntitySpec(id, Faction.DEFENDER, UnitType.MILITIA, x, y));
    }

    private static RecaptureTarget targetFor(RecaptureTargetService reg, TacticalNode node) {
        for (RecaptureTarget t : reg.allTargets()) {
            if (t.node == node) return t;
        }
        return null;
    }

    /**
     * A lane straight up the map with a place at y=25 and one at y=55, each
     * claiming a small box of ground around its own anchor.
     */
    private static ConquestLaneChain laneChain(int outerZone, int deepZone) {
        List<LaneRoute.Cell> route = new ArrayList<>();
        for (int y = 0; y < H; y++) route.add(new LaneRoute.Cell(10, y));
        List<LaneRoute.Link> links = List.of(
                new LaneRoute.Link("lane-1-band-3", 3, 10, 25, 25, 6, 20, 14, 30),
                new LaneRoute.Link("lane-1-band-1", 1, 10, 55, 55, 6, 50, 14, 60));
        List<ConquestLaneChain.Compound> compounds = new ArrayList<>();
        compounds.add(new ConquestLaneChain.Compound(outerZone, 10, 25));
        // A negative zone stands for a rung nothing was stamped on.
        if (deepZone >= 0) {
            compounds.add(new ConquestLaneChain.Compound(deepZone, 10, 55));
        }
        return ConquestLaneChain.of(
                List.of(new LaneRoute(0, links, route)), compounds);
    }

    @Test
    public void aPositionOnALaneIsWantedAtTheFrontRatherThanInItsBand() {
        FrontDepth front = frontDepth();
        TacticalNode outer = node(TacticalNode.Kind.GUARDPOST, 10, 25, Faction.DEFENDER, 4);
        TacticalNode deep = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(outer, deep)), front, laneChain(7, 8));
        // Every band contested, so nothing here is being decided by the ring.
        for (int b = 0; b < front.bands(); b++) reg.setContested(b, true);

        RecaptureTarget outerTarget = targetFor(reg, outer);
        RecaptureTarget deepTarget = targetFor(reg, deep);
        assertNotNull(outerTarget);
        assertNotNull(deepTarget);
        assertEquals(0, outerTarget.lane(), "the outer post stands on the lane");
        assertEquals(0, outerTarget.link());
        assertEquals(1, deepTarget.link());

        // Nothing lost yet: the front is the outer place, and it alone is worth
        // holding. The strongpoint behind it is not being attacked.
        reg.setLaneState(0, 0, -1);
        assertTrue(reg.isContested(outerTarget));
        assertFalse(reg.isContested(deepTarget),
                "a place the marines have not reached is not where a relief goes");

        // Outer place taken: it is what to retake, and the strongpoint is now
        // the front, so both are wanted.
        reg.setLaneState(0, 1, 0);
        assertTrue(reg.isContested(outerTarget), "the place just lost is what to retake");
        assertTrue(reg.isContested(deepTarget), "and the next one is what to hold");
    }

    @Test
    public void aPositionOnAPlaceWithNothingOnItKeepsItsBand() {
        FrontDepth front = frontDepth();
        // A rung the map found room for but stamped no compound on: it can
        // never be the lane's front and can never be lost, so the chain has no
        // answer for the guard posts standing on it.
        TacticalNode post = node(TacticalNode.Kind.GUARDPOST, 10, 55, Faction.DEFENDER, 4);
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(post)), front, laneChain(7, -1));
        RecaptureTarget target = targetFor(reg, post);
        assertNotNull(target);
        assertEquals(1, target.link(), "it still belongs to the place it stands on");

        reg.setLaneState(0, 0, -1);
        reg.setContested(target.band, true);
        assertTrue(reg.isContested(target),
                "an empty rung falls back to its ring rather than out of the layer");
        reg.setContested(target.band, false);
        assertFalse(reg.isContested(target));
    }

    @Test
    public void aPositionOnNoLaneKeepsItsBand() {
        FrontDepth front = frontDepth();
        TacticalNode offLane = node(TacticalNode.Kind.GUARDPOST, 2, 90, Faction.DEFENDER, 4);
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(offLane)), front, laneChain(7, 8));
        RecaptureTarget target = targetFor(reg, offLane);
        assertNotNull(target);
        assertEquals(-1, target.lane(), "a settlement post stands on no lane");

        reg.setContested(target.band, false);
        assertFalse(reg.isContested(target));
        reg.setContested(target.band, true);
        assertTrue(reg.isContested(target), "off the chain the ring still decides");
    }

    @Test
    public void bucketsEligibleDefenderNodesByBand() {
        FrontDepth front = frontDepth();
        TacticalNode port = node(TacticalNode.Kind.GUARDPOST, 10, 25, Faction.DEFENDER, 4);
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
        TacticalNode fort = node(TacticalNode.Kind.COMMAND_POST, 10, 87, Faction.DEFENDER, 4);

        int ps = front.bandAt(port.anchorX, port.anchorY);
        int cs = front.bandAt(city.anchorX, city.anchorY);
        int fs = front.bandAt(fort.anchorX, fort.anchorY);
        assertNotEquals(ps, cs, "precondition: port and city anchors are in distinct bands");
        assertNotEquals(cs, fs, "precondition: city and fortress anchors are in distinct bands");
        assertNotEquals(ps, fs, "precondition: port and fortress anchors are in distinct bands");

        TacticalMap tmap = new TacticalMap(List.of(port, city, fort));
        RecaptureTargetService reg = new RecaptureTargetService(tmap, front);

        assertEquals(3, reg.allTargets().size());
        assertEquals(1, reg.targetsInSlice(ps).size());
        assertEquals(1, reg.targetsInSlice(cs).size());
        assertEquals(1, reg.targetsInSlice(fs).size());
        assertEquals(port, reg.targetsInSlice(ps).get(0).node);
    }

    @Test
    public void excludesIneligibleNodes() {
        FrontDepth front = frontDepth();
        TacticalNode garrisoned = node(TacticalNode.Kind.GUARDPOST, 10, 55, Faction.DEFENDER, 4);
        TacticalNode airbase    = node(TacticalNode.Kind.AIRBASE, 10, 56, Faction.DEFENDER, 4);
        TacticalNode noGarrison = node(TacticalNode.Kind.GATE, 10, 57, Faction.DEFENDER, 0);
        TacticalNode marine     = node(TacticalNode.Kind.BEACHHEAD, 10, 10, Faction.MARINE, 4);

        TacticalMap tmap = new TacticalMap(List.of(garrisoned, airbase, noGarrison, marine));
        RecaptureTargetService reg = new RecaptureTargetService(tmap, front);

        assertEquals(1, reg.allTargets().size(), "only the garrisoned defender node is a recapture target");
        assertEquals(garrisoned, reg.allTargets().get(0).node);
    }

    @Test
    public void contestedSeedsThenDebouncesConceding() {
        BattleSimulation sim = openSim();
        FrontDepth front = frontDepth();
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
        int cs = front.bandAt(city.anchorX, city.anchorY);
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(city)), front);
        RecaptureTargetSystem sys = new RecaptureTargetSystem(reg, front);

        long defender = presence(sim, "city-def", 10, 55);
        sys.tick(TICK, sim);
        assertTrue(reg.isContested(cs), "slice seeds contested with a defender present");

        TestUnits.kill(sim, defender);
        sys.tick(TICK, sim);
        assertTrue(reg.isContested(cs), "still contested 1 tick after the last defender died (debounce)");
        sys.tick(TICK, sim);
        assertTrue(reg.isContested(cs), "still contested 2 ticks after");
        sys.tick(TICK, sim);
        assertFalse(reg.isContested(cs), "conceded after PRESENCE_DEBOUNCE_TICKS of absence");
    }

    @Test
    public void contestedDebouncesActivation() {
        BattleSimulation sim = openSim();
        FrontDepth front = frontDepth();
        TacticalNode fort = node(TacticalNode.Kind.COMMAND_POST, 10, 87, Faction.DEFENDER, 4);
        int fs = front.bandAt(fort.anchorX, fort.anchorY);
        int cs = front.bandAt(10, 55);
        assertNotEquals(fs, cs, "precondition: fortress and the seed-defender slice are distinct");
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(fort)), front);
        RecaptureTargetSystem sys = new RecaptureTargetSystem(reg, front);

        // A defender elsewhere on the seed tick so the registry locks its seed
        // with the fortress slice conceded — otherwise the first-ever defender
        // sighting would seed (not debounce) the slice it appears in.
        presence(sim, "city-def", 10, 55);
        sys.tick(TICK, sim);
        assertFalse(reg.isContested(fs), "fortress seeds conceded with no defender present");

        // A defender now enters the fortress slice post-seed — the activation
        // must ramp through the debounce, not flip on the first tick.
        presence(sim, "fort-def", 10, 87);
        sys.tick(TICK, sim);
        assertFalse(reg.isContested(fs), "not yet contested 1 tick after a defender entered");
        sys.tick(TICK, sim);
        assertFalse(reg.isContested(fs), "not yet contested 2 ticks after");
        sys.tick(TICK, sim);
        assertTrue(reg.isContested(fs), "contested after PRESENCE_DEBOUNCE_TICKS of presence");
    }

    @Test
    public void eligibleRequiresOpenAndContested() {
        BattleSimulation sim = openSim();
        FrontDepth front = frontDepth();
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
        TacticalNode port = node(TacticalNode.Kind.GUARDPOST, 10, 25, Faction.DEFENDER, 4);
        int cs = front.bandAt(city.anchorX, city.anchorY);
        int ps = front.bandAt(port.anchorX, port.anchorY);
        assertNotEquals(cs, ps, "precondition: distinct slices");

        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(city, port)), front);
        RecaptureTargetSystem sys = new RecaptureTargetSystem(reg, front);

        // Both nodes manned first (the recapture semantic is "had a garrison,
        // then lost it"), then wiped → both open. City slice has a live
        // defender (contested); port slice has none (conceded).
        Squad citySquad = garrison(sim, city, 3, 4);
        Squad portSquad = garrison(sim, port, 3, 4);
        presence(sim, "city-def", 10, 55);
        sys.tick(TICK, sim);

        citySquad.aliveMembers = 0;
        portSquad.aliveMembers = 0;
        sys.tick(TICK, sim);

        assertTrue(targetFor(reg, city).isOpen());
        assertTrue(targetFor(reg, port).isOpen());
        assertTrue(reg.isContested(cs));
        assertFalse(reg.isContested(ps));

        List<RecaptureTarget> eligible = reg.eligibleTargets();
        assertEquals(1, eligible.size(), "only the open target in a contested slice is eligible");
        assertEquals(city, eligible.get(0).node, "the conceded-slice target is filtered out");
    }

    @Test
    public void dispatchDedupAndReopenOnReplacementWipe() {
        BattleSimulation sim = openSim();
        FrontDepth front = frontDepth();
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(city)), front);
        RecaptureTargetSystem sys = new RecaptureTargetSystem(reg, front);

        Squad original = garrison(sim, city, 3, 4);    // manned first...
        presence(sim, "city-def", 10, 53);             // keeps the slice contested throughout
        sys.tick(TICK, sim);
        original.aliveMembers = 0;                     // ...then wiped → open
        sys.tick(TICK, sim);

        RecaptureTarget t = targetFor(reg, city);
        assertNotNull(t);
        assertEquals(1, reg.eligibleTargets().size(), "open + contested → eligible");

        // Dispatch a reinforcement: dedup suppresses re-dispatch while en route.
        reg.markDispatched(t);
        sys.tick(TICK, sim);
        assertTrue(reg.eligibleTargets().isEmpty(), "dispatched target is no longer eligible");

        // A DISTINCT replacement squad spawns and is assigned to the node at
        // deboard (the real arrival path — not a revived corpse). The original
        // wiped squad stays at 0; aggregate alive on the node is now > 0.
        Squad replacement = garrison(sim, city, 3, 3);
        sys.tick(TICK, sim);
        assertFalse(t.isOpen(), "held once an alive squad is assigned");
        assertFalse(t.isDispatched(), "dispatch flag clears on arrival so a later wipe re-opens");
        assertTrue(reg.eligibleTargets().isEmpty(), "held target is not eligible");

        // Replacement is wiped (the en-route/at-post failure H1 guards) → the
        // node re-opens with no timer because its assigned-alive drops to 0.
        replacement.aliveMembers = 0;
        sys.tick(TICK, sim);
        assertTrue(t.isOpen(), "re-opens when the replacement is wiped");
        assertEquals(1, reg.eligibleTargets().size(), "eligible again");
    }

    @Test
    public void staleReservationCannotReleaseNewerDispatch() {
        FrontDepth front = frontDepth();
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER,
                10, 55, Faction.DEFENDER, 4);
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(city)), front);
        RecaptureTarget target = targetFor(reg, city);
        target.manned = true;
        target.open = true;
        reg.setContested(target.band, true);

        ReinforcementDispatchReservation stale = reg.reserveDispatch(target);
        target.dispatched = false; // models timeout/arrival reopening the target
        ReinforcementDispatchReservation current = reg.reserveDispatch(target);
        target.dispatchAgeTicks = 12;

        stale.release();

        assertTrue(target.isDispatched());
        assertEquals(12, target.dispatchAgeTicks,
                "stale release must not reset the newer dispatch timeout");
        assertTrue(reg.eligibleTargets().isEmpty(),
                "stale request must not release the newer reservation");
        current.release();
        assertEquals(1, reg.eligibleTargets().size());
        assertEquals(0, target.dispatchAgeTicks);
        current.release();
        assertEquals(1, reg.eligibleTargets().size(), "releasing twice is harmless");

        reg.markDispatched(target);
        target.dispatchAgeTicks = 7;
        current.release();
        assertTrue(target.isDispatched(), "an already-released token cannot clear a later dispatch");
        assertEquals(7, target.dispatchAgeTicks);
    }

    @Test
    public void neverMannedTargetIsNotEligible() {
        BattleSimulation sim = openSim();
        FrontDepth front = frontDepth();
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(city)), front);
        RecaptureTargetSystem sys = new RecaptureTargetSystem(reg, front);

        // BattleSetup ran out of defenders: the node has garrisonSize > 0 but
        // no squad was ever assigned. Slice is contested (defenders alive
        // elsewhere in the band) — without the manned latch the trigger would
        // start dispatching to this position from tick 1.
        presence(sim, "city-def", 10, 53);
        sys.tick(TICK, sim);

        assertTrue(targetFor(reg, city).isOpen(), "unassigned node reads open");
        assertTrue(reg.eligibleTargets().isEmpty(),
                "a never-manned position is not a recapture target — nothing was lost there");
    }

    @Test
    public void dispatchTimeoutReopensTargetWithNoArrival() {
        BattleSimulation sim = openSim();
        FrontDepth front = frontDepth();
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(city)), front);
        RecaptureTargetSystem sys = new RecaptureTargetSystem(reg, front);

        Squad original = garrison(sim, city, 3, 4);
        presence(sim, "city-def", 10, 53);
        sys.tick(TICK, sim);
        original.aliveMembers = 0;
        sys.tick(TICK, sim);
        RecaptureTarget t = targetFor(reg, city);
        assertEquals(1, reg.eligibleTargets().size(), "manned-then-wiped → eligible");

        // Dispatch that dies in the delivery pipeline: no squad ever gets
        // assignedNode == city (convoy routing abort, no-means drop, or a
        // fallback reassignment). The dedup must not suppress forever.
        reg.markDispatched(t);
        for (int i = 0; i < RecaptureTargetSystem.DISPATCH_TIMEOUT_TICKS - 1; i++) {
            sys.tick(TICK, sim);
            assertTrue(reg.eligibleTargets().isEmpty(),
                    "still suppressed while the dispatch could legitimately be en route (tick " + i + ")");
        }
        sys.tick(TICK, sim);
        assertFalse(t.isDispatched(), "dispatch flag presumed lost at the timeout");
        assertEquals(1, reg.eligibleTargets().size(), "target re-opens for dispatch");
    }

    @Test
    public void contestedSeedDefersUntilDefendersExist() {
        BattleSimulation sim = openSim();
        FrontDepth front = frontDepth();
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
        int cs = front.bandAt(city.anchorX, city.anchorY);
        RecaptureTargetService reg = new RecaptureTargetService(
                new TacticalMap(List.of(city)), front);
        RecaptureTargetSystem sys = new RecaptureTargetSystem(reg, front);

        // Ticks during sim-init before any defender is placed must not lock the
        // band to "conceded".
        sys.tick(TICK, sim);
        sys.tick(TICK, sim);
        assertFalse(reg.isContested(cs));

        // First defender sighting seeds contested immediately — no debounce ramp.
        presence(sim, "late-def", 10, 55);
        sys.tick(TICK, sim);
        assertTrue(reg.isContested(cs),
                "seeds contested on first defender sighting, not after a debounce ramp");
    }
}
