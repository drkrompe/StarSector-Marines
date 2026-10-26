package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechWorkshopTest {

    @Test void restoredBerthsGateFabricationAtCommitWithoutSpendingOrDeletingOwnedMechs() {
        var bay = new MechBay();
        var resources = TestPurse.rich().reclaiming(Reclamation.MACHINERY);
        int[] berths = {0};
        var workshop = new MechWorkshop(bay, resources, () -> berths[0]);
        for (int count = 0; count <= 4; count++) {
            long credits = resources.credits;
            assertEquals(MechWorkshop.Status.LANCE_FULL,
                    workshop.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND).status());
            assertEquals(credits, resources.credits);
            assertEquals(count, bay.activeSquad().mechs().size());
            if (count < 4) {
                berths[0]++;
                assertTrue(workshop.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND).succeeded());
            }
        }
        berths[0] = 1;
        assertFalse(workshop.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND).succeeded());
        assertEquals(4, bay.activeSquad().mechs().size());
    }

    @Test
    void scrappingFreesThePadReturnsFittedPartsToStoresAndTheShopsShareOfTheHullToTheHold() {
        MechBay bay = MechBay.legacyStarterFixture();
        TestPurse resources = TestPurse.rich().reclaiming(Reclamation.MACHINERY);
        MechWorkshop workshop = new MechWorkshop(bay, resources);
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        MechWeaponComponent arms = Arrays.stream(MechMountSlot.values())
                .map(mech::weaponAt).filter(Objects::nonNull).findFirst().orElseThrow();
        int spareArms = bay.availableWeapon(arms.id);
        long fitted = Arrays.stream(MechMountSlot.values()).filter(slot -> mech.weaponAt(slot) == arms).count();
        int occupied = bay.activeSquad().occupied();
        long before = resources.credits;
        FabricationCost quoted = workshop.mechSalvage(mech.id());
        assertNotNull(quoted);

        assertEquals(MechWorkshop.Status.SCRAPPED, workshop.scrapMech(mech.id()).status());

        assertNull(bay.mechById(mech.id()));
        assertEquals(occupied - 1, bay.activeSquad().occupied());
        assertEquals(spareArms + fitted, bay.availableWeapon(arms.id), "every fitted copy is a spare again");
        assertEquals(before + quoted.credits(), resources.credits, "what was quoted is what arrives");
        assertEquals(MechWorkshop.Status.INVALID, workshop.scrapMech(mech.id()).status());
    }

    @Test
    void fabricatesAndInstallsAWeaponAsOneMaterialTransaction() {
        MechBay bay = MechBay.legacyStarterFixture();
        TestPurse resources = TestPurse.rich().reclaiming(Reclamation.MACHINERY);
        MechWorkshop workshop = new MechWorkshop(bay, resources);
        long before = resources.credits;
        FabricationCost bill = workshop.weaponQuote(MechWeaponComponent.PULSE_LASER);

        MechWorkshop.Result result = workshop.fitWeapon(MechBay.STARTER_MECH_ID,
                MechMountSlot.LEFT_ARM, MechWeaponComponent.PULSE_LASER);

        assertEquals(MechWorkshop.Status.FABRICATED_AND_INSTALLED, result.status());
        assertEquals(MechWeaponComponent.PULSE_LASER,
                bay.mechById(MechBay.STARTER_MECH_ID).leftArm());
        assertEquals(before - bill.credits(), resources.credits);
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.CHAINGUN.id));
    }

    @Test
    void insufficientMaterialsChangeNeitherCargoNorInstalledWeapon() {
        MechBay bay = MechBay.legacyStarterFixture();
        TestPurse resources = new TestPurse(5).reclaiming(Reclamation.MACHINERY);
        MechWorkshop workshop = new MechWorkshop(bay, resources);

        MechWorkshop.Result result = workshop.fitWeapon(MechBay.STARTER_MECH_ID,
                MechMountSlot.LEFT_ARM, MechWeaponComponent.PULSE_LASER);

        assertEquals(MechWorkshop.Status.INSUFFICIENT_MATERIALS, result.status());
        assertEquals(MechWeaponComponent.CHAINGUN,
                bay.mechById(MechBay.STARTER_MECH_ID).leftArm());
        assertEquals(5, resources.credits);
        assertEquals(0, resources.spends);
        assertEquals(0, bay.ownedWeapon(MechWeaponComponent.PULSE_LASER.id));
    }

    @Test
    void removedWeaponReturnsToStoresAndLeavesTheDeploymentMountEmpty() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechWorkshop workshop = new MechWorkshop(bay, new TestPurse(0).reclaiming(Reclamation.MACHINERY));

        MechWorkshop.Result result = workshop.stripWeapon(MechBay.STARTER_MECH_ID,
                MechMountSlot.LEFT_ARM);

        assertEquals(MechWorkshop.Status.STRIPPED, result.status());
        assertEquals(1, bay.availableWeapon(MechWeaponComponent.CHAINGUN.id));
        assertNull(bay.mechById(MechBay.STARTER_MECH_ID).leftArm());
        assertNull(bay.activeDeployment().get(0).leftArm());
    }

    @Test
    void installedWeaponMovesAtomicallyOnlyToACompatibleVacantMount() {
        MechBay bay = MechBay.legacyStarterFixture();
        MechWorkshop workshop = new MechWorkshop(bay, new TestPurse(0).reclaiming(Reclamation.MACHINERY));
        assertTrue(bay.stripWeapon(MechBay.STARTER_MECH_ID,
                MechMountSlot.RIGHT_SHOULDER));

        MechWorkshop.Result moved = workshop.moveWeapon(MechBay.STARTER_MECH_ID,
                MechMountSlot.LEFT_SHOULDER, MechMountSlot.RIGHT_SHOULDER);

        assertEquals(MechWorkshop.Status.MOVED, moved.status());
        CampaignMech mech = bay.mechById(MechBay.STARTER_MECH_ID);
        assertNull(mech.leftShoulder());
        assertEquals(MechWeaponComponent.SRM_15, mech.rightShoulder());

        MechWorkshop.Result occupied = workshop.moveWeapon(MechBay.STARTER_MECH_ID,
                MechMountSlot.LEFT_ARM, MechMountSlot.RIGHT_SHOULDER);
        assertEquals(MechWorkshop.Status.TARGET_OCCUPIED, occupied.status());
        assertEquals(MechWeaponComponent.CHAINGUN, mech.leftArm());
        assertEquals(MechWeaponComponent.SRM_15, mech.rightShoulder());
    }

    @Test
    void chassisFabricationBuildsTheLanceUntilAllFourGantriesAreOccupied() {
        MechBay bay = new MechBay();
        TestPurse resources = TestPurse.rich().reclaiming(Reclamation.MACHINERY);
        MechWorkshop workshop = new MechWorkshop(bay, resources);

        MechWorkshop.Result hound = workshop.fabricateChassis(
                MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        assertTrue(hound.succeeded());
        assertNotNull(hound.mech());
        assertEquals(MechVariant.HOUND, hound.mech().variant());
        // Bought as a bare frame: no nose gun, no engine, held in its gantry until fitted.
        assertNull(hound.mech().arms());
        assertEquals(List.of(MechInternal.Role.ENGINE, MechInternal.Role.CAPACITOR_BANK),
                hound.mech().missingInternals());
        assertTrue(workshop.fabricateChassis(MechBay.STARTER_SQUAD_ID,
                MechVariant.HOUND).succeeded());
        assertTrue(workshop.fabricateChassis(MechBay.STARTER_SQUAD_ID,
                MechVariant.HOUND).succeeded());
        assertTrue(workshop.fabricateChassis(MechBay.STARTER_SQUAD_ID,
                MechVariant.HOUND).succeeded());

        long credits = resources.credits;
        MechWorkshop.Result full = workshop.fabricateChassis(
                MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        assertFalse(full.succeeded());
        assertEquals(MechWorkshop.Status.LANCE_FULL, full.status());
        assertEquals(credits, resources.credits);
        assertEquals(4, bay.activeSquad().mechs().size());
    }

    /** Buying needs a port: away from one every purchase is refused unspent, while stores and the bay still work. */
    @Test
    void awayFromAPortNothingIsBoughtButASpareFromStoresStillFits() {
        MechBay bay = MechBay.legacyStarterFixture();
        TestPurse purse = TestPurse.rich().reclaiming(Reclamation.MACHINERY);
        MechWorkshop workshop = new MechWorkshop(bay, purse);
        assertEquals(MechWorkshop.Status.STRIPPED,
                workshop.stripWeapon(MechBay.STARTER_MECH_ID, MechMountSlot.LEFT_ARM).status());
        long before = purse.credits;

        purse.away();
        assertEquals(MechWorkshop.Status.NO_MARKET, workshop.fabricateWeapon(MechWeaponComponent.PULSE_LASER).status());
        assertEquals(MechWorkshop.Status.NO_MARKET, workshop.fitWeapon(MechBay.STARTER_MECH_ID,
                MechMountSlot.LEFT_ARM, MechWeaponComponent.PULSE_LASER).status());
        assertEquals(MechWorkshop.Status.NO_MARKET,
                new MechWorkshop(new MechBay(), purse).fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND).status());
        assertEquals(before, purse.credits);
        assertNull(bay.mechById(MechBay.STARTER_MECH_ID).leftArm());

        assertEquals(MechWorkshop.Status.INSTALLED_FROM_STORES, workshop.fitWeapon(MechBay.STARTER_MECH_ID,
                MechMountSlot.LEFT_ARM, MechWeaponComponent.CHAINGUN).status());
        assertEquals(before, purse.credits);
    }
}
