package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrize;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultTheme;
import com.dillon.starsectormarines.i18n.Strings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RuinsPrizeWordingTest {

    private static final RuinsVaultPrize CORE = new RuinsVaultPrize(RuinsVaultPrize.Family.AI_CORE,
            RuinsVaultTheme.SERVER_HALL, RuinsVaultPrize.BETA_CORE, 2);

    @Test
    void aPoweredVaultNamesThePrizeWholeAndAForcedOneDoesNot() {
        assertEquals("Beta Core", RuinsPrizeWording.of(CORE, true, true, id -> "Beta Core"));
        assertNotEquals("Beta Core", RuinsPrizeWording.of(CORE, false, true, id -> "Beta Core"));
    }

    @Test
    void aLostExpeditionLeavesThePrizeSealedAndUnnamed() {
        assertEquals(Strings.get("resultsScreenPrizeSealed"), RuinsPrizeWording.of(CORE, true, false, id -> "Beta Core"));
    }
}
