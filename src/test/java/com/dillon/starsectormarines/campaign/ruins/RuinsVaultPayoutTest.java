package com.dillon.starsectormarines.campaign.ruins;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsVaultPayoutTest {

    private static final long SEED = 77L;
    private static final String SITE = "ruins_site";

    private static RuinsVaultPrize core(String id, int stakes) {
        return new RuinsVaultPrize(RuinsVaultPrize.Family.AI_CORE, RuinsVaultTheme.SERVER_HALL, id, stakes);
    }

    private static RuinsVaultPrize nanoforge() {
        return new RuinsVaultPrize(RuinsVaultPrize.Family.COLONY_ITEM, RuinsVaultTheme.PRISTINE_NANOFORGE,
                RuinsVaultTheme.PRISTINE_NANOFORGE.itemId, 3);
    }

    @Test
    void aPoweredVaultPaysTheCoreOrItemWhole() {
        RuinsVaultPayout core = RuinsVaultPayout.of(SEED, SITE, core(RuinsVaultPrize.BETA_CORE, 2), true);
        assertEquals(RuinsVaultPrize.BETA_CORE, core.itemId());
        assertTrue(core.techCaches().isEmpty());
        RuinsVaultPayout item = RuinsVaultPayout.of(SEED, SITE, nanoforge(), true);
        assertEquals("pristine_nanoforge", item.itemId());
        assertFalse(item.broken(nanoforge()));
    }

    @Test
    void aForcedVaultBreaksItIntoOneCacheMoreThanItsStake() {
        RuinsVaultPayout alpha = RuinsVaultPayout.of(SEED, SITE, core(RuinsVaultPrize.ALPHA_CORE, 3), false);
        assertNull(alpha.itemId());
        assertEquals(4, alpha.techCaches().size());
        assertTrue(alpha.broken(core(RuinsVaultPrize.ALPHA_CORE, 3)));
        assertEquals(2, RuinsVaultPayout.of(SEED, SITE, core(RuinsVaultPrize.GAMMA_CORE, 1), false).techCaches().size());
        Set<String> ids = new HashSet<>();
        for (RuinsTechCache cache : alpha.techCaches()) ids.add(cache.findId());
        assertEquals(4, ids.size());
    }

    @Test
    void goodsAndTechPayTheSameEitherWay() {
        RuinsVaultPrize goods = new RuinsVaultPrize(RuinsVaultPrize.Family.GOODS, RuinsVaultTheme.DEPOT, null, 0);
        RuinsVaultPrize tech = new RuinsVaultPrize(RuinsVaultPrize.Family.TECH, RuinsVaultTheme.ARMORY, null, 1);
        for (RuinsVaultPrize prize : List.of(goods, tech)) {
            assertEquals(RuinsVaultPayout.of(SEED, SITE, prize, true), RuinsVaultPayout.of(SEED, SITE, prize, false));
        }
        assertEquals(RuinsVaultPayout.GOODS_BUNDLES, RuinsVaultPayout.of(SEED, SITE, goods, false).goodsBundles());
        assertEquals(RuinsVaultPayout.TECH_CACHES, RuinsVaultPayout.of(SEED, SITE, tech, false).techCaches().size());
    }

    @Test
    void aPayoutIsNeverEmpty() {
        for (RuinsVaultTheme theme : RuinsVaultTheme.values()) {
            RuinsVaultPrize prize = new RuinsVaultPrize(theme.family, theme,
                    theme.family == RuinsVaultPrize.Family.AI_CORE ? RuinsVaultPrize.GAMMA_CORE : theme.itemId, 0);
            for (boolean powered : new boolean[] {true, false}) {
                RuinsVaultPayout payout = RuinsVaultPayout.of(SEED, SITE, prize, powered);
                assertTrue(payout.itemId() != null || !payout.techCaches().isEmpty() || payout.goodsBundles() > 0);
            }
        }
    }

    @Test
    void aPayoutNeedsAPrizeAndASite() {
        assertThrows(IllegalArgumentException.class, () -> RuinsVaultPayout.of(SEED, SITE, null, true));
        assertThrows(IllegalArgumentException.class, () -> RuinsVaultPayout.of(SEED, null, nanoforge(), true));
    }
}
