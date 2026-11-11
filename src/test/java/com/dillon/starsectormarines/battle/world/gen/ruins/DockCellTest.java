package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Cell;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DockCellTest {

    private static final Area SHOP = new Area(0, 0, 15, 11);

    @Test
    void anOpenShopDocksAtItsMiddle() {
        assertEquals(new Cell(SHOP.centreX(), SHOP.centreY()), RuinsDelveStage.dockCell(SHOP, (x, y) -> true));
    }

    @Test
    void theDockKeepsTwoClearCellsFromAnyPiece() {
        // A piece on the middle: the dock moves until the piece is three cells off it.
        Cell middle = new Cell(SHOP.centreX(), SHOP.centreY());
        Cell dock = RuinsDelveStage.dockCell(SHOP, (x, y) -> !(x == middle.x() && y == middle.y()));
        assertEquals(3, Math.max(Math.abs(dock.x() - middle.x()), Math.abs(dock.y() - middle.y())));
    }

    @Test
    void aShopWithNoClearSquareHasNoDock() {
        assertNull(RuinsDelveStage.dockCell(SHOP, (x, y) -> (x + y) % 3 != 0));
    }
}
