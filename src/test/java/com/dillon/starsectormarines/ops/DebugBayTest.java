package com.dillon.starsectormarines.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.SpawnInOnslaught;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.ops.detachment.DebugCompany;
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

    @Test void anUnlockedDebugCompanyCanIssueEveryCardAndKeepsTheOtherHalf() {
        var roster = DebugMissionCatalog.starterCompany(1);
        DebugCompany.unlockEveryCard(roster);
        var squads = DebugSquadCards.lineSquads(roster);
        var squad = squads.get(0);
        String armor = squad.armorDoctrineId();
        var offered = DebugSquadCards.weaponOptions(roster).stream().map(SquadWeaponDoctrine::id).toList();
        for (SquadWeaponDoctrine doctrine : SquadEquipmentDoctrines.weaponDoctrines()) {
            assertTrue(offered.contains(doctrine.id()), doctrine.id());
            assertEquals(1, DebugSquadCards.issueWeapons(roster, squads, doctrine.id()), doctrine.id());
            assertEquals(doctrine.id(), squad.weaponDoctrineId());
        }
        assertEquals(armor, squad.armorDoctrineId());
        String weapons = squad.weaponDoctrineId();
        var sheets = DebugSquadCards.armorOptions(roster);
        assertTrue(sheets.size() > 1, "a starter company knows one sheet; the debug company knows them all");
        for (var plan : sheets) {
            assertEquals(1, DebugSquadCards.issueArmor(roster, squads, plan.id()), plan.id());
            assertEquals(plan.id(), squad.armorDoctrineId());
        }
        assertEquals(weapons, squad.weaponDoctrineId());
    }
}
