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
        EquipmentAcquisitionEligibility.Progress veteran = progress();

        assertTrue(allows(common, FactionEquipmentSource.MARKET, veteran));
        assertFalse(allows(advanced, FactionEquipmentSource.MARKET, veteran));
        assertFalse(allows(prestige, FactionEquipmentSource.MARKET, veteran));
    }

    @Test
    void licencesAndPatronRewardsAskNoReputationOfAnyTier() {
        for (FactionEquipmentSource source : new FactionEquipmentSource[]{
                FactionEquipmentSource.LICENSE, FactionEquipmentSource.PATRON}) {
            assertTrue(allows(common, source, progress()));
            assertTrue(allows(advanced, source, progress()));
            assertTrue(allows(prestige, source, progress()));
        }
    }

    private static EquipmentAcquisitionEligibility.Progress progress() {
        return EquipmentAcquisitionEligibility.Progress.complete();
    }

    private static boolean allows(EquipmentTemplateCard card,
                                  FactionEquipmentSource source,
                                  EquipmentAcquisitionEligibility.Progress progress) {
        return EquipmentAcquisitionEligibility.allows(card, source, progress);
    }
}
