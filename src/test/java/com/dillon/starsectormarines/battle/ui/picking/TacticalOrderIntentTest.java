package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cursor and the order have to agree.
 *
 * <p>A hover preview is only worth having if it predicts the click. Most of
 * these therefore assert the pair: what the cursor says, and what actually
 * happens when the same click is made — because the failure mode of a preview
 * is not that it looks wrong, it is that it quietly stops matching.
 */
class TacticalOrderIntentTest {

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

    private static int squadAt(BattleSimulation sim, int x, int y, int size) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        for (int i = 0; i < size; i++) {
            sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE, x + i, y)
                    .squad(squadId));
        }
        return squadId;
    }

    @Test
    void aSquadOverAFriendlyApcReadsGetIn_andTheClickAgrees() {
        BattleSimulation sim = arena();
        apcAt(sim, 50.5f, 50.5f);
        int squadId = squadAt(sim, 20, 20, 3);
        Selection selection = new Selection();
        selection.selectSquad(squadId);

        assertEquals(TacticalOrderIntent.MOUNT,
                TacticalOrderIntent.resolve(sim, selection, 50, 50));

        sim.getSquadMoveOrderService().requestMove(squadId, 50, 50);
        sim.getSquadMoveOrderSystem().tick(sim);
        assertInstanceOf(SquadMoveOrderService.ActiveMountOrder.class,
                sim.getSquadMoveOrderService().activeOrder(squadId),
                "the cursor promised a ride, so the click has to give one");
    }

    @Test
    void aSquadOverOpenGroundReadsMove() {
        BattleSimulation sim = arena();
        apcAt(sim, 50.5f, 50.5f);
        int squadId = squadAt(sim, 20, 20, 3);
        Selection selection = new Selection();
        selection.selectSquad(squadId);

        assertEquals(TacticalOrderIntent.MOVE,
                TacticalOrderIntent.resolve(sim, selection, 35, 35));
    }

    @Test
    void aSquadTooBigForTheApcReadsNoRoom_andIsNotAMountOrder() {
        // An APC seats four. Six is not a ride, and saying so beats a click
        // that looks like it was ignored.
        BattleSimulation sim = arena();
        apcAt(sim, 50.5f, 50.5f);
        int squadId = squadAt(sim, 20, 20, 6);
        Selection selection = new Selection();
        selection.selectSquad(squadId);

        assertEquals(TacticalOrderIntent.MOUNT_BLOCKED,
                TacticalOrderIntent.resolve(sim, selection, 50, 50));
        assertTrue(!TacticalOrderIntent.MOUNT_BLOCKED.actionable(),
                "and it reads as refused rather than as an order");

        sim.getSquadMoveOrderService().requestMove(squadId, 50, 50);
        sim.getSquadMoveOrderSystem().tick(sim);
        assertInstanceOf(SquadMoveOrderService.ActiveMoveOrder.class,
                sim.getSquadMoveOrderService().activeOrder(squadId),
                "the click falls through to an ordinary move, not a mount");
    }

    @Test
    void anEnemyApcIsJustGround() {
        BattleSimulation sim = arena();
        long enemy = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.DEFENDER,
                VehicleMission.deployed(50.5f, 50.5f));
        sim.convoy().body(enemy).teleport(50.5f, 50.5f, 0f);
        int squadId = squadAt(sim, 20, 20, 3);
        Selection selection = new Selection();
        selection.selectSquad(squadId);

        assertEquals(TacticalOrderIntent.MOVE,
                TacticalOrderIntent.resolve(sim, selection, 50, 50),
                "you cannot get into somebody else's APC");
    }

    @Test
    void aLoadedApcOverItselfReadsUnload_andTheClickAgrees() {
        BattleSimulation sim = arena();
        long apc = apcAt(sim, 40.5f, 40.5f);
        int squadId = squadAt(sim, 40, 40, 3);
        assertEquals(3, sim.transport().mountSquad(apc, squadId), "fixture check: aboard");
        Selection selection = new Selection();
        selection.selectVehicle(apc);

        assertEquals(TacticalOrderIntent.DISMOUNT,
                TacticalOrderIntent.resolve(sim, selection, 40, 40));

        sim.getVehicleMoveOrderService().requestMove(apc, 40, 40);
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.transport().manifest(apc).isEmpty(),
                "the cursor said unload, so the click has to unload");
    }

    @Test
    void anEmptyApcOverItselfOffersNothing() {
        // Nothing to unload and nowhere to go: better to say nothing than to
        // caption a click that will be refused.
        BattleSimulation sim = arena();
        long apc = apcAt(sim, 40.5f, 40.5f);
        Selection selection = new Selection();
        selection.selectVehicle(apc);

        assertEquals(TacticalOrderIntent.NONE,
                TacticalOrderIntent.resolve(sim, selection, 40, 40));
    }

    @Test
    void aSelectedApcOverGroundReadsMove() {
        BattleSimulation sim = arena();
        long apc = apcAt(sim, 40.5f, 40.5f);
        Selection selection = new Selection();
        selection.selectVehicle(apc);

        assertEquals(TacticalOrderIntent.MOVE,
                TacticalOrderIntent.resolve(sim, selection, 60, 60));
    }

    @Test
    void nothingSelectedOffersNothing() {
        BattleSimulation sim = arena();
        apcAt(sim, 40.5f, 40.5f);

        assertEquals(TacticalOrderIntent.NONE,
                TacticalOrderIntent.resolve(sim, new Selection(), 40, 40));
    }
}
