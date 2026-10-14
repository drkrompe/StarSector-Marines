package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.DigInSystem;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveDefendAreaOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveMoveOrder;
import com.dillon.starsectormarines.battle.ui.highlight.FightingPositionHighlightPublisher;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cursor, the click and the overlay agree about a trench, on a small arena with one hand-laid trench. */
class OccupyOrderTest {

    private static final int W = 80;
    private static final int H = 80;
    private static final int TRENCH_Y = 40;

    private boolean previouslyEnabled;
    private BattleSimulation sim;
    private int squadId;
    private Selection selection;

    @BeforeEach
    void setUp() {
        previouslyEnabled = DigInSystem.isEnabled();
        DigInSystem.setEnabledForEvidence(true);
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 10; y <= 70; y++) {
            for (int x = 10; x <= 70; x++) grid.setWalkableFloor(x, y);
        }
        sim = new BattleSimulation(grid, new CellTopology(W, H));
        DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                1, 1, null, 0, null, FightingPositionRole.TRENCH);
        for (int x = 30; x <= 41; x++) sim.addDoodad(new Doodad(x, TRENCH_Y, def));
        squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        for (int i = 0; i < 3; i++) {
            sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE, 20 + i, 20).squad(squadId));
        }
        sim.advance(1f / 30f);   // settles the squads' live-member counts
        selection = new Selection();
        selection.selectSquad(squadId);
    }

    @AfterEach
    void tearDown() {
        DigInSystem.setEnabledForEvidence(previouslyEnabled);
    }

    private void click(int x, int y) {
        sim.getSquadMoveOrderService().requestMove(squadId, x, y);
        sim.getSquadMoveOrderSystem().tick(sim);
    }

    @Test
    void aClickOnTheTrenchOccupiesItAndTheCursorSaidSo() {
        assertEquals(1, sim.fightingPositions().runCount());
        assertEquals(TacticalOrderIntent.OCCUPY, TacticalOrderIntent.resolve(sim, selection, 35, TRENCH_Y));

        click(35, TRENCH_Y);

        ActiveDefendAreaOrder order = assertInstanceOf(ActiveDefendAreaOrder.class,
                sim.getSquadMoveOrderService().activeOrder(squadId));
        assertTrue(order.isOccupy());
        assertEquals(sim.fightingPositions().runIdAt(35, TRENCH_Y), order.occupyRunId());
        assertEquals(35, order.destinationX());
        assertEquals(TRENCH_Y, order.destinationY());
        assertEquals(sim.fightingPositions().runIdAt(35, TRENCH_Y), sim.occupyRunOf(squadId));
    }

    @Test
    void aClickBesideTheTrenchIsAnOrdinaryMove() {
        assertEquals(TacticalOrderIntent.MOVE, TacticalOrderIntent.resolve(sim, selection, 35, TRENCH_Y + 3));

        click(35, TRENCH_Y + 3);

        assertInstanceOf(ActiveMoveOrder.class, sim.getSquadMoveOrderService().activeOrder(squadId));
        assertEquals(-1, sim.occupyRunOf(squadId));
    }

    @Test
    void withFightingPositionsOffTheTrenchIsOrdinaryGround() {
        DigInSystem.setEnabledForEvidence(false);
        assertEquals(TacticalOrderIntent.MOVE, TacticalOrderIntent.resolve(sim, selection, 35, TRENCH_Y));

        click(35, TRENCH_Y);

        assertInstanceOf(ActiveMoveOrder.class, sim.getSquadMoveOrderService().activeOrder(squadId));
    }

    @Test
    void anExplicitDefendAreaOnTheTrenchIsNotAnOccupyOrder() {
        sim.getSquadMoveOrderService().requestDefendArea(squadId, 35, TRENCH_Y);
        sim.getSquadMoveOrderSystem().tick(sim);

        ActiveDefendAreaOrder order = assertInstanceOf(ActiveDefendAreaOrder.class,
                sim.getSquadMoveOrderService().activeOrder(squadId));
        assertFalse(order.isOccupy());
    }

    @Test
    void theOverlayShowsPositionsOnlyWhileInfantryIsSelected() {
        HighlightOverlay overlay = new HighlightOverlay();

        FightingPositionHighlightPublisher.publish(selection, sim, overlay, null);
        assertEquals(12, overlay.source(HighlightOverlay.SRC_FIGHTING_POSITIONS).size());
        assertTrue(overlay.source(HighlightOverlay.SRC_FIGHTING_POSITION_CLAIMS).isEmpty());

        FightingPositionHighlightPublisher.publish(new Selection(), sim, overlay, null);
        assertFalse(overlay.hasSource(HighlightOverlay.SRC_FIGHTING_POSITIONS));
    }

    @Test
    void theOverlayIsCulledToTheViewport() {
        HighlightOverlay overlay = new HighlightOverlay();
        VisibleCellRect visible = new VisibleCellRect(0, 0, 33, 79);

        FightingPositionHighlightPublisher.publish(selection, sim, overlay, visible);

        assertEquals(4, overlay.source(HighlightOverlay.SRC_FIGHTING_POSITIONS).size(), "x = 30..33 only");
    }
}
