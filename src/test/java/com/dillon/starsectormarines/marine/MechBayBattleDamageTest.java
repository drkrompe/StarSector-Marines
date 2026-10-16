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

/** Settlement writes a battle's frozen section records back to the bay. */
class MechBayBattleDamageTest {

    @Test void aDestroyedMountLosesItsAssemblyAndComesBackEmptyWithThePatternKept() {
        MechBay bay = new MechBay();
        String pattern = MechPattern.of(MechWeaponComponent.SRM_15).id();
        bay.learnPattern(pattern);
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.BULWARK);
        assertEquals(2, bay.ownedWeapon(MechWeaponComponent.SRM_15.id));

        MechBay.Settlement settlement = bay.settleBattleDamage(List.of(new CompanyMechLedger.Damage(mech.id(),
                MechDamageTest.ReportFixture.whole()
                        .destroyed(MechSection.LEFT_SHOULDER, MechWeaponComponent.SRM_15).build(), false)));

        assertEquals(1, settlement.assembliesLost());
        assertNull(mech.leftShoulder());
        assertEquals(MechWeaponComponent.SRM_15, mech.rightShoulder());
        assertEquals(1, bay.ownedWeapon(MechWeaponComponent.SRM_15.id));
        assertEquals(0, bay.availableWeapon(MechWeaponComponent.SRM_15.id));
        assertTrue(bay.knowsPattern(pattern));
        // The shoulder itself is rebuilt like any other damaged section.
        assertEquals(1f, mech.damage().structureLost(MechSection.LEFT_SHOULDER), 1e-6f);
    }

    @Test void otherDamageIsRecordedForRepairAndTheMechStays() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.BULWARK);

        MechBay.Settlement settlement = bay.settleBattleDamage(List.of(new CompanyMechLedger.Damage(mech.id(),
                MechDamageTest.ReportFixture.whole().destroyed(MechSection.LEGS, null)
                        .section(MechSection.HEAD, 0.5f, 0f).build(), false)));

        assertEquals(1, settlement.mechsDamaged());
        assertEquals(0, settlement.assembliesLost());
        assertEquals(mech, bay.mechById(mech.id()));
        assertNotNull(mech.damage());
        assertEquals(1f, mech.damage().structureLost(MechSection.LEGS), 1e-6f);
        assertEquals(0.5f, mech.damage().structureLost(MechSection.HEAD), 1e-6f);
        assertEquals(CampaignMech.Readiness.DAMAGED, mech.readiness(0f));
        assertEquals(MechWeaponComponent.SRM_15, mech.leftShoulder());
    }

    @Test void aTorsoKillLosesTheChassisAndEverythingFittedToIt() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.BULWARK);
        CampaignMech spare = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        bay.addWeapon(MechWeaponComponent.SRM_15.id, 1);
        int engines = bay.ownedInternal(MechInternal.INDUSTRIAL_ENGINE.id);

        MechBay.Settlement settlement = bay.settleBattleDamage(List.of(new CompanyMechLedger.Damage(mech.id(),
                MechDamageTest.ReportFixture.whole().destroyed(MechSection.TORSO, null).build(), true)));

        assertEquals(1, settlement.chassisLost());
        assertNull(bay.mechById(mech.id()));
        assertEquals(List.of(spare), bay.activeSquad().mechs());
        assertEquals(1, bay.activeSquad().occupied());
        assertEquals(0, bay.ownedWeapon(MechWeaponComponent.DUAL_CHAINGUNS.id));
        assertEquals(1, bay.ownedWeapon(MechWeaponComponent.SRM_15.id), "the loose spare stays");
        assertEquals(engines - 1, bay.ownedInternal(MechInternal.INDUSTRIAL_ENGINE.id));
    }

    @Test void aChassisThatDiedAsOneBodyIsStillLost() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);

        bay.settleBattleDamage(List.of(new CompanyMechLedger.Damage(mech.id(), null, true)));

        assertNull(bay.mechById(mech.id()));
    }

    @Test void aReplayedResultStrikesNothingTwice() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.BULWARK);
        List<CompanyMechLedger.Damage> damage = List.of(new CompanyMechLedger.Damage(mech.id(),
                MechDamageTest.ReportFixture.whole()
                        .destroyed(MechSection.RIGHT_SHOULDER, MechWeaponComponent.SRM_15).build(), false),
                new CompanyMechLedger.Damage("not-in-the-bay", null, true));

        bay.settleBattleDamage(damage);
        MechBay.Settlement replay = bay.settleBattleDamage(damage);

        assertEquals(0, replay.assembliesLost());
        assertEquals(0, replay.chassisLost());
        assertEquals(1, bay.ownedWeapon(MechWeaponComponent.SRM_15.id));
    }
}
