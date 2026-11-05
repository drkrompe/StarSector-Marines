package com.dillon.starsectormarines.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dillon.starsectormarines.battle.mech.CompanyMechLedger;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.ops.battleview.SquadMuster;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DebriefForceTest {

    private static MechSections.Report report(float remaining) {
        int n = MechSection.values().length;
        boolean[] fitted = new boolean[n];
        float[] structure = new float[n];
        float[] armor = new float[n];
        for (int i = 0; i < n; i++) {
            fitted[i] = true;
            structure[i] = remaining;
            armor[i] = remaining;
        }
        return new MechSections.Report("plan", fitted, structure, armor, new boolean[n],
                new MechWeaponComponent[MechMountSlot.values().length]);
    }

    private static DebriefForce.Machine machine(float days, boolean lost) {
        return new DebriefForce.Machine("m", "m", MechVariant.HOUND, report(1f), null, lost, days);
    }

    private static MissionOutcome outcome(MissionSource source, List<CompanyMechLedger.Fielded> mechs,
                                          List<ConvoyService.FieldedVehicle> vehicles) {
        return MissionOutcome.builder().missionSource(source).fieldedMechs(mechs).fieldedVehicles(vehicles).build();
    }

    @Test void aLostMarinesSeatComesBackEmpty() {
        SquadMuster muster = BriefingViewModel.previewMuster("a", "A", 12).without(Set.of("a-3", "stranger"));
        assertEquals(11, muster.readyCount());
        assertEquals(1, muster.vacantCount());
        assertEquals(SquadMuster.State.VACANT, muster.billets().get(3).state());
        assertEquals(SquadMuster.State.READY, muster.billets().get(4).state());
    }

    @Test void fullRepairIsTheLongestSingleRepair() {
        assertEquals(3.5f, DebriefForce.daysToFullyRepaired(List.of(machine(1f, false), machine(3.5f, false),
                machine(0f, true))));
        assertEquals(0f, DebriefForce.daysToFullyRepaired(List.of()));
    }

    @Test void aMechWithNoClockIsQuotedTheGantryAndAnUntouchedOneNothing() {
        var worn = new CompanyMechLedger.Fielded(null, null, MechVariant.HOUND, report(.5f), false);
        var whole = new CompanyMechLedger.Fielded(null, null, MechVariant.HOUND, report(1f), false);
        assertTrue(DebriefForce.repairDays(worn, null, 0f) > 0f);
        assertEquals(0f, DebriefForce.repairDays(whole, null, 0f));
    }

    @Test void aLostMechOwesNoRepair() {
        var lost = new CompanyMechLedger.Fielded("a", "Rook", MechVariant.HOUND, report(.5f), true);
        var machines = DebriefForce.machines(outcome(MissionSource.STORY, List.of(lost), List.of()), null, 0f);
        assertEquals(0f, machines.get(0).repairDays());
        assertTrue(machines.get(0).lost());
    }

    @Test void onlyADebugBattleShowsMachinesTheCompanyDoesNotOwn() {
        var owned = new CompanyMechLedger.Fielded("a", null, MechVariant.HOUND, report(1f), false);
        var supplied = new CompanyMechLedger.Fielded(null, null, MechVariant.WARDEN, report(1f), false);
        var vehicle = new ConvoyService.FieldedVehicle(null, null, VehicleType.values()[0], false);
        assertEquals(1, DebriefForce.machines(outcome(MissionSource.STORY, List.of(owned, supplied), List.of(vehicle)), null, 0f).size());
        assertEquals(3, DebriefForce.machines(outcome(MissionSource.DEBUG, List.of(owned, supplied), List.of(vehicle)), null, 0f).size());
    }
}
