package com.dillon.starsectormarines.campaign.ruins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsPrizeGrantTest {

    private static final long SEED = 11L;
    private static final String SITE = "ruins_site";

    private static List<RuinsPrizeGrant> grants(RuinsVaultPrize prize, boolean powered) {
        return RuinsPrizeGrant.of(RuinsVaultPayout.of(SEED, SITE, prize, powered), prize, SEED, SITE);
    }

    @Test
    void aWholeColonyItemIsASpecialItemAndAWholeCoreACommodity() {
        RuinsVaultPrize item = new RuinsVaultPrize(RuinsVaultPrize.Family.COLONY_ITEM, RuinsVaultTheme.SYNCHROTRON,
                "synchrotron", 2);
        assertEquals(List.of(new RuinsPrizeGrant(RuinsPrizeGrant.Kind.SPECIAL_ITEM, "synchrotron", 1, 0L)),
                grants(item, true));
        RuinsVaultPrize core = new RuinsVaultPrize(RuinsVaultPrize.Family.AI_CORE, RuinsVaultTheme.SERVER_HALL,
                RuinsVaultPrize.ALPHA_CORE, 3);
        assertEquals(List.of(new RuinsPrizeGrant(RuinsPrizeGrant.Kind.COMMODITY, RuinsVaultPrize.ALPHA_CORE, 1, 0L)),
                grants(core, true));
    }

    @Test
    void aBrokenPrizeIsTechRollsEachWithItsOwnSeed() {
        RuinsVaultPrize core = new RuinsVaultPrize(RuinsVaultPrize.Family.AI_CORE, RuinsVaultTheme.SERVER_HALL,
                RuinsVaultPrize.BETA_CORE, 2);
        List<RuinsPrizeGrant> broken = grants(core, false);
        assertEquals(3, broken.size());
        for (RuinsPrizeGrant grant : broken) assertEquals(RuinsPrizeGrant.Kind.TECH_ROLL, grant.kind());
        assertNotEquals(broken.get(0).seed(), broken.get(1).seed());
        assertEquals(broken, grants(core, false));
    }

    @Test
    void eachTechFindRollsItsOwnGroupFromTheSiteAndItsId() {
        List<RuinsPrizeGrant> rolls = RuinsPrizeGrant.ofTechFinds(List.of("sealed-store-1", "guardian-hive"), SEED, SITE);
        assertEquals(2, rolls.size());
        for (RuinsPrizeGrant roll : rolls) assertEquals(RuinsPrizeGrant.Kind.TECH_ROLL, roll.kind());
        assertEquals(RuinsTechCache.of(SEED, SITE, "guardian-hive").dropGroup(), rolls.get(1).id());
        assertNotEquals(rolls.get(0).seed(), rolls.get(1).seed());
        assertEquals(rolls, RuinsPrizeGrant.ofTechFinds(List.of("sealed-store-1", "guardian-hive"), SEED, SITE));
    }

    @Test
    void goodsPayBundlesOfTheFieldResources() {
        RuinsVaultPrize goods = new RuinsVaultPrize(RuinsVaultPrize.Family.GOODS, RuinsVaultTheme.DEPOT, null, 0);
        List<RuinsPrizeGrant> paid = grants(goods, false);
        assertEquals(RuinsVaultPayout.GOODS_BUNDLES, paid.size());
        for (RuinsPrizeGrant grant : paid) {
            assertEquals(RuinsPrizeGrant.Kind.COMMODITY, grant.kind());
            assertTrue(grant.quantity() > 0);
        }
    }
}
