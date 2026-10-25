package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.CompanyMechLedger;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A repairing mech drops carrying the wear left on it; only an incomplete frame stays behind. */
class MechBayDropManifestTest {

    @Test void aRepairingMechDropsCarryingTheWearItsClockHasNotTakenOff() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        bay.settleBattleDamage(List.of(new CompanyMechLedger.Damage(mech.id(),
                MechDamageTest.ReportFixture.whole().section(MechSection.HEAD, 0.5f, 0f).build(), false)),
                10f, MechFieldRepair.NEUTRAL);
        float halfway = 10f + (mech.repairUntilDay() - 10f) / 2f;

        List<MechDeploymentSpec> manifest = bay.activeDeployment(halfway);

        assertEquals(1, manifest.size());
        assertEquals(mech.id(), manifest.get(0).campaignId());
        assertEquals(0.25f, manifest.get(0).wear().structureLost(MechSection.HEAD), 1e-4f);
        assertEquals(0.5f, manifest.get(0).wear().armorLost(MechSection.HEAD), 1e-4f);
        assertEquals(List.of(), bay.heldBack(halfway));
        assertTrue(bay.activeDeployment(mech.repairUntilDay()).get(0).wear().isEmpty());
    }

    @Test void aMechMissingAnEngineOrCapacitorsStaysOffTheManifestUntilRefitted() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        MechInternal engine = mech.internal(MechInternal.Role.ENGINE);

        assertTrue(bay.stripInternal(mech.id(), MechInternal.Role.ENGINE));
        assertTrue(bay.stripInternal(mech.id(), MechInternal.Role.CAPACITOR_BANK));

        assertNull(mech.internal(MechInternal.Role.ENGINE));
        assertEquals(List.of(MechInternal.Role.ENGINE, MechInternal.Role.CAPACITOR_BANK), mech.missingInternals());
        assertEquals(List.of(), bay.activeDeployment(0f));
        assertEquals(CampaignMech.Readiness.INCOMPLETE, bay.heldBack(0f).get(0).reason());
        assertEquals(1, bay.availableInternal(engine.id));

        assertTrue(bay.installInternal(mech.id(), engine));
        assertTrue(bay.installInternal(mech.id(), MechInternal.FACTORY_CAPACITOR_BANK));
        assertEquals(1, bay.activeDeployment(0f).size());
    }

    @Test void theManifestCarriesEachMechsCampaignIdentity() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);

        assertEquals(mech.id(), bay.activeDeployment(0f).get(0).campaignId());
    }
}
