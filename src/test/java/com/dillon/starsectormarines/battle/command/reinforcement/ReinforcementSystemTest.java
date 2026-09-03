package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.BattleResources;
import com.dillon.starsectormarines.battle.command.ResourceType;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReinforcementSystemTest {

    @Test
    void rejectedMeansFallsThroughAndSpendsOneTicketOnCommit() {
        BattleSimulation sim = openSim();
        ReinforcementService service = new ReinforcementService();
        BattleResources resources = funded();
        StubMeans convoy = new StubMeans(ReinforcementDispatchResult.REJECTED);
        StubMeans shuttle = new StubMeans(ReinforcementDispatchResult.COMMITTED);
        service.addMeans(convoy);
        service.addMeans(shuttle);
        service.post(request(false));

        new ReinforcementSystem(service, resources).tick(1f, sim);

        assertEquals(1, convoy.attempts);
        assertEquals(1, shuttle.attempts);
        assertEquals(1f, balance(resources), 0.0001f);
        assertTrue(service.isPendingEmpty());
    }

    @Test
    void retryableMeansRefundsAndRequeuesWithoutTryingFallback() {
        BattleSimulation sim = openSim();
        ReinforcementService service = new ReinforcementService();
        BattleResources resources = funded();
        StubMeans convoy = new StubMeans(ReinforcementDispatchResult.RETRYABLE);
        StubMeans shuttle = new StubMeans(ReinforcementDispatchResult.COMMITTED);
        service.addMeans(convoy);
        service.addMeans(shuttle);
        service.post(request(false));
        ReinforcementSystem system = new ReinforcementSystem(service, resources);

        system.tick(1f, sim);

        assertEquals(1, convoy.attempts);
        assertEquals(0, shuttle.attempts);
        assertEquals(2f, balance(resources), 0.0001f);
        assertFalse(service.isPendingEmpty());

        convoy.result = ReinforcementDispatchResult.COMMITTED;
        system.tick(1f, sim);
        assertEquals(2, convoy.attempts);
        assertEquals(1f, balance(resources), 0.0001f);
        assertTrue(service.isPendingEmpty());
    }

    @Test
    void allRejectedRefundsOrdinaryTicketButPrepaidReserveStaysSpent() {
        BattleSimulation sim = openSim();
        ReinforcementService ordinary = new ReinforcementService();
        BattleResources resources = funded();
        ordinary.addMeans(new StubMeans(ReinforcementDispatchResult.REJECTED));
        ordinary.post(request(false));

        new ReinforcementSystem(ordinary, resources).tick(1f, sim);

        assertEquals(2f, balance(resources), 0.0001f);
        assertTrue(ordinary.isPendingEmpty());

        ReinforcementService prepaid = new ReinforcementService();
        BattleResources empty = new BattleResources();
        prepaid.addMeans(new StubMeans(ReinforcementDispatchResult.REJECTED));
        prepaid.post(request(true));
        new ReinforcementSystem(prepaid, empty).tick(1f, sim);

        assertEquals(0f, balance(empty), 0.0001f);
        assertTrue(prepaid.isPendingEmpty(),
                "a launched prepaid failure remains a sunk commitment");
    }

    @Test
    void theSoonestArrivingMeansGetsTheRequestWhereverItWasRegistered() {
        BattleSimulation sim = openSim();
        ReinforcementService service = new ReinforcementService();
        List<String> tried = new ArrayList<>();
        StubMeans slow = new StubMeans(ReinforcementDispatchResult.COMMITTED, 90f)
                .named("slow", tried);
        StubMeans quick = new StubMeans(ReinforcementDispatchResult.COMMITTED, 20f)
                .named("quick", tried);
        service.addMeans(slow);
        service.addMeans(quick);
        service.post(request(false));

        new ReinforcementSystem(service, funded()).tick(1f, sim);

        assertEquals(List.of("quick"), tried,
                "the request goes to whichever means would answer it soonest");
        assertEquals(0, slow.attempts);
    }

    @Test
    void meansThatWouldArriveTogetherResolveInRegistrationOrder() {
        BattleSimulation sim = openSim();
        ReinforcementService service = new ReinforcementService();
        List<String> tried = new ArrayList<>();
        service.addMeans(new StubMeans(ReinforcementDispatchResult.COMMITTED, 30f)
                .named("first", tried));
        service.addMeans(new StubMeans(ReinforcementDispatchResult.COMMITTED, 30f)
                .named("second", tried));
        service.post(request(false));

        new ReinforcementSystem(service, funded()).tick(1f, sim);

        assertEquals(List.of("first"), tried,
                "a tie is broken by the order the battle installed them, "
                        + "so the same battle dispatches the same way twice");
    }

    @Test
    void aRejectedFirstChoiceFallsThroughToTheNextSoonest() {
        BattleSimulation sim = openSim();
        ReinforcementService service = new ReinforcementService();
        List<String> tried = new ArrayList<>();
        service.addMeans(new StubMeans(ReinforcementDispatchResult.COMMITTED, 60f)
                .named("middling", tried));
        service.addMeans(new StubMeans(ReinforcementDispatchResult.COMMITTED, 95f)
                .named("slowest", tried));
        service.addMeans(new StubMeans(ReinforcementDispatchResult.REJECTED, 15f)
                .named("quickest", tried));
        service.post(request(false));

        new ReinforcementSystem(service, funded()).tick(1f, sim);

        assertEquals(List.of("quickest", "middling"), tried,
                "fallthrough follows arrival order, not registration order");
    }

    @Test
    void anInfeasibleMeansIsNeverAskedWhenItWouldArrive() {
        BattleSimulation sim = openSim();
        ReinforcementService service = new ReinforcementService();
        List<String> tried = new ArrayList<>();
        StubMeans unsupplied = new StubMeans(ReinforcementDispatchResult.COMMITTED, 1f)
                .named("unsupplied", tried);
        unsupplied.feasible = false;
        service.addMeans(unsupplied);
        service.addMeans(new StubMeans(ReinforcementDispatchResult.COMMITTED, 80f)
                .named("only-option", tried));
        service.post(request(false));

        new ReinforcementSystem(service, funded()).tick(1f, sim);

        assertEquals(0, unsupplied.arrivalQueries,
                "a means that cannot deliver is not asked how fast it would");
        assertEquals(List.of("only-option"), tried);
    }

    /**
     * A means whose proof finishes some ticks after the dispatch that started
     * it — the convoy's shape, and the whole reason {@code advance} exists. The
     * request would otherwise wait out the rest of the cadence second whatever
     * moment the proof landed on.
     */
    @Test
    void aMeansThatFinishesPreparingIsDispatchedOnThatTickRatherThanTheNextCadence() {
        BattleSimulation sim = openSim();
        ReinforcementService service = new ReinforcementService();
        BattleResources resources = funded();
        PreparingMeans convoy = new PreparingMeans(3);
        service.addMeans(convoy);
        service.post(request(false));
        ReinforcementSystem system = new ReinforcementSystem(service, resources);

        system.tick(1f, sim);
        assertEquals(1, convoy.attempts, "the first ask starts the preparation");
        assertFalse(service.isPendingEmpty());
        assertEquals(2f, balance(resources), 0.0001f,
                "a retryable attempt refunds its ticket");

        system.tick(0.0166f, sim);
        assertEquals(1, convoy.attempts, "an unfinished proof is not re-asked");
        system.tick(0.0166f, sim);
        assertEquals(1, convoy.attempts);

        system.tick(0.0166f, sim);

        assertEquals(2, convoy.attempts,
                "the tick the proof finishes is the tick the request is offered again");
        assertTrue(service.isPendingEmpty());
        assertEquals(1f, balance(resources), 0.0001f);
    }

    /**
     * The control for the case above: a means that prepares nothing must not
     * acquire an off-cadence dispatch. The cadence is what keeps the trigger
     * walk off the per-frame path, and only a means saying it just finished
     * something may bypass it.
     */
    @Test
    void aMeansThatIsMerelyRetryableIsStillOnlyRetriedOnTheCadence() {
        BattleSimulation sim = openSim();
        ReinforcementService service = new ReinforcementService();
        StubMeans convoy = new StubMeans(ReinforcementDispatchResult.RETRYABLE);
        service.addMeans(convoy);
        service.post(request(false));
        ReinforcementSystem system = new ReinforcementSystem(service, funded());

        system.tick(1f, sim);
        assertEquals(1, convoy.attempts);
        for (int tick = 0; tick < 30; tick++) system.tick(0.0166f, sim);
        assertEquals(1, convoy.attempts,
                "nothing said it had finished, so nothing asked again");
    }

    /** Answers RETRYABLE until {@code advance} has been called enough times, then commits. */
    private static final class PreparingMeans implements ReinforcementMeans {
        private final int ticksToPrepare;
        private int ticksPrepared;
        int attempts;

        PreparingMeans(int ticksToPrepare) { this.ticksToPrepare = ticksToPrepare; }

        @Override
        public boolean advance(float dt, BattleControl sim) {
            if (attempts == 0 || ticksPrepared >= ticksToPrepare) return false;
            return ++ticksPrepared == ticksToPrepare;
        }

        @Override
        public boolean canFulfill(BattleView sim, ReinforcementRequest req) { return true; }

        @Override
        public float arrivalSeconds(BattleView sim, ReinforcementRequest req) { return 10f; }

        @Override
        public ReinforcementDispatchResult dispatch(
                BattleControl sim, ReinforcementRequest req) {
            attempts++;
            return ticksPrepared >= ticksToPrepare
                    ? ReinforcementDispatchResult.COMMITTED
                    : ReinforcementDispatchResult.RETRYABLE;
        }
    }

    private static final class StubMeans implements ReinforcementMeans {
        ReinforcementDispatchResult result;
        /** What this stub claims it would take to arrive. Equal by default, so registration order decides. */
        float arrivalSeconds;
        /** Whether this stub can serve the request at all. */
        boolean feasible = true;
        /** How many times the system asked when this stub would arrive. */
        int arrivalQueries;
        int attempts;
        /** Shared across a test's stubs so the order they were tried in is readable. */
        List<String> trace = new ArrayList<>();
        String name = "stub";

        StubMeans(ReinforcementDispatchResult result) {
            this(result, 10f);
        }

        StubMeans(ReinforcementDispatchResult result, float arrivalSeconds) {
            this.result = result;
            this.arrivalSeconds = arrivalSeconds;
        }

        StubMeans named(String name, List<String> trace) {
            this.name = name;
            this.trace = trace;
            return this;
        }

        @Override
        public boolean canFulfill(BattleView sim, ReinforcementRequest req) {
            return feasible;
        }

        @Override
        public float arrivalSeconds(BattleView sim, ReinforcementRequest req) {
            arrivalQueries++;
            return arrivalSeconds;
        }

        @Override
        public ReinforcementDispatchResult dispatch(
                BattleControl sim, ReinforcementRequest req) {
            attempts++;
            trace.add(name);
            return result;
        }
    }

    private static BattleResources funded() {
        BattleResources resources = new BattleResources();
        resources.produce(Faction.DEFENDER, ResourceType.REINFORCEMENT, 2f);
        return resources;
    }

    private static float balance(BattleResources resources) {
        return resources.getBalance(
                Faction.DEFENDER, ResourceType.REINFORCEMENT);
    }

    private static ReinforcementRequest request(boolean prepaid) {
        return new ReinforcementRequest(Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL,
                5, 5, 5, 5, prepaid);
    }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(12, 12);
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(12, 12));
    }
}
