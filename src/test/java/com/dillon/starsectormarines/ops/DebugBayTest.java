package com.dillon.starsectormarines.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.SpawnInOnslaught;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import java.util.List;
import org.junit.jupiter.api.Test;

class DebugBayTest {
    @Test void slotsCycleThroughEmptyMechsAndVehiclesAndWrap() {
        var slot = DebugBaySlot.EMPTY;
        int options = DebugBaySlot.options().size();
        assertEquals(1 + MechVariant.values().length + VehicleType.values().length, options);
        for (int i = 0; i < options; i++) slot = DebugBaySlot.cycle(slot, 1);
        assertTrue(slot.sameAs(DebugBaySlot.EMPTY));
        assertTrue(DebugBaySlot.cycle(DebugBaySlot.EMPTY, -1).vehicle != null);
    }

    @Test void aRosterOfSlotsKeepsMechsAndVehiclesAndSkipsEmpties() {
        var roster = DebugMechRoster.ofSlots(List.of(DebugBaySlot.of(MechVariant.HOUND), DebugBaySlot.EMPTY,
                DebugBaySlot.of(VehicleType.BREAKTHROUGH_TANK), DebugBaySlot.EMPTY));
        assertEquals(2, roster.count());
        assertEquals(2, roster.slots().size());
        var powers = roster.applyTo(List.of());
        assertEquals(1, powers.size());
    }

    @Test void theOnslaughtEggIsOnlyAddedByTheDebugHelper() {
        var powers = DebugMechRoster.easterEggPowers();
        assertEquals(SpawnInOnslaught.ID, powers.get(0).id);
    }

    @Test void aSquadCardCycleKeepsTheOtherCardAndMovesTheSquad() {
        var roster = DebugMissionCatalog.starterCompany(2);
        var squad = DebugSquadCards.lineSquads(roster).get(0);
        String armor = squad.armorDoctrineId();
        String weapons = squad.weaponDoctrineId();
        String next = DebugSquadCards.cycleWeapons(roster, squad, 1);
        assertEquals(next, squad.weaponDoctrineId());
        assertEquals(armor, squad.armorDoctrineId());
        assertEquals(weapons, DebugSquadCards.cycleWeapons(roster, squad, -1));
    }
}
