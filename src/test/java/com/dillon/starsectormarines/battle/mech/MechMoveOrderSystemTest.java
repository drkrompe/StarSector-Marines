package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.ui.highlight.CellHighlight;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.highlight.MechMoveOrderHighlightPublisher;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechMoveOrderSystemTest {

    @Test
    void commandSnapsBlockedClickToNearestReachableCellWithoutReplacingPlan() {
        BattleSimulation sim = openSimulation(16, 10);
        sim.getGrid().setWalkable(10, 5, false);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long mech = spawnMech(sim, squadId, Faction.MARINE, 3, 5);
        Squad squad = finishSquad(sim, squadId, mech);
        ObjectiveAssignment assignment = ObjectiveAssignment.attackMove(
                squadId, 14, 5);
        squad.assignedObjective = assignment;
        SquadPlan plan = new SquadPlan(List.of(
                new SquadPlan.Step(ExecuteMechDoctrine.INSTANCE)));
        squad.currentPlan = plan;
        squad.currentGoal = MechEliminateEnemiesGoal.INSTANCE;
        sim.setPath(mech, new int[]{3, 5, 8, 5});

        sim.getMechMoveOrderService().requestMove(mech, 10, 5);
        sim.getMechMoveOrderSystem().tick(sim);

        ActiveOrder order = sim.getMechMoveOrderService().activeOrder(mech);
        assertNotNull(order);
        assertEquals(10, order.requestedX());
        assertEquals(5, order.requestedY());
        assertEquals(10, order.destinationX());
        assertEquals(4, order.destinationY(),
                "stable cell order breaks the four-way nearest-cell tie");
        assertTrue(Paths.isEmpty(sim.world().path(mech)));
        assertSame(plan, squad.currentPlan);
        assertSame(MechEliminateEnemiesGoal.INSTANCE, squad.currentGoal);
        assertSame(assignment, squad.assignedObjective);
    }

    @Test
    void activeOrderMovesAndFiresThenHandsBackOnArrival() {
        BattleSimulation sim = openSimulation(20, 12);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long mech = spawnMech(sim, squadId, Faction.MARINE, 3, 4);
        Squad squad = finishSquad(sim, squadId, mech);
        long enemy = sim.spawn(new EntitySpec(
                "visible-target", Faction.DEFENDER, UnitType.MARINE, 3, 1));
        MechLoadoutComponent loadout = sim.world().mechLoadout(mech);
        loadout.torsoAimTargetId = enemy;
        loadout.torsoOnTarget = true;

        sim.getMechMoveOrderService().requestMove(mech, 12, 4);
        sim.getMechMoveOrderSystem().tick(sim);

        assertTrue(sim.getMechMoveOrderSystem().executeIfActive(mech, squad, sim));
        assertEquals(enemy, sim.world().targetId(mech));
        assertFalse(sim.getShotsThisFrame().isEmpty(),
                "the ordinary installed-weapon pass remains live while moving");
        assertEquals(12, Paths.destX(sim.world().path(mech)));
        assertEquals(4, Paths.destY(sim.world().path(mech)));

        sim.world().setPos(mech, 12.5f, 4.5f);
        assertFalse(sim.getMechMoveOrderSystem().executeIfActive(mech, squad, sim));
        assertNull(sim.getMechMoveOrderService().activeOrder(mech));
        assertTrue(Paths.isEmpty(sim.world().path(mech)));
    }

    @Test
    void survivalSuspendsAndWithdrawalCancelsTheMove() {
        BattleSimulation sim = openSimulation(20, 12);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long mech = spawnMech(sim, squadId, Faction.MARINE, 3, 4);
        Squad squad = finishSquad(sim, squadId, mech);
        sim.getMechMoveOrderService().requestMove(mech, 12, 4);
        sim.getMechMoveOrderSystem().tick(sim);

        squad.moraleBroken = true;
        assertFalse(sim.getMechMoveOrderSystem().executeIfActive(mech, squad, sim));
        assertNotNull(sim.getMechMoveOrderService().activeOrder(mech),
                "survival temporarily owns movement without erasing intent");

        squad.moraleBroken = false;
        squad.assignedObjective = ObjectiveAssignment.withdraw(squadId, 0, 4);
        assertFalse(sim.getMechMoveOrderSystem().executeIfActive(mech, squad, sim));
        assertNull(sim.getMechMoveOrderService().activeOrder(mech));
    }

    @Test
    void enemyInfantryRescueAndStaleSelectionsAreIgnored() {
        BattleSimulation sim = openSimulation(20, 12);
        int enemySquadId = sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH);
        long enemy = spawnMech(sim, enemySquadId, Faction.DEFENDER, 12, 5);
        finishSquad(sim, enemySquadId, enemy);

        int rescueSquadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long rescue = spawnMech(sim, rescueSquadId, Faction.MARINE, 4, 5);
        finishSquad(sim, rescueSquadId, rescue).rescuePickupMech = true;
        long infantry = sim.spawn(new EntitySpec(
                "marine", Faction.MARINE, UnitType.MARINE, 3, 3));

        for (long id : new long[]{enemy, rescue, infantry, Long.MAX_VALUE}) {
            sim.getMechMoveOrderService().requestMove(id, 8, 5);
        }
        sim.getMechMoveOrderSystem().tick(sim);

        assertNull(sim.getMechMoveOrderService().activeOrder(enemy));
        assertNull(sim.getMechMoveOrderService().activeOrder(rescue));
        assertNull(sim.getMechMoveOrderService().activeOrder(infantry));
        assertNull(sim.getMechMoveOrderService().activeOrder(Long.MAX_VALUE));
    }

    @Test
    void selectedActiveOrderPublishesItsResolvedDestination() {
        BattleSimulation sim = openSimulation(16, 10);
        sim.getGrid().setWalkable(10, 5, false);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long mech = spawnMech(sim, squadId, Faction.MARINE, 3, 5);
        finishSquad(sim, squadId, mech);
        sim.getMechMoveOrderService().requestMove(mech, 10, 5);
        sim.getMechMoveOrderSystem().tick(sim);
        Selection selection = new Selection();
        selection.selectUnit(squadId, mech);
        HighlightOverlay overlay = new HighlightOverlay();

        MechMoveOrderHighlightPublisher.publish(selection, sim, overlay);

        List<CellHighlight> marks = overlay.source(
                HighlightOverlay.SRC_MECH_MOVE_DESTINATION);
        assertEquals(1, marks.size());
        assertEquals(10, marks.get(0).cellX);
        assertEquals(4, marks.get(0).cellY);

        selection.clear();
        MechMoveOrderHighlightPublisher.publish(selection, sim, overlay);
        assertFalse(overlay.hasSource(
                HighlightOverlay.SRC_MECH_MOVE_DESTINATION));
    }

    private static long spawnMech(BattleSimulation sim, int squadId,
                                  Faction faction, int x, int y) {
        MechVariant variant = MechVariant.BULWARK;
        long mech = sim.spawn(variant.applyTo(new EntitySpec(
                "mech-" + sim.liveUnitCount(), faction,
                UnitType.HEAVY_MECH, x, y).squad(squadId)));
        sim.world().attachMechLoadout(mech,
                variant.createLoadout(MechRole.BALANCED));
        return mech;
    }

    private static Squad finishSquad(BattleSimulation sim, int squadId,
                                     long leader) {
        Squad squad = sim.getSquad(squadId);
        squad.leaderId = leader;
        squad.aliveMembers = 1;
        squad.originalSize = 1;
        squad.centroidX = sim.world().x(leader);
        squad.centroidY = sim.world().y(leader);
        return squad;
    }

    private static BattleSimulation openSimulation(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }
}
