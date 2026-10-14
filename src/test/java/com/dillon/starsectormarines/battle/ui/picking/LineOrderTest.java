package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.FrontageLine;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveDefendAreaOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveMoveOrder;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A right drag gives the selection a line to hold, facing the way the preview drew it. */
class LineOrderTest {

    private static final int W = 80;
    private static final int H = 80;

    private BattleSimulation sim;
    private Selection selection;
    private int first;
    private int second;

    @BeforeEach
    void setUp() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 10; y <= 70; y++) {
            for (int x = 10; x <= 70; x++) grid.setWalkableFloor(x, y);
        }
        sim = new BattleSimulation(grid, new CellTopology(W, H));
        first = squadAt(20, 30);
        second = squadAt(20, 50);
        sim.advance(1f / 30f);
        selection = new Selection();
        selection.selectSquad(first);
    }

    private int squadAt(int x, int y) {
        int id = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        for (int i = 0; i < 3; i++) {
            sim.spawn(new EntitySpec("m" + id + "-" + i, Faction.MARINE, UnitType.MARINE, x + i, y).squad(id));
        }
        return id;
    }

    private void drag(int x0, int y0, int x1, int y1) {
        SelectionOrders.requestLine(sim, selection, x0, y0, x1, y1);
        sim.getSquadMoveOrderSystem().tick(sim);
    }

    @Test
    void aDragIsALineHeldFacingAwayFromWhereTheSquadStands() {
        List<FrontageLine> preview = SelectionOrders.previewLines(sim, selection, 40, 25, 40, 45);
        drag(40, 25, 40, 45);

        ActiveDefendAreaOrder order = assertInstanceOf(ActiveDefendAreaOrder.class,
                sim.getSquadMoveOrderService().activeOrder(first));
        FrontageLine line = order.line();
        assertNotNull(line, "a drag carries its line");
        assertEquals(40, line.x0());
        assertEquals(45, line.y1());
        assertTrue(line.frontX() > 0.9f, "the squad stands west, so the line faces east");
        assertEquals(preview.get(0), line, "the order faces the way the preview drew");
        assertTrue(sim.getSquad(first).hasPlayerOrder(AssignmentKind.DEFEND_AREA));
        assertEquals(line, sim.frontageLineOf(first));
    }

    @Test
    void twoSquadsShareTheLineEndToEnd() {
        selection.selectSquads(List.of(first, second), first, 0L, false);
        drag(40, 20, 40, 60);

        FrontageLine a = ((ActiveDefendAreaOrder) sim.getSquadMoveOrderService().activeOrder(first)).line();
        FrontageLine b = ((ActiveDefendAreaOrder) sim.getSquadMoveOrderService().activeOrder(second)).line();
        assertEquals(20, a.y0());
        assertEquals(40, a.y1());
        assertEquals(40, b.y0());
        assertEquals(60, b.y1());
    }

    @Test
    void aDragTooShortToBeALineIsAClick() {
        drag(40, 25, 41, 25);
        assertInstanceOf(ActiveMoveOrder.class, sim.getSquadMoveOrderService().activeOrder(first));
    }
}
