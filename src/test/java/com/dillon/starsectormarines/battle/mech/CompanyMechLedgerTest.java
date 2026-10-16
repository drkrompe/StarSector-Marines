package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.DurabilityModel;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The battle freezes each owned mech's sections for settlement. */
class CompanyMechLedgerTest {

    @Test void anUnhitOrUnownedMechReportsNothing() {
        CompanyMechLedger ledger = new CompanyMechLedger();
        ledger.tag("owned", bulwark());
        ledger.tag(null, bulwark());

        assertEquals(List.of(), ledger.damage());
    }

    @Test void aHitMechReportsItsSectionsAndLostMounts() {
        CompanyMechLedger ledger = new CompanyMechLedger();
        MechLoadoutComponent loadout = bulwark();
        ledger.tag("support_mech_01", loadout);
        MechSections sections = loadout.sectionsFor(710f, 710f, 300f, 300f);
        hit(sections, MechSection.LEFT_SHOULDER, 120f);
        loadout.destroyMount(MechMountSlot.LEFT_SHOULDER);

        List<CompanyMechLedger.Damage> damage = ledger.damage();

        assertEquals(1, damage.size());
        assertEquals("support_mech_01", damage.get(0).campaignId());
        assertFalse(damage.get(0).chassisLost());
        MechSections.Report report = damage.get(0).report();
        assertTrue(report.destroyed()[MechSection.LEFT_SHOULDER.ordinal()]);
        assertSame(MechWeaponComponent.SRM_15, report.lostMounts()[MechMountSlot.LEFT_SHOULDER.ordinal()]);
        assertNull(report.lostMounts()[MechMountSlot.RIGHT_SHOULDER.ordinal()]);
    }

    @Test void aTorsoKillOrAWreckLosesTheChassis() {
        CompanyMechLedger ledger = new CompanyMechLedger();
        MechLoadoutComponent torsoKill = bulwark();
        hit(torsoKill.sectionsFor(710f, 710f, 300f, 300f), MechSection.TORSO, 100_000f);
        MechLoadoutComponent wholeBody = bulwark();
        wholeBody.wreckSpawned = true;
        ledger.tag("a", torsoKill);
        ledger.tag("b", wholeBody);

        List<CompanyMechLedger.Damage> damage = ledger.damage();

        assertTrue(damage.get(0).chassisLost());
        assertTrue(damage.get(1).chassisLost());
        assertNull(damage.get(1).report());
    }

    private static MechLoadoutComponent bulwark() {
        MechVariant variant = MechVariant.BULWARK;
        return new MechLoadoutComponent(variant, variant.arms, variant.leftShoulder,
                variant.rightShoulder, variant.defaultRole);
    }

    private static void hit(MechSections sections, MechSection section, float damage) {
        sections.resolve(section, damage, 100f, 0f, 1f,
                new DurabilityModel.Resolution(), new MechSections.Hit());
    }
}
