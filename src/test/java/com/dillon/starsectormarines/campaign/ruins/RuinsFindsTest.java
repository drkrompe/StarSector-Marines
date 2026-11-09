package com.dillon.starsectormarines.campaign.ruins;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The find tiers, tech caches and the old colony's batteries. */
class RuinsFindsTest {

    @Test
    void theRouteHoldsSupplyAndKeptPlacesHoldTech() {
        assertEquals(RuinsFindTier.SUPPLY, RuinsFindTier.Place.ROUTE.tier);
        assertEquals(RuinsFindTier.TECH, RuinsFindTier.Place.SEALED_STORE.tier);
        assertEquals(RuinsFindTier.TECH, RuinsFindTier.Place.GUARDIAN_ROOM.tier);
    }

    @Test
    void theVaultStoreHoldsOneTechCacheOnItsDeepestLayer() {
        for (int layers = 1; layers <= 4; layers++) {
            int tech = 0;
            for (int layer = 1; layer <= layers; layer++) {
                if (RuinsFindTier.vaultStoreTier(layer, layers) == RuinsFindTier.TECH) tech++;
            }
            assertEquals(1, tech);
            assertEquals(RuinsFindTier.TECH, RuinsFindTier.vaultStoreTier(layers, layers));
        }
        assertThrows(IllegalArgumentException.class, () -> RuinsFindTier.vaultStoreTier(3, 2));
    }

    @Test
    void aTechCacheHoldsTheSameGroupEveryVisitAndLeansToWeapons() {
        Map<String, Integer> groups = new HashMap<>();
        for (int i = 0; i < 2100; i++) {
            RuinsTechCache cache = RuinsTechCache.of(5L, "site_" + i, "store-1");
            assertEquals(cache, RuinsTechCache.of(5L, "site_" + i, "store-1"));
            groups.merge(cache.dropGroup(), 1, Integer::sum);
        }
        assertEquals(3, groups.size());
        int weapons = groups.get(RuinsTechCache.WEAPONS);
        assertTrue(weapons > 1300 && weapons < 1700, "weapons " + weapons);
    }

    @Test
    void onlyACoreOrItemSiteKeepsBatteriesAndTheGradeSetsHowMany() {
        RuinsVaultPrize goods = new RuinsVaultPrize(RuinsVaultPrize.Family.GOODS, RuinsVaultTheme.DEPOT, null, 0);
        RuinsVaultPrize tech = new RuinsVaultPrize(RuinsVaultPrize.Family.TECH, RuinsVaultTheme.ARMORY, null, 1);
        RuinsVaultPrize core = new RuinsVaultPrize(RuinsVaultPrize.Family.AI_CORE, RuinsVaultTheme.SERVER_HALL,
                RuinsVaultPrize.GAMMA_CORE, 1);
        assertEquals(0, RuinsBatteries.count(RuinsGrade.VAST, goods));
        assertEquals(0, RuinsBatteries.count(RuinsGrade.VAST, tech));
        assertEquals(1, RuinsBatteries.count(RuinsGrade.SCATTERED, core));
        assertEquals(4, RuinsBatteries.count(RuinsGrade.VAST, core));
    }

    @Test
    void batteriesWakeWithTheHallUnlessTheirBranchIsCut() {
        assertEquals(RuinsBatteries.State.DARK, RuinsBatteries.state(false, true));
        assertEquals(RuinsBatteries.State.DARK, RuinsBatteries.state(false, false));
        assertEquals(RuinsBatteries.State.AWAKE, RuinsBatteries.state(true, true));
        assertEquals(RuinsBatteries.State.CUT, RuinsBatteries.state(true, false));
    }
}
