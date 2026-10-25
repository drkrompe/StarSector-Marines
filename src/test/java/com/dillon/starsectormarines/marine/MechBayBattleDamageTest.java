package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.CompanyMechLedger;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Settlement writes a battle's frozen section records back to the bay and starts the free repair. */
class MechBayBattleDamageTest {

    @Test void aDestroyedMountLosesItsAssemblyAndComesBackEmptyWithThePatternKept() {
        MechBay bay = new MechBay();
        String pattern = MechPattern.of(MechWeaponComponent.SRM_15).id();
        bay.learnPattern(pattern);
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.BULWARK);
        assertEquals(2, bay.ownedWeapon(MechWeaponComponent.SRM_15.id));

        MechBay.Settlement settlement = settle(bay, mech, 0f, MechDamageTest.ReportFixture.whole()
                .destroyed(MechSection.LEFT_SHOULDER, MechWeaponComponent.SRM_15));

        assertEquals(1, settlement.assembliesLost());
        assertNull(mech.leftShoulder());
        assertEquals(MechWeaponComponent.SRM_15, mech.rightShoulder());
        assertEquals(1, bay.ownedWeapon(MechWeaponComponent.SRM_15.id));
        assertEquals(0, bay.availableWeapon(MechWeaponComponent.SRM_15.id));
        assertTrue(bay.knowsPattern(pattern));
        // The shoulder itself is rebuilt like any other damaged section.
        assertEquals(1f, mech.damageOn(0f).structureLost(MechSection.LEFT_SHOULDER), 1e-6f);
    }

    @Test void damageStartsAFreeRepairWhoseClockWearsItAway() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.BULWARK);

        MechBay.Settlement settlement = settle(bay, mech, 10f, MechDamageTest.ReportFixture.whole()
                .destroyed(MechSection.LEGS, null).section(MechSection.HEAD, 0.5f, 0f));
        float days = MechRepairs.estimate(MechVariant.BULWARK, mech.damageOn(10f)).days();

        assertEquals(1, settlement.mechsDamaged());
        assertEquals(mech, bay.mechById(mech.id()));
        assertEquals(10f + days, mech.repairUntilDay(), 1e-4f);
        assertEquals(CampaignMech.Readiness.UNDER_REPAIR, mech.readiness(10f));
        assertEquals(1f, mech.damageOn(10f).structureLost(MechSection.LEGS), 1e-6f);
        assertEquals(0.5f, mech.damageOn(10f + days / 2f).structureLost(MechSection.LEGS), 1e-4f);
        assertEquals(0.25f, mech.damageOn(10f + days / 2f).structureLost(MechSection.HEAD), 1e-4f);
        assertNull(mech.damageOn(10f + days));
        assertEquals(CampaignMech.Readiness.READY, mech.readiness(10f + days));
        assertEquals(MechWeaponComponent.SRM_15, mech.leftShoulder());
    }

    @Test void fieldRepairShortensTheClock() {
        MechBay plainBay = new MechBay();
        CampaignMech plain = plainBay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.BULWARK);
        MechBay jigsBay = new MechBay();
        CampaignMech jigs = jigsBay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.BULWARK);
        MechDamageTest.ReportFixture report = MechDamageTest.ReportFixture.whole().destroyed(MechSection.LEGS, null);

        plainBay.settleBattleDamage(List.of(damage(plain, report)), 0f, MechFieldRepair.NEUTRAL);
        jigsBay.settleBattleDamage(List.of(damage(jigs, report)), 0f, new MechFieldRepair(0.75f, 1f));

        assertEquals(plain.repairUntilDay() * 0.75f, jigs.repairUntilDay(), 1e-4f);
    }

    @Test void aMechHitAgainMidRepairRestartsOnTheWorseOfWhatItCarriedAndWhatItTook() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        settle(bay, mech, 0f, MechDamageTest.ReportFixture.whole().section(MechSection.LEGS, 0f, 0f));
        float half = mech.repairUntilDay() / 2f;

        settle(bay, mech, half, MechDamageTest.ReportFixture.whole().section(MechSection.HEAD, 0.2f, 1f));

        MechDamage now = mech.damageOn(half);
        assertEquals(0.5f, now.structureLost(MechSection.LEGS), 1e-4f);
        assertEquals(0.8f, now.structureLost(MechSection.HEAD), 1e-4f);
        assertEquals(half + MechRepairs.estimate(MechVariant.HOUND, now).days(), mech.repairUntilDay(), 1e-4f);
    }

    @Test void damageAnOlderSaveLeftWaitingForAnOrderGetsItsClock() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        MechDamage legacy = MechDamage.of(MechDamageTest.ReportFixture.whole()
                .section(MechSection.LEGS, 0f, 0f).build());
        CampaignMechDamageSaveTest.setField(mech, "damage", legacy);
        assertNotNull(mech.damageOn(100f));

        bay.startPendingRepairs(5f, MechFieldRepair.NEUTRAL);

        assertEquals(5f + MechRepairs.estimate(MechVariant.HOUND, legacy).days(), mech.repairUntilDay(), 1e-4f);
        assertNull(mech.damageOn(mech.repairUntilDay()));
    }

    @Test void aTorsoKillLosesTheChassisAndEverythingFittedToIt() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.BULWARK);
        CampaignMech spare = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        bay.addWeapon(MechWeaponComponent.SRM_15.id, 1);
        int engines = bay.ownedInternal(MechInternal.INDUSTRIAL_ENGINE.id);

        MechBay.Settlement settlement = bay.settleBattleDamage(List.of(new CompanyMechLedger.Damage(mech.id(),
                MechDamageTest.ReportFixture.whole().destroyed(MechSection.TORSO, null).build(), true)),
                0f, MechFieldRepair.NEUTRAL);

        assertEquals(1, settlement.chassisLost());
        assertNull(bay.mechById(mech.id()));
        assertEquals(List.of(spare), bay.activeSquad().mechs());
        assertEquals(1, bay.activeSquad().occupied());
        assertEquals(0, bay.ownedWeapon(MechWeaponComponent.CHAINGUN.id));
        assertEquals(1, bay.ownedWeapon(MechWeaponComponent.SRM_15.id), "the loose spare stays");
        assertEquals(engines - 1, bay.ownedInternal(MechInternal.INDUSTRIAL_ENGINE.id));
    }

    @Test void aChassisThatDiedAsOneBodyIsStillLost() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);

        bay.settleBattleDamage(List.of(new CompanyMechLedger.Damage(mech.id(), null, true)), 0f, MechFieldRepair.NEUTRAL);

        assertNull(bay.mechById(mech.id()));
    }

    @Test void aReplayedResultStrikesNothingTwice() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.BULWARK);
        List<CompanyMechLedger.Damage> damage = List.of(new CompanyMechLedger.Damage(mech.id(),
                MechDamageTest.ReportFixture.whole()
                        .destroyed(MechSection.RIGHT_SHOULDER, MechWeaponComponent.SRM_15).build(), false),
                new CompanyMechLedger.Damage("not-in-the-bay", null, true));

        bay.settleBattleDamage(damage, 3f, MechFieldRepair.NEUTRAL);
        float until = mech.repairUntilDay();
        MechBay.Settlement replay = bay.settleBattleDamage(damage, 3f, MechFieldRepair.NEUTRAL);

        assertEquals(0, replay.assembliesLost());
        assertEquals(0, replay.chassisLost());
        assertEquals(1, bay.ownedWeapon(MechWeaponComponent.SRM_15.id));
        assertEquals(until, mech.repairUntilDay(), 1e-6f);
    }

    private static MechBay.Settlement settle(MechBay bay, CampaignMech mech, float day,
                                             MechDamageTest.ReportFixture report) {
        return bay.settleBattleDamage(List.of(damage(mech, report)), day, MechFieldRepair.NEUTRAL);
    }

    private static CompanyMechLedger.Damage damage(CampaignMech mech, MechDamageTest.ReportFixture report) {
        return new CompanyMechLedger.Damage(mech.id(), report.build(), false);
    }
}
