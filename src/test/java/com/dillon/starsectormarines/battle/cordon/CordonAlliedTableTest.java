package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.fabrication.FabricationService.Machine;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules of the Cordon's allied table by density ({@code cordon-allied-forces.md}), asked directly. */
class CordonAlliedTableTest {

    @Test
    void everyThirdMachineOffATownsLineIsAHound() {
        CordonAlliedTable town = CordonAlliedTable.of(CordonPlan.Density.TOWN);
        for (int n = 0; n < 9; n++) {
            Machine machine = town.machine(n);
            assertEquals(n % 3 == 2, machine.chassis() == MechVariant.HOUND, "machine " + n);
        }
        assertTrue(town.buildsCarriers());
    }

    @Test
    void aCityBuildsOnlyHoundsAndSendsNoCarrierForward() {
        CordonAlliedTable urban = CordonAlliedTable.of(CordonPlan.Density.URBAN);
        for (int n = 0; n < 4; n++) assertEquals(Machine.mech(MechVariant.HOUND), urban.machine(n));
        assertFalse(urban.buildsCarriers());
        assertFalse(urban.carriersForward());
        assertFalse(urban.openingCarrier());
        assertTrue(urban.openingArmour().isEmpty());
        assertEquals(2, urban.mouthHounds());
    }

    @Test
    void openCountryWalksItsWholeLineIncludingTheRepeatedCarrier() {
        CordonAlliedTable rural = CordonAlliedTable.of(CordonPlan.Density.RURAL);
        assertEquals(List.of(VehicleType.HEAVY_APC, VehicleType.SCOUT_CAR, VehicleType.HEAVY_APC,
                        VehicleType.MILITIA_TECHNICAL, VehicleType.HEAVY_APC),
                List.of(rural.machine(0).vehicle(), rural.machine(1).vehicle(), rural.machine(2).vehicle(),
                        rural.machine(3).vehicle(), rural.machine(4).vehicle()));
        assertTrue(rural.carriersForward());
        assertEquals(CordonAlliedTable.CarrierDrop.BEHIND_WALL, rural.drop());
    }

    @Test
    void aTownsCarriersSetDownAtTheRearGateOnly() {
        assertEquals(CordonAlliedTable.CarrierDrop.REAR_GATE, CordonAlliedTable.of(CordonPlan.Density.TOWN).drop());
    }

    @Test
    void theGateIsTheWallsGapNearestItsMiddle() {
        boolean[] wall = parse("#..###.#####");
        assertEquals(6, CordonArmour.gateAlong(wall, 7));
        assertEquals(1, CordonArmour.gateAlong(wall, 2));
    }

    @Test
    void anUnbrokenWallOrAnOpenEndIsNoGate() {
        assertEquals(-1, CordonArmour.gateAlong(parse("#######"), 3));
        assertEquals(-1, CordonArmour.gateAlong(parse("..#####..."), 5));
    }

    @Test
    void streetMouthsAreTheMiddlesOfTheOpenRunsNearestTheRedoubtFirst() {
        boolean[] row = open(".###..###...####");
        assertEquals(List.of(4, 10, 0), CordonArmour.mouths(row, 6, 12));
    }

    @Test
    void openCountryIsAMouthWhereItIsNearestTheRedoubt() {
        boolean[] row = open("####....................####");
        assertEquals(List.of(14), CordonArmour.mouths(row, 14, 12));
        assertEquals(List.of(6), CordonArmour.mouths(row, 1, 12));
    }

    @Test
    void aPairSharingTheOnlyMouthStandsEitherSideOfIt() {
        assertEquals(-2, CordonArmour.across(0, true));
        assertEquals(2, CordonArmour.across(1, true));
        assertEquals(0, CordonArmour.across(0, false));
        assertEquals(4, CordonArmour.across(1, false));
        assertEquals(-4, CordonArmour.across(2, false));
    }

    /** Open ground along a row: {@code .} open, {@code #} built. */
    private static boolean[] open(String cells) {
        boolean[] out = new boolean[cells.length()];
        for (int i = 0; i < out.length; i++) out[i] = cells.charAt(i) == '.';
        return out;
    }

    /** A wall along a row: {@code #} wall, {@code .} a gap. */
    private static boolean[] parse(String cells) {
        boolean[] out = new boolean[cells.length()];
        for (int i = 0; i < out.length; i++) out[i] = cells.charAt(i) == '#';
        return out;
    }
}
