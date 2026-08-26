package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentAcquisitionEligibilityTest {

    private final EquipmentTemplateCard common = EquipmentTemplateCatalog.primary(
            WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.SERVICE);
    private final EquipmentTemplateCard advanced = EquipmentTemplateCatalog.primary(
            WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.MILSPEC);
    private final EquipmentTemplateCard prestige = EquipmentTemplateCatalog.primary(
            WeaponRegistry.STARTER_PRIMARY_ID, EquipmentGrade.MASTERWORK);

    @Test
    void openMarketsNeverSellAdvancedCapability() {
        EquipmentAcquisitionEligibility.Progress veteran = progress(100, 100);

        assertTrue(allows(common, FactionEquipmentSource.MARKET, veteran));
        assertFalse(allows(advanced, FactionEquipmentSource.MARKET, veteran));
        assertFalse(allows(prestige, FactionEquipmentSource.MARKET, veteran));
    }

    @Test
    void licensesAndPatronRewardsFollowExistingMrbBands() {
        for (FactionEquipmentSource source : new FactionEquipmentSource[]{
                FactionEquipmentSource.LICENSE, FactionEquipmentSource.PATRON}) {
            assertTrue(allows(common, source, progress(0, 0)));
            assertFalse(allows(advanced, source, progress(100, 4)));
            assertTrue(allows(advanced, source, progress(0, 5)));
            assertFalse(allows(prestige, source, progress(100, 19)));
            assertTrue(allows(prestige, source, progress(0, 20)));
        }
    }

    @Test
    void recoveryUsesOperationalHistoryInsteadOfMrbStanding() {
        assertFalse(allows(advanced, FactionEquipmentSource.RECOVERY,
                progress(4, 100)));
        assertTrue(allows(advanced, FactionEquipmentSource.RECOVERY,
                progress(5, -100)));
        assertFalse(allows(prestige, FactionEquipmentSource.RECOVERY,
                progress(14, 100)));
        assertTrue(allows(prestige, FactionEquipmentSource.RECOVERY,
                progress(15, -100)));
    }

    @Test
    void presentationThresholdsComeFromTheSamePolicyAsFiltering() {
        assertEquals(5, EquipmentAcquisitionEligibility.requiredMrb(
                EquipmentAccessTier.ADVANCED));
        assertEquals(20, EquipmentAcquisitionEligibility.requiredMrb(
                EquipmentAccessTier.PRESTIGE));
        assertEquals(5, EquipmentAcquisitionEligibility.requiredRecoveryVictories(
                EquipmentAccessTier.ADVANCED));
        assertEquals(15, EquipmentAcquisitionEligibility.requiredRecoveryVictories(
                EquipmentAccessTier.PRESTIGE));
        assertEquals(EquipmentAccessTier.ADVANCED,
                EquipmentAcquisitionEligibility.licensedTier(progress(0, 5)));
        assertEquals(EquipmentAccessTier.PRESTIGE,
                EquipmentAcquisitionEligibility.recoveryTier(progress(15, -100)));
    }

    private static EquipmentAcquisitionEligibility.Progress progress(
            int victories, int mrbRep) {
        return new EquipmentAcquisitionEligibility.Progress(victories, mrbRep);
    }

    private static boolean allows(EquipmentTemplateCard card,
                                  FactionEquipmentSource source,
                                  EquipmentAcquisitionEligibility.Progress progress) {
        return EquipmentAcquisitionEligibility.allows(card, source, progress);
    }
}
