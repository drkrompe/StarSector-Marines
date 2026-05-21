package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Initial seeding of the houses graph from vanilla sector state. Idempotent —
 * does nothing if {@link CampaignState#houseCount} > 0.
 *
 * <p>Seeds one {@link HouseRank#TIER_1} house per market and one
 * {@link HouseRank#TIER_2} house per inhabited system. Flavor is derived
 * deterministically from the market/system id hash, so re-running on an empty
 * state always produces the same houses.
 *
 * <p>Stakes are <em>not</em> seeded here — they emerge from contract resolution
 * later. A freshly-seeded house owns no industries; everything starts as
 * faction-baseline.
 */
public final class HouseSeeder {

    private static final Logger LOG = Global.getLogger(HouseSeeder.class);

    private HouseSeeder() {}

    public static void seed(CampaignState state) {
        if (state.houseCount > 0) {
            LOG.info("HouseSeeder: state already populated (houseCount=" + state.houseCount + ") — skipping seed");
            return;
        }

        List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
        Set<String> seededSystems = new HashSet<>();

        int t1 = 0;
        int t2 = 0;

        for (MarketAPI market : markets) {
            if (market == null || market.isHidden()) continue;

            int marketIdx  = state.marketRegistry.intern(market.getId());
            int factionIdx = state.factionRegistry.intern(market.getFactionId());

            HouseFlavor flavor = deterministicFlavor(market.getId());
            String displayName = market.getName() + " " + flavor.name().toLowerCase() + " house";

            state.addHouse(marketIdx, factionIdx, flavor, HouseRank.TIER_1, HouseStatus.ACTIVE, displayName);
            t1++;

            StarSystemAPI system = market.getStarSystem();
            if (system != null) {
                String sysId = system.getId();
                if (seededSystems.add(sysId)) {
                    HouseFlavor sysFlavor = deterministicFlavor(sysId);
                    String sysDisplay = system.getName() + " " + sysFlavor.name().toLowerCase() + " house";
                    state.addHouse(marketIdx, factionIdx, sysFlavor, HouseRank.TIER_2,
                            HouseStatus.ACTIVE, sysDisplay);
                    t2++;
                }
            }
        }

        LOG.info("HouseSeeder: seeded " + t1 + " Tier-1 houses (one per market) and "
                + t2 + " Tier-2 houses (one per inhabited system)");
    }

    /**
     * Deterministic flavor pick from a string id. Splaying the hash by mixing
     * a constant gives a flatter distribution across the 4 flavors than naked
     * String.hashCode() (which tends to cluster low bits on similar prefixes).
     */
    private static HouseFlavor deterministicFlavor(String id) {
        int h = id.hashCode();
        h ^= (h >>> 16);
        h *= 0x85ebca6b;
        h ^= (h >>> 13);
        int pick = Math.floorMod(h, HouseFlavor.values().length);
        return HouseFlavor.values()[pick];
    }
}
