package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.BattleResources;
import com.dillon.starsectormarines.battle.command.ResourceType;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.TestUnits;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.bsp.BiomeMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Slices 1/2/4 coverage for {@link CounterattackSystem} (the staged bulge
 * counterattack, {@code roadmap/conquest/stories/biome-counterattack.md}):
 * the muster gate (surplus budget + stable front + a reclaimable conceded
 * slice + at least one deliverable means, all-or-nothing lump earmark), the
 * telegraph/assault-launch abort/refund and its churn-guard cooldown, the
 * massed prepaid wave dispatch (skipping targets already re-manned since the
 * muster snapshot, marking posted targets dispatched), and resolve
 * success/failure/cap (success requires a sustained hold, not a momentary
 * presence flip). Also covers {@link ReinforcementRequest#prepaid} at the
 * {@link ReinforcementSystem} dispatch layer directly. Fixture mirrors
 * {@link RecaptureTargetServiceTest}.
 */
public class CounterattackSystemTest {

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

    private static BiomeMap biomeMap() {
        return new BiomeMap(W, H, TraversalAxis.SOUTH_TO_NORTH, new Random(42));
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

    /** A lone alive defender at {@code (x,y)} — contributes biome presence only (no squad/node assignment). Returns the spawned unit's id. */
    private static long presence(BattleSimulation sim, String id, int x, int y) {
        return sim.spawn(new EntitySpec(id, Faction.DEFENDER, UnitType.MILITIA, x, y));
    }

    private static RecaptureTarget targetFor(RecaptureTargetService reg, TacticalNode node) {
        for (RecaptureTarget t : reg.allTargets()) {
            if (t.node == node) return t;
        }
        throw new IllegalStateException("no target for node " + node);
    }

    /** Drives {@code sys} forward one cadence tick at a time until it reaches {@code target}, failing if it doesn't within {@code maxTicks}. */
    private static void runUntil(CounterattackSystem sys, BattleSimulation sim,
                                 CounterattackSystem.Phase target, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            sys.tick(TICK, sim);
            if (sys.getPhase() == target) return;
        }
        fail("phase did not reach " + target + " within " + maxTicks + " ticks (still " + sys.getPhase() + ")");
    }

    /**
     * Reinforcement means stub that always reports deliverable. The muster
     * gate's deliverability probe ({@code CounterattackSystem#anyMeansCanDeliver})
     * only cares whether *something* is registered that can fulfill a
     * representative request — these tests exercise the phase machine, not
     * means-specific delivery logic, so a trivial always-true stub keeps the
     * fixture minimal.
     */
    private static final class AlwaysDeliverMeans implements ReinforcementMeans {
        @Override
        public boolean canFulfill(BattleView sim, ReinforcementRequest req) { return true; }

        @Override
        public void dispatch(BattleControl sim, ReinforcementRequest req) { }
    }

    /**
     * A conceded CITY slice with one manned+wiped target, and a contested
     * FORTRESS_DISTRICT slice (lone live presence, never assigned to a
     * node) — the minimal "stable front with a reclaimable rear" shape the
     * muster gate wants. Returned via the out-params so callers can extend
     * the map with extra nodes before ticking.
     */
    private static final class Harness {
        final BattleSimulation sim;
        final BiomeMap biomes;
        final TacticalNode fort;
        final TacticalNode city;
        final RecaptureTargetService targets;
        final RecaptureTargetSystem recaptureSys;
        final ReinforcementService reinforcement;
        final BattleResources resources;
        final CounterattackSystem sys;
        final BiomeKind fortSlice;
        final BiomeKind citySlice;

        Harness() {
            sim = openSim();
            biomes = biomeMap();
            fort = node(TacticalNode.Kind.COMMAND_POST, 10, 87, Faction.DEFENDER, 4);
            city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
            fortSlice = biomes.biomeAt(fort.anchorX, fort.anchorY);
            citySlice = biomes.biomeAt(city.anchorX, city.anchorY);
            assertNotEquals(fortSlice, citySlice, "precondition: distinct slices");

            targets = new RecaptureTargetService(new TacticalMap(List.of(fort, city)), biomes);
            recaptureSys = new RecaptureTargetSystem(targets, biomes);
            reinforcement = new ReinforcementService();
            reinforcement.addMeans(new AlwaysDeliverMeans());
            resources = new BattleResources();
            sys = new CounterattackSystem(targets, reinforcement, resources, TraversalAxis.SOUTH_TO_NORTH);

            Squad citySquad = garrison(sim, city, 3, 4);
            presence(sim, "fort-def", fort.anchorX, fort.anchorY);
            recaptureSys.tick(TICK, sim);
            citySquad.aliveMembers = 0;
            for (int i = 0; i < 5; i++) recaptureSys.tick(TICK, sim);

            assertTrue(targets.eligibleTargets().isEmpty(), "precondition: nothing eligible on the contested front");
            assertTrue(targets.isContested(fortSlice), "precondition: fortress stays contested");
            assertFalse(targets.isContested(citySlice), "precondition: city slice conceded");
        }

        float floor() {
            return (CounterattackSystem.BURST_TICKETS + CounterattackSystem.SURPLUS_FLOOR_TICKETS) * resources.reinforcementCost();
        }

        void fund(float surplus) {
            resources.produce(Faction.DEFENDER, ResourceType.REINFORCEMENT, floor() + surplus);
        }
    }

    @Test
    public void musterFiresOnConcededSliceWithSurplus() {
        Harness h = new Harness();
        h.fund(1f);
        float before = h.resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT);

        h.sys.tick(TICK, h.sim);

        assertEquals(CounterattackSystem.Phase.TELEGRAPH, h.sys.getPhase());
        assertEquals(h.citySlice, h.sys.getBulgeSlice(), "reclaims the conceded (not the contested) slice");
        assertEquals(before - CounterattackSystem.BURST_TICKETS * h.resources.reinforcementCost(),
                h.resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT), 0.0001f,
                "muster debits exactly the lump");
    }

    @Test
    public void noMusterWhenBudgetShort() {
        Harness h = new Harness();
        float short_ = h.floor() - 0.5f;
        h.resources.produce(Faction.DEFENDER, ResourceType.REINFORCEMENT, short_);

        h.sys.tick(TICK, h.sim);

        assertEquals(CounterattackSystem.Phase.IDLE, h.sys.getPhase());
        assertEquals(short_, h.resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT), 0.0001f,
                "no debit attempted when short of the surplus floor");
    }

    @Test
    public void noMusterWhileEligibleTargetsExist() {
        BattleSimulation sim = openSim();
        BiomeMap biomes = biomeMap();
        TacticalNode fort = node(TacticalNode.Kind.COMMAND_POST, 10, 87, Faction.DEFENDER, 4);
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);
        // A second fortress-slice node, manned then wiped, with its own
        // presence keeping the slice contested — open + manned + contested +
        // undispatched, i.e. eligible on the ordinary frontline filter.
        TacticalNode fortOpen = node(TacticalNode.Kind.MG_NEST, 12, 90, Faction.DEFENDER, 4);

        RecaptureTargetService targets = new RecaptureTargetService(
                new TacticalMap(List.of(fort, city, fortOpen)), biomes);
        RecaptureTargetSystem recaptureSys = new RecaptureTargetSystem(targets, biomes);
        ReinforcementService reinforcement = new ReinforcementService();
        BattleResources resources = new BattleResources();
        CounterattackSystem sys = new CounterattackSystem(targets, reinforcement, resources, TraversalAxis.SOUTH_TO_NORTH);

        Squad citySquad = garrison(sim, city, 3, 4);
        Squad fortOpenSquad = garrison(sim, fortOpen, 3, 4);
        presence(sim, "fort-def", fort.anchorX, fort.anchorY);
        recaptureSys.tick(TICK, sim);
        citySquad.aliveMembers = 0;
        fortOpenSquad.aliveMembers = 0;
        for (int i = 0; i < 5; i++) recaptureSys.tick(TICK, sim);

        assertEquals(1, targets.eligibleTargets().size(), "precondition: fortress target still eligible");

        resources.produce(Faction.DEFENDER, ResourceType.REINFORCEMENT,
                (CounterattackSystem.BURST_TICKETS + CounterattackSystem.SURPLUS_FLOOR_TICKETS) * resources.reinforcementCost() + 5f);

        sys.tick(TICK, sim);

        assertEquals(CounterattackSystem.Phase.IDLE, sys.getPhase(), "eligible frontline targets block muster");
    }

    @Test
    public void noMusterWithNoMannedTargetsInConcededSlice() {
        BattleSimulation sim = openSim();
        BiomeMap biomes = biomeMap();
        TacticalNode fort = node(TacticalNode.Kind.COMMAND_POST, 10, 87, Faction.DEFENDER, 4);
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4); // never garrisoned
        BiomeKind cs = biomes.biomeAt(city.anchorX, city.anchorY);

        RecaptureTargetService targets = new RecaptureTargetService(new TacticalMap(List.of(fort, city)), biomes);
        RecaptureTargetSystem recaptureSys = new RecaptureTargetSystem(targets, biomes);
        ReinforcementService reinforcement = new ReinforcementService();
        BattleResources resources = new BattleResources();
        CounterattackSystem sys = new CounterattackSystem(targets, reinforcement, resources, TraversalAxis.SOUTH_TO_NORTH);

        presence(sim, "fort-def", fort.anchorX, fort.anchorY);
        for (int i = 0; i < 5; i++) recaptureSys.tick(TICK, sim);

        assertTrue(targets.eligibleTargets().isEmpty());
        assertFalse(targets.isContested(cs), "precondition: city slice conceded");
        assertFalse(targetFor(targets, city).manned, "precondition: city node was never manned");

        resources.produce(Faction.DEFENDER, ResourceType.REINFORCEMENT,
                (CounterattackSystem.BURST_TICKETS + CounterattackSystem.SURPLUS_FLOOR_TICKETS) * resources.reinforcementCost() + 5f);

        sys.tick(TICK, sim);

        assertEquals(CounterattackSystem.Phase.IDLE, sys.getPhase(),
                "no manned target anywhere along the reclaim scan — nothing to reclaim");
    }

    @Test
    public void musterBlockedWhenNoMeansCanDeliver() {
        BattleSimulation sim = openSim();
        BiomeMap biomes = biomeMap();
        TacticalNode fort = node(TacticalNode.Kind.COMMAND_POST, 10, 87, Faction.DEFENDER, 4);
        TacticalNode city = node(TacticalNode.Kind.HEAVY_TOWER, 10, 55, Faction.DEFENDER, 4);

        RecaptureTargetService targets = new RecaptureTargetService(new TacticalMap(List.of(fort, city)), biomes);
        RecaptureTargetSystem recaptureSys = new RecaptureTargetSystem(targets, biomes);
        ReinforcementService reinforcement = new ReinforcementService(); // deliberately no means registered
        BattleResources resources = new BattleResources();
        CounterattackSystem sys = new CounterattackSystem(targets, reinforcement, resources, TraversalAxis.SOUTH_TO_NORTH);

        Squad citySquad = garrison(sim, city, 3, 4);
        presence(sim, "fort-def", fort.anchorX, fort.anchorY);
        recaptureSys.tick(TICK, sim);
        citySquad.aliveMembers = 0;
        for (int i = 0; i < 5; i++) recaptureSys.tick(TICK, sim);

        assertTrue(targets.eligibleTargets().isEmpty(), "precondition: stable/overflow front");

        resources.produce(Faction.DEFENDER, ResourceType.REINFORCEMENT,
                (CounterattackSystem.BURST_TICKETS + CounterattackSystem.SURPLUS_FLOOR_TICKETS) * resources.reinforcementCost() + 5f);
        float balanceBefore = resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT);

        sys.tick(TICK, sim);

        assertEquals(CounterattackSystem.Phase.IDLE, sys.getPhase(),
                "no registered means can deliver — muster is blocked instead of burning the earmark for zero squads");
        assertEquals(balanceBefore, resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT), 0.0001f,
                "no debit attempted");
    }

    @Test
    public void telegraphAbortsAndRefundsWhenSliceRecontested() {
        Harness h = new Harness();
        h.fund(3f);
        h.sys.tick(TICK, h.sim);
        assertEquals(CounterattackSystem.Phase.TELEGRAPH, h.sys.getPhase(), "precondition: bulge mustered");
        float afterMuster = h.resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT);

        // Defenders naturally re-establish the conceded slice mid-telegraph.
        long returning = presence(h.sim, "city-return", h.city.anchorX, h.city.anchorY);
        for (int i = 0; i < RecaptureTargetSystem.PRESENCE_DEBOUNCE_TICKS; i++) {
            h.recaptureSys.tick(TICK, h.sim);
        }
        assertTrue(h.targets.isContested(h.citySlice), "precondition: slice re-contested");

        h.sys.tick(TICK, h.sim);

        assertEquals(CounterattackSystem.Phase.COOLDOWN, h.sys.getPhase(),
                "abort spaces the next muster via a short cooldown, not an immediate return to IDLE");
        assertEquals(afterMuster + CounterattackSystem.BURST_TICKETS * h.resources.reinforcementCost(),
                h.resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT), 0.0001f,
                "full lump refunded on natural abort");

        // Churn guard: the straggler leaves and the slice concedes again —
        // muster conditions are satisfied once more — but the abort cooldown
        // still blocks an immediate re-muster.
        TestUnits.kill(h.sim, returning);
        for (int i = 0; i < RecaptureTargetSystem.PRESENCE_DEBOUNCE_TICKS; i++) {
            h.recaptureSys.tick(TICK, h.sim);
        }
        assertFalse(h.targets.isContested(h.citySlice), "precondition: slice conceded again");

        h.sys.tick(TICK, h.sim);
        assertEquals(CounterattackSystem.Phase.COOLDOWN, h.sys.getPhase(),
                "abort cooldown blocks immediate re-muster churn even though conditions are met again");

        int abortCooldownTicks = Math.round(CounterattackSystem.ABORT_COOLDOWN_SEC / TICK);
        runUntil(h.sys, h.sim, CounterattackSystem.Phase.IDLE, abortCooldownTicks);
        assertNull(h.sys.getBulgeSlice());

        h.sys.tick(TICK, h.sim);
        assertEquals(CounterattackSystem.Phase.TELEGRAPH, h.sys.getPhase(),
                "re-musters once the abort cooldown has elapsed and conditions are met again");
    }

    @Test
    public void assaultAbortsAndRefundsWhenSliceRecontestsAtLaunch() {
        Harness h = new Harness();
        h.fund(3f);

        runUntil(h.sys, h.sim, CounterattackSystem.Phase.ASSAULT, 25);
        float duringAssault = h.resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT);

        // Defenders re-establish the slice naturally in the one-tick gap
        // between the last TELEGRAPH check and this ASSAULT tick.
        presence(h.sim, "city-return", h.city.anchorX, h.city.anchorY);
        for (int i = 0; i < RecaptureTargetSystem.PRESENCE_DEBOUNCE_TICKS; i++) {
            h.recaptureSys.tick(TICK, h.sim);
        }
        assertTrue(h.targets.isContested(h.citySlice), "precondition: slice re-contested right at launch");

        h.sys.tick(TICK, h.sim);

        assertEquals(CounterattackSystem.Phase.COOLDOWN, h.sys.getPhase(),
                "assault-launch abort — refunded, short cooldown, not counted against the cap");
        assertEquals(duringAssault + CounterattackSystem.BURST_TICKETS * h.resources.reinforcementCost(),
                h.resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT), 0.0001f,
                "full lump refunded — no wave was actually launched");
        assertTrue(h.reinforcement.drainPending().isEmpty(), "no wave requests posted against an already re-held slice");
    }

    @Test
    public void assaultPostsPrepaidWaveRoundRobinOverTargets() {
        BattleSimulation sim = openSim();
        BiomeMap biomes = biomeMap();
        TacticalNode fort = node(TacticalNode.Kind.COMMAND_POST, 10, 87, Faction.DEFENDER, 4);
        TacticalNode cityA = node(TacticalNode.Kind.HEAVY_TOWER, 8, 55, Faction.DEFENDER, 4);
        TacticalNode cityB = node(TacticalNode.Kind.HEAVY_TOWER, 12, 55, Faction.DEFENDER, 4);
        BiomeKind csA = biomes.biomeAt(cityA.anchorX, cityA.anchorY);
        BiomeKind csB = biomes.biomeAt(cityB.anchorX, cityB.anchorY);
        assertEquals(csA, csB, "precondition: both city nodes share a slice");

        RecaptureTargetService targets = new RecaptureTargetService(
                new TacticalMap(List.of(fort, cityA, cityB)), biomes);
        RecaptureTargetSystem recaptureSys = new RecaptureTargetSystem(targets, biomes);
        ReinforcementService reinforcement = new ReinforcementService();
        reinforcement.addMeans(new AlwaysDeliverMeans());
        BattleResources resources = new BattleResources();
        CounterattackSystem sys = new CounterattackSystem(targets, reinforcement, resources, TraversalAxis.SOUTH_TO_NORTH);

        Squad squadA = garrison(sim, cityA, 3, 4);
        Squad squadB = garrison(sim, cityB, 3, 4);
        presence(sim, "fort-def", fort.anchorX, fort.anchorY);
        recaptureSys.tick(TICK, sim);
        squadA.aliveMembers = 0;
        squadB.aliveMembers = 0;
        for (int i = 0; i < 5; i++) recaptureSys.tick(TICK, sim);

        resources.produce(Faction.DEFENDER, ResourceType.REINFORCEMENT,
                (CounterattackSystem.BURST_TICKETS + CounterattackSystem.SURPLUS_FLOOR_TICKETS) * resources.reinforcementCost() + 10f);

        runUntil(sys, sim, CounterattackSystem.Phase.RESOLVE, 60);

        List<ReinforcementRequest> posted = reinforcement.drainPending();
        assertEquals(CounterattackSystem.BURST_TICKETS, posted.size(), "posts exactly the burst size");
        for (int i = 0; i < posted.size(); i++) {
            ReinforcementRequest req = posted.get(i);
            assertTrue(req.prepaid, "wave requests are prepaid");
            assertEquals(ReinforcementRequest.Reason.COUNTERATTACK, req.reason);
            TacticalNode expected = (i % 2 == 0) ? cityA : cityB;
            assertEquals(expected.anchorX, req.objectiveX, "round-robins the objective over the snapshot targets");
            assertEquals(expected.anchorY, req.objectiveY);
        }
        assertTrue(targetFor(targets, cityA).isDispatched(),
                "posted target marked dispatched — prevents the frontline trigger double-dispatching once contested");
        assertTrue(targetFor(targets, cityB).isDispatched(),
                "posted target marked dispatched — prevents the frontline trigger double-dispatching once contested");
    }

    @Test
    public void assaultSkipsTargetAlreadyReMannedSinceMusterSnapshot() {
        BattleSimulation sim = openSim();
        BiomeMap biomes = biomeMap();
        TacticalNode fort = node(TacticalNode.Kind.COMMAND_POST, 10, 87, Faction.DEFENDER, 4);
        TacticalNode cityA = node(TacticalNode.Kind.HEAVY_TOWER, 8, 55, Faction.DEFENDER, 4);
        TacticalNode cityB = node(TacticalNode.Kind.HEAVY_TOWER, 12, 55, Faction.DEFENDER, 4);
        BiomeKind citySlice = biomes.biomeAt(cityA.anchorX, cityA.anchorY);
        assertEquals(citySlice, biomes.biomeAt(cityB.anchorX, cityB.anchorY), "precondition: shared slice");

        RecaptureTargetService targets = new RecaptureTargetService(
                new TacticalMap(List.of(fort, cityA, cityB)), biomes);
        RecaptureTargetSystem recaptureSys = new RecaptureTargetSystem(targets, biomes);
        ReinforcementService reinforcement = new ReinforcementService();
        reinforcement.addMeans(new AlwaysDeliverMeans());
        BattleResources resources = new BattleResources();
        CounterattackSystem sys = new CounterattackSystem(targets, reinforcement, resources, TraversalAxis.SOUTH_TO_NORTH);

        Squad squadA = garrison(sim, cityA, 3, 4);
        Squad squadB = garrison(sim, cityB, 3, 4);
        presence(sim, "fort-def", fort.anchorX, fort.anchorY);
        recaptureSys.tick(TICK, sim);
        squadA.aliveMembers = 0;
        squadB.aliveMembers = 0;
        for (int i = 0; i < 5; i++) recaptureSys.tick(TICK, sim);

        resources.produce(Faction.DEFENDER, ResourceType.REINFORCEMENT,
                (CounterattackSystem.BURST_TICKETS + CounterattackSystem.SURPLUS_FLOOR_TICKETS) * resources.reinforcementCost() + 10f);

        runUntil(sys, sim, CounterattackSystem.Phase.TELEGRAPH, 5);

        // A frontline reinforcement already en route to cityA before the
        // slice conceded deboards mid-telegraph: assignedNode flips cityA
        // held, but its rally lands rearward (outside the biome slice), so
        // the slice itself doesn't read contested and the TELEGRAPH abort
        // check never fires.
        int returnSid = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        Squad returning = sim.getSquad(returnSid);
        returning.assignedNode = cityA;
        returning.aliveMembers = 3;
        sim.spawn(new EntitySpec("return-a", Faction.DEFENDER, UnitType.MILITIA,
                fort.anchorX, fort.anchorY).squad(returnSid));
        recaptureSys.tick(TICK, sim);

        assertFalse(targetFor(targets, cityA).isOpen(), "precondition: cityA re-manned by assignment");
        assertFalse(targets.isContested(citySlice),
                "precondition: slice itself still reads conceded — the squad isn't physically inside it");

        runUntil(sys, sim, CounterattackSystem.Phase.RESOLVE, 25);

        List<ReinforcementRequest> posted = reinforcement.drainPending();
        assertFalse(posted.isEmpty(), "still dispatches against the target that's genuinely open");
        for (ReinforcementRequest req : posted) {
            assertFalse(req.objectiveX == cityA.anchorX && req.objectiveY == cityA.anchorY,
                    "must not post a duplicate wave request at a target already re-manned since the muster snapshot");
        }
    }

    @Test
    public void resolveSuccessWhenSliceContestedAgain() {
        Harness h = new Harness();
        h.fund(5f);
        runUntil(h.sys, h.sim, CounterattackSystem.Phase.RESOLVE, 60);

        presence(h.sim, "retake", h.city.anchorX, h.city.anchorY);
        for (int i = 0; i < RecaptureTargetSystem.PRESENCE_DEBOUNCE_TICKS; i++) {
            h.recaptureSys.tick(TICK, h.sim);
        }
        assertTrue(h.targets.isContested(h.citySlice), "precondition: slice re-contested");

        // A single flip isn't enough — success requires a sustained hold.
        for (int i = 0; i < CounterattackSystem.SUCCESS_HOLD_TICKS - 1; i++) {
            h.sys.tick(TICK, h.sim);
            assertEquals(CounterattackSystem.Phase.RESOLVE, h.sys.getPhase(), "hold streak not yet confirmed");
        }

        h.sys.tick(TICK, h.sim);

        assertEquals(CounterattackSystem.Phase.COOLDOWN, h.sys.getPhase(), "sustained hold moves to cooldown");
    }

    @Test
    public void resolveDoesNotSucceedOnTransientContestedFlip() {
        Harness h = new Harness();
        h.fund(5f);
        runUntil(h.sys, h.sim, CounterattackSystem.Phase.RESOLVE, 60);

        // The wave itself walks into the slice and briefly flips it
        // contested — mere presence, not a defeated/held position.
        long waveUnit = presence(h.sim, "wave-unit", h.city.anchorX, h.city.anchorY);
        for (int i = 0; i < RecaptureTargetSystem.PRESENCE_DEBOUNCE_TICKS; i++) {
            h.recaptureSys.tick(TICK, h.sim);
        }
        assertTrue(h.targets.isContested(h.citySlice), "precondition: slice reads contested from wave presence alone");

        for (int i = 0; i < CounterattackSystem.SUCCESS_HOLD_TICKS - 2; i++) {
            h.sys.tick(TICK, h.sim);
        }
        assertEquals(CounterattackSystem.Phase.RESOLVE, h.sys.getPhase(), "brief presence alone hasn't confirmed a hold yet");

        // The wave gets wiped before the hold window completes.
        TestUnits.kill(h.sim, waveUnit);
        for (int i = 0; i < RecaptureTargetSystem.PRESENCE_DEBOUNCE_TICKS; i++) {
            h.recaptureSys.tick(TICK, h.sim);
        }
        assertFalse(h.targets.isContested(h.citySlice), "precondition: slice reverts to conceded once the wave is wiped");

        h.sys.tick(TICK, h.sim);

        assertEquals(CounterattackSystem.Phase.RESOLVE, h.sys.getPhase(),
                "a wave wiped before the hold window completes must not have registered as success");
    }

    @Test
    public void resolveFailureOnWindowExpiry() {
        Harness h = new Harness();
        h.fund(5f);
        runUntil(h.sys, h.sim, CounterattackSystem.Phase.RESOLVE, 60);

        int windowTicks = Math.round(CounterattackSystem.RESOLVE_WINDOW_SEC / TICK);
        for (int i = 0; i < windowTicks; i++) {
            h.sys.tick(TICK, h.sim);
        }

        assertEquals(CounterattackSystem.Phase.COOLDOWN, h.sys.getPhase(), "unresolved wave times out to cooldown");
    }

    @Test
    public void maxBulgesCapStopsMuster() {
        Harness h = new Harness();

        for (int cycle = 0; cycle < CounterattackSystem.MAX_BULGES_PER_BATTLE; cycle++) {
            h.fund(5f);
            runUntil(h.sys, h.sim, CounterattackSystem.Phase.TELEGRAPH, 5);
            runUntil(h.sys, h.sim, CounterattackSystem.Phase.RESOLVE, 30);
            runUntil(h.sys, h.sim, CounterattackSystem.Phase.COOLDOWN, 130);
            runUntil(h.sys, h.sim, CounterattackSystem.Phase.IDLE, 250);
            h.reinforcement.drainPending();
        }

        h.fund(5f);
        float balanceBefore = h.resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT);

        h.sys.tick(TICK, h.sim);

        assertEquals(CounterattackSystem.Phase.IDLE, h.sys.getPhase(), "cap reached — no third bulge");
        assertEquals(balanceBefore, h.resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT), 0.0001f,
                "no debit attempted once the cap blocks muster");
    }

    @Test
    public void prepaidRequestSkipsDebit() {
        BattleSimulation sim = openSim();
        ReinforcementService service = new ReinforcementService();
        BattleResources resources = new BattleResources(); // zero balance throughout
        ReinforcementSystem reinforcementSystem = new ReinforcementSystem(service, resources);

        boolean[] dispatched = {false};
        service.addMeans(new ReinforcementMeans() {
            @Override
            public boolean canFulfill(BattleView sim, ReinforcementRequest req) {
                return true;
            }

            @Override
            public void dispatch(BattleControl sim, ReinforcementRequest req) {
                dispatched[0] = true;
            }
        });
        service.post(new ReinforcementRequest(Faction.DEFENDER, ReinforcementRequest.Reason.COUNTERATTACK,
                ReinforcementRequest.Strength.SMALL, 5, 5, 5, 5, true));

        reinforcementSystem.tick(TICK, sim);

        assertTrue(dispatched[0], "a prepaid request dispatches even with zero balance");
        assertEquals(0f, resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT), 0.0001f,
                "dispatching a prepaid request must not debit");
    }
}
