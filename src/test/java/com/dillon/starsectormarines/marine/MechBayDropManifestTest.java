package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.CompanyMechLedger;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The armor-drop manifest leaves damaged, repairing and incomplete mechs in their gantries. */
class MechBayDropManifestTest {

    @Test void damagedAndRepairingMechsAreHeldBackWithTheirReason() {
        MechBay bay = new MechBay();
        CampaignMech ready = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        CampaignMech damaged = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.BULWARK);
        CampaignMech repairing = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.WARDEN);
        damage(bay, damaged);
        damage(bay, repairing);
        repairing.beginRepair(12f);

        List<MechDeploymentSpec> manifest = bay.activeDeployment(10f);
        List<MechBay.HeldBack> held = bay.heldBack(10f);

        assertEquals(1, manifest.size());
        assertEquals(ready.id(), manifest.get(0).campaignId());
        assertEquals(2, held.size());
        assertSame(damaged, held.get(0).mech());
        assertEquals(CampaignMech.Readiness.DAMAGED, held.get(0).reason());
        assertSame(repairing, held.get(1).mech());
        assertEquals(CampaignMech.Readiness.UNDER_REPAIR, held.get(1).reason());
        assertEquals(2f, held.get(1).daysRemaining(), 1e-5f);
    }

    @Test void aFinishedRepairReturnsTheMechToTheManifest() {
        MechBay bay = new MechBay();
        CampaignMech mech = bay.fabricateChassis(MechBay.STARTER_SQUAD_ID, MechVariant.HOUND);
        damage(bay, mech);
        mech.beginRepair(12f);

        assertEquals(List.of(), bay.activeDeployment(11.9f));
        assertEquals(1, bay.activeDeployment(12f).size());
        assertEquals(List.of(), bay.heldBack(12f));
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

    private static void damage(MechBay bay, CampaignMech mech) {
        bay.settleBattleDamage(List.of(new CompanyMechLedger.Damage(mech.id(),
                MechDamageTest.ReportFixture.whole().section(MechSection.HEAD, 0.5f, 0f).build(), false)));
    }
}
