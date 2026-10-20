package com.dillon.starsectormarines.battle.ui.picking;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** A right drag on a vehicle or mech reads as a heading in the convention every chassis turns by. */
class FacingDragTest {

    @Test
    void aDragsHeadingIsTheSpriteBearing() {
        assertEquals(0f, SelectionOrders.facingOf(0f, 3f), 1e-4f, "+Y is north, zero");
        assertEquals(-90f, SelectionOrders.facingOf(2f, 0f), 1e-4f, "east is clockwise of north");
        assertEquals(90f, SelectionOrders.facingOf(-2f, 0f), 1e-4f);
        assertEquals(180f, Math.abs(SelectionOrders.facingOf(0f, -1f)), 1e-4f);
    }

    @Test
    void infantryDrawALineRatherThanAHeading() {
        NavigationGrid grid = new NavigationGrid(20, 20);
        for (int y = 0; y < 20; y++) for (int x = 0; x < 20; x++) grid.setWalkableFloor(x, y);
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(20, 20));
        int squad = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 5, 5).squad(squad));
        sim.getSquad(squad).aliveMembers = 1;
        Selection selection = new Selection();
        selection.selectSquad(squad);

        assertFalse(SelectionOrders.takesFacing(sim, selection));
        assertFalse(SelectionOrders.takesFacing(sim, new Selection()), "nothing selected takes nothing");
    }
}
