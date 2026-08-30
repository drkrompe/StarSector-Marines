package com.dillon.starsectormarines.battle.ui.picking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionTest {

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
