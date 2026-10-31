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

    @Test
    void aGroupHoldsSquadsAndVehiclesAndTheSquadKeepsTheFocus() {
        Selection selection = new Selection();
        selection.selectGroup(List.of(), List.of(90L, 91L), NONE_SQUAD, 0L, 91L, false);
        assertEquals(91L, selection.getSelectedVehicleId(), "a vehicle-only group focuses its primary vehicle");
        assertTrue(selection.hasVehicleSelection());

        selection.selectGroup(List.of(5), List.of(), 5, 51L, 0L, true);
        assertEquals(List.of(90L, 91L), selection.getSelectedVehicleIds(), "Shift keeps the vehicles");
        assertEquals(5, selection.getSelectedSquadId());
        assertFalse(selection.hasVehicleSelection(), "a squad takes the focus, and the squad plate");

        selection.retainVehicles(id -> id != 90L);
        assertEquals(List.of(91L), selection.getSelectedVehicleIds());
        selection.retainSquads(id -> false);
        assertEquals(91L, selection.getSelectedVehicleId(), "with the squads gone the vehicle is the focus");

        selection.selectVehicle(92L);
        assertEquals(List.of(92L), selection.getSelectedVehicleIds(), "a click selects one vehicle alone");
        selection.selectUnit(4, 42L);
        assertEquals(0, selection.vehicleCount());
    }

    @Test
    void aSelectedCivilianIsExclusiveWithEveryCommandScope() {
        Selection selection = new Selection();
        selection.selectGroup(List.of(3, 4), List.of(9L), 3, 30L, 0L, false);
        selection.selectCivilian(77L);
        assertTrue(selection.hasCivilianSelection());
        assertEquals(0, selection.squadCount());
        assertEquals(0, selection.vehicleCount());

        selection.selectGroup(List.of(5), List.of(), 5, 50L, 0L, true);
        assertFalse(selection.hasCivilianSelection(), "a Shift box after a civilian starts a fresh scope");
        assertEquals(1, selection.squadCount());
        selection.selectCivilian(77L);
        selection.selectSquad(6);
        assertEquals(0L, selection.getSelectedCivilianId());
    }

    private static final int NONE_SQUAD = Selection.NONE;
}
