package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a right-click means depends on what is under it.
 *
 * <p>Red Alert 2 settled this grammar long ago and it is worth copying exactly:
 * pointing a squad at a transport is "get in", and pointing a loaded transport
 * at itself is "everybody out". Neither needs a button, because the vehicle is
 * the target and what it does follows from whether anybody is in it.
 */
class ContextualTransportOrderTest {

    private static final int W = 80;
    private static final int H = 80;

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 10; y <= 70; y++) {
            for (int x = 10; x <= 70; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static long apcAt(BattleSimulation sim, float x, float y) {
        long id = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.MARINE,
                VehicleMission.deployed(x, y));
        sim.convoy().body(id).teleport(x, y, 0f);
        return id;
    }

    private static int squadAt(BattleSimulation sim, int x, int y) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        for (int i = 0; i < 3; i++) {
            sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE, x + i, y)
                    .squad(squadId));
        }
        return squadId;
    }

    @Test
    void pointingASquadAtAnApcIsAnOrderToGetIn() {
        BattleSimulation sim = arena();
        long apc = apcAt(sim, 50.5f, 50.5f);
        int squadId = squadAt(sim, 20, 20);

        sim.getSquadMoveOrderService().requestMove(squadId, 50, 50);
        sim.getSquadMoveOrderSystem().tick(sim);

        SquadMoveOrderService.ActiveOrder order =
                sim.getSquadMoveOrderService().activeOrder(squadId);
        assertInstanceOf(SquadMoveOrderService.ActiveMountOrder.class, order,
                "clicking a friendly APC is a ride, not a walk to the ground under it");
        assertEquals(apc, ((SquadMoveOrderService.ActiveMountOrder) order).vehicleId());
    }

    @Test
    void pointingASquadAtOrdinaryGroundIsStillJustAMove() {
        BattleSimulation sim = arena();
        apcAt(sim, 50.5f, 50.5f);
        int squadId = squadAt(sim, 20, 20);

        sim.getSquadMoveOrderService().requestMove(squadId, 35, 35);
        sim.getSquadMoveOrderSystem().tick(sim);

        assertInstanceOf(SquadMoveOrderService.ActiveMoveOrder.class,
                sim.getSquadMoveOrderService().activeOrder(squadId),
                "an APC elsewhere on the map must not capture every click");
    }

    @Test
    void theSquadWalksOverAndGetsIn() {
        BattleSimulation sim = arena();
        long apc = apcAt(sim, 40.5f, 20.5f);
        int squadId = squadAt(sim, 20, 20);
        // A one-sided battle is already decided and stops advancing anything,
        // so the squad would stand still for reasons that have nothing to do
        // with the order. One unreachable enemy keeps the clock running.
        int foe = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE);
        sim.spawn(new EntitySpec("foe", Faction.DEFENDER, UnitType.MARINE, 68, 68)
                .squad(foe));

        sim.getSquadMoveOrderService().requestMove(squadId, 40, 20);
        for (int i = 0; i < 60 * 30; i++) sim.advance(BattleSimulation.TICK_DT);

        assertEquals(3, sim.transport().manifest(apc).size(),
                "they should have walked to the APC and boarded it");
    }

    @Test
    void pointingALoadedApcAtItselfIsEverybodyOut() {
        BattleSimulation sim = arena();
        long apc = apcAt(sim, 40.5f, 40.5f);
        int squadId = squadAt(sim, 40, 40);
        assertEquals(3, sim.transport().mountSquad(apc, squadId), "fixture check: aboard");

        sim.getVehicleMoveOrderService().requestMove(apc, 40, 40);
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.transport().manifest(apc).isEmpty(),
                "right-clicking a loaded transport on itself unloads it");
    }

    @Test
    void pointingAnEmptyApcAtItselfIsStillAMoveOrder() {
        // Nothing to unload: this is an ordinary move whose destination has
        // already been reached. Clearing an earlier refusal proves it resolved.
        BattleSimulation sim = arena();
        long apc = apcAt(sim, 40.5f, 40.5f);
        sim.getVehicleMoveOrderService().refuse(apc, 0, 0,
                VehicleMoveOrderService.Refusal.NO_ROUTE);

        sim.getVehicleMoveOrderService().requestMove(apc, 40, 40);
        sim.advance(BattleSimulation.TICK_DT);

        assertNull(sim.getVehicleMoveOrderService().refusal(apc),
                "the completed self-move clears the previous refusal rather than being ignored");
        assertNull(sim.getVehicleMoveOrderService().activeOrder(apc));
        assertEquals(40.5f, sim.convoy().body(apc).x);
        assertEquals(40.5f, sim.convoy().body(apc).y);
    }
}
