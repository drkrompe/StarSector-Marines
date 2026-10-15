package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.infantry.HoldPlaceGoal;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.HoldPlaceDecision;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadBeliefs;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveHoldPlaceOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveMoveOrder;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.HeldPlaces;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cursor and the click agree about a held building, on a small arena with one published place. */
class HoldPlaceOrderTest {

    private static final int W = 80;
    private static final int H = 80;
    private static final TacticalNode PLACE = new TacticalNode(TacticalNode.Kind.INNER_POSITION,
            45, 45, 40, 40, 50, 50, Faction.ALLY, HeldPlaces.PRIORITY, 0);

    private BattleSimulation sim;
    private int squadId;
    private Selection selection;

    @BeforeEach
    void setUp() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 10; y <= 70; y++) {
            for (int x = 10; x <= 70; x++) grid.setWalkableFloor(x, y);
        }
        sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setTacticalMap(new TacticalMap(List.of(PLACE)));
        squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        for (int i = 0; i < 3; i++) {
            sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE, 20 + i, 20).squad(squadId));
        }
        sim.advance(1f / 30f);
        selection = new Selection();
        selection.selectSquad(squadId);
    }

    private void click(int x, int y) {
        sim.getSquadMoveOrderService().requestMove(squadId, x, y);
        sim.getSquadMoveOrderSystem().tick(sim);
    }

    @Test
    void aClickInsideAHeldPlaceHoldsItAndTheCursorSaidSo() {
        assertEquals(TacticalOrderIntent.OCCUPY, TacticalOrderIntent.resolve(sim, selection, 42, 48));

        click(42, 48);

        ActiveHoldPlaceOrder order = assertInstanceOf(ActiveHoldPlaceOrder.class,
                sim.getSquadMoveOrderService().activeOrder(squadId));
        assertSame(PLACE, order.targetNode());
        assertEquals(PLACE.anchorX, order.destinationX(), "entered at the interior anchor, not where clicked");
        Squad squad = sim.getSquad(squadId);
        assertTrue(squad.hasPlayerOrder(AssignmentKind.HOLD_NODE));
        assertSame(PLACE, squad.assignedObjective.targetNode());
        assertTrue(HoldPlaceGoal.INSTANCE.relevance(WorldStateBuilder.build(squad, sim), squad, sim) > 0f,
                "the player's hold has a goal that will act on it");
    }

    @Test
    void aPlaceTheSquadBelievesIsOccupiedReadsClearAndIsTheSameHold() {
        int enemy = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        long occupant = sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MILITIA, 41, 41).squad(enemy));
        SquadBeliefs.sighted(sim.getSquad(squadId), occupant, 41, 41, sim.getSimTickIndex());

        TacticalOrderIntent intent = TacticalOrderIntent.resolve(sim, selection, 42, 48);
        assertEquals(TacticalOrderIntent.CLEAR, intent);
        assertTrue(intent.assault() && intent.actionable());
        assertEquals(TacticalOrderIntent.MOVE, TacticalOrderIntent.resolve(sim, selection, 35, 48),
                "outside the place the occupant changes nothing");

        click(42, 48);

        assertInstanceOf(ActiveHoldPlaceOrder.class, sim.getSquadMoveOrderService().activeOrder(squadId));
        assertTrue(sim.getSquad(squadId).hasPlayerOrder(AssignmentKind.HOLD_NODE));
    }

    @Test
    void anOccupantTheSquadHasNotSeenDoesNotShowOnTheCursor() {
        Squad squad = sim.getSquad(squadId);
        assertFalse(squad.hasBelievedContacts());
        assertFalse(HoldPlaceDecision.believedOccupied(squad, PLACE));
        assertEquals(TacticalOrderIntent.OCCUPY, TacticalOrderIntent.resolve(sim, selection, 42, 48));
    }

    @Test
    void aClickOutsideIsAnOrdinaryMove() {
        assertEquals(TacticalOrderIntent.MOVE, TacticalOrderIntent.resolve(sim, selection, 35, 48));

        click(35, 48);

        assertInstanceOf(ActiveMoveOrder.class, sim.getSquadMoveOrderService().activeOrder(squadId));
        assertFalse(sim.getSquad(squadId).hasPlayerOrder(AssignmentKind.HOLD_NODE));
    }

    @Test
    void theSmallerOfTwoOverlappingPlacesIsTheOnePointedAt() {
        TacticalNode room = new TacticalNode(TacticalNode.Kind.INNER_POSITION,
                43, 43, 42, 42, 44, 44, Faction.ALLY, HeldPlaces.PRIORITY, 0);
        TacticalMap map = new TacticalMap(List.of(PLACE, room));
        assertSame(room, HoldPlaceDecision.placeUnderClick(map, 43, 43));
        assertSame(PLACE, HoldPlaceDecision.placeUnderClick(map, 48, 48));
        assertNull(HoldPlaceDecision.placeUnderClick(map, 30, 30));
        assertNull(HoldPlaceDecision.placeUnderClick(null, 43, 43));
    }

    @Test
    void anObjectiveIsNotAHeldPlace() {
        TacticalNode post = new TacticalNode(TacticalNode.Kind.COMMAND_POST,
                45, 45, 40, 40, 50, 50, Faction.DEFENDER, 100, 6);
        assertNull(HoldPlaceDecision.placeUnderClick(new TacticalMap(List.of(post)), 45, 45));
    }
}
