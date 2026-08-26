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

    private static final class StubMeans implements ReinforcementMeans {
        ReinforcementDispatchResult result;
        int attempts;

        StubMeans(ReinforcementDispatchResult result) {
            this.result = result;
        }

        @Override
        public boolean canFulfill(BattleView sim, ReinforcementRequest req) {
            return true;
        }

        @Override
        public ReinforcementDispatchResult dispatch(
                BattleControl sim, ReinforcementRequest req) {
            attempts++;
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
