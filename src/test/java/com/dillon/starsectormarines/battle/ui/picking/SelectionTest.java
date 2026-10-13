package com.dillon.starsectormarines.battle.ui.picking;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionTest {

    @Test
    void groupScopeDeduplicatesAndKeepsAnIndependentPrimary() {
        Selection selection = new Selection();
        selection.selectSquads(List.of(2, 3, 2), 3, 31L, false);
        assertEquals(List.of(2, 3), selection.getSelectedSquadIds());
        assertEquals(3, selection.getSelectedSquadId());
        selection.selectSquads(List.of(4, 2), 4, 41L, true);
        assertEquals(List.of(2, 3, 4), selection.getSelectedSquadIds());
        selection.retainSquads(id -> id != 4);
        assertEquals(List.of(2, 3), selection.getSelectedSquadIds());
        assertEquals(2, selection.getSelectedSquadId());
        assertEquals(0L, selection.getSelectedUnitEntityId());
        selection.selectUnit(7, 71L);
        assertEquals(List.of(7), selection.getSelectedSquadIds());
        selection.selectVehicle(99L);
        assertEquals(List.of(), selection.getSelectedSquadIds());
    }

    @Test
    void unitAndVehicleSelectionsRemainMutuallyExclusive() {
        Selection selection = new Selection();
        selection.selectVehicle(91L);
        selection.selectUnit(4, 42L);

        assertEquals(4, selection.getSelectedSquadId());
        assertEquals(42L, selection.getSelectedUnitEntityId());
        assertFalse(selection.hasVehicleSelection());

        selection.selectVehicle(92L);
        assertTrue(selection.hasVehicleSelection());
        assertFalse(selection.hasSquadSelection());
        assertEquals(0L, selection.getSelectedUnitEntityId());

        selection.selectSquad(7);
        assertTrue(selection.hasSquadSelection());
        assertEquals(0L, selection.getSelectedVehicleId());
    }
}
