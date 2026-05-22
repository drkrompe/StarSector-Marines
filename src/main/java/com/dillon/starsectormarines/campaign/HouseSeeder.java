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
            PatronArchetype archetype = deterministicArchetype(market.getId(), HouseRank.TIER_1);
            String displayName = market.getName() + " " + flavor.name().toLowerCase() + " house";

            state.addHouse(marketIdx, factionIdx, flavor, HouseRank.TIER_1, HouseStatus.ACTIVE,
                    archetype, displayName);
            t1++;

            StarSystemAPI system = market.getStarSystem();
            if (system != null) {
                String sysId = system.getId();
                if (seededSystems.add(sysId)) {
                    HouseFlavor sysFlavor = deterministicFlavor(sysId);
                    PatronArchetype sysArchetype = deterministicArchetype(sysId, HouseRank.TIER_2);
                    String sysDisplay = system.getName() + " " + sysFlavor.name().toLowerCase() + " house";
                    state.addHouse(marketIdx, factionIdx, sysFlavor, HouseRank.TIER_2,
                            HouseStatus.ACTIVE, sysArchetype, sysDisplay);
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

    /**
     * Deterministic archetype pick weighted by rank. Tier-1 patrons skew
     * toward the four "rough edges" archetypes (TIME_RUSHED, FALLEN_NOBLE,
     * NEWCOMER, SUSPICIOUS) — these are the desperate / unproven / shady
     * clients an unknown merc would actually be working with. Tier-2 skews
     * toward the polished pair (ESTABLISHED, TRUE_BELIEVER) plus the
     * sustained-presence three from T1.
     *
     * <p>Different mixer constant from {@link #deterministicFlavor} so
     * flavor and archetype don't correlate (same market shouldn't always
     * pair the same flavor with the same archetype).
     */
    private static PatronArchetype deterministicArchetype(String id, HouseRank rank) {
        int h = id.hashCode() + (rank == HouseRank.TIER_2 ? 0x9E37 : 0);
        h ^= (h >>> 16);
        h *= 0xC2B2AE35;
        h ^= (h >>> 13);
        PatronArchetype[] pool = rank == HouseRank.TIER_1 ? T1_POOL : T2_POOL;
        return pool[Math.floorMod(h, pool.length)];
    }

    /**
     * T1 pool — desperate / unproven / morally murky. Each appears with
     * weight matching its slot count, so {@link PatronArchetype#TIME_RUSHED}
     * shows up about twice as often as {@link PatronArchetype#TRUE_BELIEVER}.
     * Tuned to make early-game work read mostly as scrappy-with-occasional-
     * ideologue rather than uniform.
     */
    private static final PatronArchetype[] T1_POOL = {
            PatronArchetype.TIME_RUSHED,  PatronArchetype.TIME_RUSHED,
            PatronArchetype.FALLEN_NOBLE, PatronArchetype.FALLEN_NOBLE,
            PatronArchetype.SUSPICIOUS,
            PatronArchetype.NEWCOMER,
            PatronArchetype.TRUE_BELIEVER,
    };

    /**
     * T2 pool — patrons who survived to a wider stake have more polish.
     * ESTABLISHED dominates; the rougher archetypes still appear (a
     * Director can be desperate too) at reduced weight.
     */
    private static final PatronArchetype[] T2_POOL = {
            PatronArchetype.ESTABLISHED,  PatronArchetype.ESTABLISHED,
            PatronArchetype.TRUE_BELIEVER,
            PatronArchetype.SUSPICIOUS,
            PatronArchetype.TIME_RUSHED,
            PatronArchetype.FALLEN_NOBLE,
    };
}
