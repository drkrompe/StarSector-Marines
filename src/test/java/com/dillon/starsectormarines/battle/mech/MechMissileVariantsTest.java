package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.MechDuelBand.Weapon;
import com.dillon.starsectormarines.marine.MechFrameBudget;
import com.dillon.starsectormarines.marine.MechPattern;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anti-armor and fragmentation racks ({@code mechs-nouns.md}, Missile warheads) read off the shipped
 * weapon data: the gate, the sidegrade's equal room, and the target and duel readings.
 */
class MechMissileVariantsTest {

    private static final List<MechWeaponComponent> AT_RACKS = List.of(
            MechWeaponComponent.SRM_5, MechWeaponComponent.SRM_15,
            MechWeaponComponent.LRM_5, MechWeaponComponent.LRM_15);

    /** A line marine's plate, a Bulwark's and a breakthrough tank's. */
    private static final float MAN = 4f;
    private static final float BULWARK = 18f;
    private static final float TANK = 36f;

    @Test
    void antiArmorRacksAreGatedAndFragmentationRacksAreNot() {
        for (MechWeaponComponent rack : AT_RACKS) {
            assertTrue(rack.antiArmorOnly(), rack.name());
            assertFalse(rack.fragCounterpart().antiArmorOnly(), rack.fragCounterpart().name());
        }
        assertFalse(MechWeaponComponent.CHAINGUN.antiArmorOnly());
    }

    @Test
    void aFragmentationRackCostsTheSameRoomAsItsAntiArmorRack() {
        for (MechWeaponComponent rack : AT_RACKS) {
            MechWeaponComponent frag = rack.fragCounterpart();
            assertNotNull(frag, rack.name());
            assertEquals(rack.footprintColumns, frag.footprintColumns, rack.name());
            assertEquals(rack.footprintRows, frag.footprintRows, rack.name());
            assertEquals(MechPattern.of(rack).accessTier(), MechPattern.of(frag).accessTier(), rack.name());
            assertEquals(MechFrameBudget.mass(rack), MechFrameBudget.mass(frag), rack.name());
            assertEquals(rack.fluxPerTrigger(), frag.fluxPerTrigger(), rack.name());
            assertEquals(rack.cycleSeconds(), frag.cycleSeconds(), rack.name());
            assertEquals(rack.ammoCapacity, frag.ammoCapacity, rack.name());
            assertEquals(rack.projectilesPerTrigger, frag.projectilesPerTrigger, rack.name());
        }
    }

    @Test
    void anAntiArmorRackScoresNothingAgainstAMan() {
        assertEquals(0f, MechTargetPressure.pressure(List.of(MechWeaponComponent.SRM_15), 12f, MAN, false));
        assertTrue(MechTargetPressure.pressure(List.of(MechWeaponComponent.SRM_15), 12f, BULWARK, true) > 0f);
        assertTrue(MechTargetPressure.pressure(List.of(MechWeaponComponent.SRM_15_FRAG), 12f, MAN, false) > 0f);
    }

    @Test
    void eachWarheadWinsAgainstWhatItIsFor() {
        for (MechWeaponComponent rack : AT_RACKS) {
            MechWeaponComponent frag = rack.fragCounterpart();
            float distance = Math.min(rack.weaponDef().range, frag.weaponDef().range) * 0.6f;
            float atTank = MechTargetPressure.pressure(List.of(rack), distance, TANK, true);
            float fragTank = MechTargetPressure.pressure(List.of(frag), distance, TANK, true);
            assertTrue(atTank > 2f * fragTank, rack.name() + ": " + atTank + " against " + fragTank);
            float atBulwark = MechTargetPressure.pressure(List.of(rack), distance, BULWARK, true);
            float fragBulwark = MechTargetPressure.pressure(List.of(frag), distance, BULWARK, true);
            assertTrue(atBulwark > fragBulwark, rack.name() + ": " + atBulwark + " against " + fragBulwark);
        }
    }

    @Test
    void aChaingunBulwarkTurnsToTheTankOnlyWhileItsRacksAreLoaded() {
        List<MechWeaponComponent> loaded = List.of(MechWeaponComponent.CHAINGUN,
                MechWeaponComponent.SRM_15, MechWeaponComponent.SRM_15);
        List<MechWeaponComponent> racksEmpty = List.of(MechWeaponComponent.CHAINGUN);
        float distance = 16f;
        assertTrue(MechTargetPressure.pressure(loaded, distance, TANK, true)
                > MechTargetPressure.pressure(loaded, distance, MAN, false));
        assertTrue(MechTargetPressure.pressure(racksEmpty, distance, MAN, false)
                > MechTargetPressure.pressure(racksEmpty, distance, TANK, true));
    }

    @Test
    void anIndirectRackDoesNotScoreInsideTheArmsReach() {
        List<MechWeaponComponent> warden = List.of(MechWeaponComponent.HC_20,
                MechWeaponComponent.LRM_5_FRAG);
        float inside = MechWeaponComponent.HC_20.weaponDef().range - 2f;
        assertEquals(MechTargetPressure.pressure(List.of(MechWeaponComponent.HC_20), inside, MAN, false),
                MechTargetPressure.pressure(warden, inside, MAN, false));
    }

    @Test
    void theDuelBandReadsAFragmentationRackAtItsArmorEfficiency() {
        Weapon plain = Weapon.of(MechWeaponComponent.SRM_15_FRAG.weaponDef());
        Weapon againstTank = Weapon.of(MechWeaponComponent.SRM_15_FRAG.weaponDef(), TANK);
        assertTrue(againstTank.pressure() < 0.3f * plain.pressure(), againstTank + " against " + plain);
        Weapon antiArmor = Weapon.of(MechWeaponComponent.SRM_15.weaponDef(), TANK);
        assertTrue(antiArmor.pressure() > Weapon.of(MechWeaponComponent.SRM_15.weaponDef()).pressure(),
                "an anti-armor rack is read at its contact payload, not its small splash");
    }

    @Test
    void theWardenShipsFragmentationLrms() {
        assertEquals(MechWeaponComponent.LRM_5_FRAG, MechVariant.WARDEN.leftShoulder);
        assertEquals(MechWeaponComponent.LRM_5_FRAG, MechVariant.WARDEN.rightShoulder);
    }
}
