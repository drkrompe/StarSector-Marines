package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Initial seeding of the houses graph from vanilla sector state. Idempotent —
 * does nothing if {@link CampaignState#houseCount} > 0.
 *
 * <p>Seeds 2–4 {@link HouseRank#TIER_1} houses per market (scaled by market
 * size) plus one {@link HouseRank#TIER_2} house per inhabited system. Every
 * derivation — house count, per-house flavor / archetype / name, and the stake
 * split — is deterministic from the market/system id, so re-running on an empty
 * state always reproduces the same board. The *running* simulation then diverges
 * from this seed and persists via xstream; there is no re-derivation at load.
 *
 * <p>Multiple houses per market is load-bearing: the political layer is
 * house-vs-house on a shared industry ("House Drennar's stake transferred to
 * House Korvath"), which is unseedable with a single house per market. See
 * {@code roadmap/campaign/living-world/overview.md} §Genesis.
 *
 * <p>Stakes are seeded from {@link MarketAPI#getIndustries()}: each industry
 * gets a deterministic dominant house plus (usually) a contender, with an
 * unclaimed faction-baseline remainder — instant contested pluralities for the
 * drift / chain loops to operate on.
 */
public final class HouseSeeder {

    private static final Logger LOG = Global.getLogger(HouseSeeder.class);

    /** Max stake share (the byte column is 0..255; we keep room below the ceiling). */
    private static final int SHARE_MAX = 255;

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
        int stakes = 0;

        for (MarketAPI market : markets) {
            if (market == null || market.isHidden()) continue;

            int marketIdx  = state.marketRegistry.intern(market.getId());
            int factionIdx = state.factionRegistry.intern(market.getFactionId());

            int n = houseCountForMarket(market);
            long[] localHouses = new long[n];
            for (int k = 0; k < n; k++) {
                HouseFlavor flavor = deterministicFlavor(market.getId(), k);
                PatronArchetype archetype = deterministicArchetype(market.getId(), k, HouseRank.TIER_1);
                String displayName = deterministicName(market.getId(), k, flavor);
                localHouses[k] = state.addHouse(marketIdx, factionIdx, flavor, HouseRank.TIER_1,
                        HouseStatus.ACTIVE, archetype, displayName);
                t1++;
            }

            stakes += seedStakes(state, market, marketIdx, localHouses);

            StarSystemAPI system = market.getStarSystem();
            if (system != null) {
                String sysId = system.getId();
                if (seededSystems.add(sysId)) {
                    HouseFlavor sysFlavor = deterministicFlavor(sysId, 0);
                    PatronArchetype sysArchetype = deterministicArchetype(sysId, 0, HouseRank.TIER_2);
                    String sysDisplay = deterministicName(sysId, 0, sysFlavor);
                    state.addHouse(marketIdx, factionIdx, sysFlavor, HouseRank.TIER_2,
                            HouseStatus.ACTIVE, sysArchetype, sysDisplay);
                    t2++;
                }
            }
        }

        LOG.info("HouseSeeder: seeded " + t1 + " Tier-1 houses (2-4 per market), "
                + t2 + " Tier-2 houses (one per inhabited system), and " + stakes + " stakes");
    }

    /**
     * 2–4 houses per market, scaled by vanilla market size. Bigger markets
     * support more competing houses. Deterministic — size is stable per market.
     */
    private static int houseCountForMarket(MarketAPI market) {
        int size = market.getSize();
        int n = 2;
        if (size >= 6) n++;
        if (size >= 8) n++;
        return n;
    }

    /**
     * Seeds stakes for one market: walks {@link MarketAPI#getIndustries()} and,
     * for each, hands a dominant share to one deterministically-chosen local
     * house and a contender share to another, leaving the remainder unclaimed
     * (faction-baseline). Returns the number of stake rows created.
     *
     * <p>Shares are kept well under {@link #SHARE_MAX} so dominant + contender
     * never sum past the ceiling and there's always headroom for the drift loop
     * to push a flip later.
     */
    private static int seedStakes(CampaignState state, MarketAPI market, int marketIdx, long[] localHouses) {
        int n = localHouses.length;
        if (n == 0) return 0;

        int created = 0;
        for (Industry industry : market.getIndustries()) {
            if (industry == null || industry.getId() == null) continue;
            int industryIdx = state.industryRegistry.intern(industry.getId());

            int h = mix(market.getId().hashCode() * 31 + industry.getId().hashCode());
            int dominant = Math.floorMod(h, n);
            // Dominant share ~110..150 of 255 (≈43–59%).
            int dominantShare = 110 + Math.floorMod(h >>> 4, 41);
            state.addStake(localHouses[dominant], marketIdx, industryIdx, (short) dominantShare);
            created++;

            if (n >= 2) {
                int contender = (dominant + 1 + Math.floorMod(h >>> 12, n - 1)) % n;
                // Contender share ~50..90 (≈20–35%); capped so the pair stays under the ceiling.
                int contenderShare = 50 + Math.floorMod(h >>> 8, 41);
                contenderShare = Math.min(contenderShare, SHARE_MAX - dominantShare);
                if (contenderShare > 0) {
                    state.addStake(localHouses[contender], marketIdx, industryIdx, (short) contenderShare);
                    created++;
                }
            }
        }
        return created;
    }

    /**
     * Deterministic flavor pick from a string id and per-market house ordinal.
     * Folding the ordinal {@code k} in means houses on the same market draw
     * different flavors (a market can still legitimately host two of the same).
     * Splaying the hash gives a flatter distribution across the 4 flavors than
     * naked {@link String#hashCode()}.
     */
    private static HouseFlavor deterministicFlavor(String id, int k) {
        int h = mix(id.hashCode() + k * 0x9E3779B1);
        int pick = Math.floorMod(h, HouseFlavor.values().length);
        return HouseFlavor.values()[pick];
    }

    /**
     * Deterministic archetype pick weighted by rank and varied by per-market
     * house ordinal. Tier-1 patrons skew toward the four "rough edges"
     * archetypes (TIME_RUSHED, FALLEN_NOBLE, NEWCOMER, SUSPICIOUS) — the
     * desperate / unproven / shady clients an unknown merc actually works with.
     * Tier-2 skews toward the polished pair (ESTABLISHED, TRUE_BELIEVER).
     *
     * <p>Different mixer constant from {@link #deterministicFlavor} so flavor
     * and archetype don't correlate.
     */
    private static PatronArchetype deterministicArchetype(String id, int k, HouseRank rank) {
        int h = mix(id.hashCode() + k * 0x85EBCA77 + (rank == HouseRank.TIER_2 ? 0x9E37 : 0));
        PatronArchetype[] pool = rank == HouseRank.TIER_1 ? T1_POOL : T2_POOL;
        return pool[Math.floorMod(h, pool.length)];
    }

    /**
     * Deterministic display name from a flavor-specific surname pool, formatted
     * per flavor. The ordinal {@code k} offsets the pool index so houses on one
     * market get distinct names (k &lt; pool length, so 2–4 picks never collide).
     *
     * <p>Placeholder pools — the real per-flavor namesets live in
     * {@code roadmap/campaign/flavors/} once that authoring pass lands.
     */
    private static String deterministicName(String id, int k, HouseFlavor flavor) {
        String[] pool = NAME_POOLS[flavor.ordinal()];
        int base = mix(id.hashCode());
        String surname = pool[Math.floorMod(base + k, pool.length)];
        switch (flavor) {
            case CORPORATE:  return surname + " Holdings";
            case UNDERWORLD: return "the " + surname + " syndicate";
            case SECTARIAN:  return "the " + surname + " conclave";
            case FEUDAL:
            default:         return "House " + surname;
        }
    }

    /** Single-purpose integer hash mixer (fmix32-style) for splaying ids. */
    private static int mix(int x) {
        x ^= (x >>> 16);
        x *= 0x85EBCA6B;
        x ^= (x >>> 13);
        x *= 0xC2B2AE35;
        x ^= (x >>> 16);
        return x;
    }

    /**
     * T1 pool — desperate / unproven / morally murky. Weighted by slot count, so
     * {@link PatronArchetype#TIME_RUSHED} shows up about twice as often as
     * {@link PatronArchetype#TRUE_BELIEVER}.
     */
    private static final PatronArchetype[] T1_POOL = {
            PatronArchetype.TIME_RUSHED,  PatronArchetype.TIME_RUSHED,
            PatronArchetype.FALLEN_NOBLE, PatronArchetype.FALLEN_NOBLE,
            PatronArchetype.SUSPICIOUS,
            PatronArchetype.NEWCOMER,
            PatronArchetype.TRUE_BELIEVER,
    };

    /** T2 pool — patrons who survived to a wider stake have more polish. */
    private static final PatronArchetype[] T2_POOL = {
            PatronArchetype.ESTABLISHED,  PatronArchetype.ESTABLISHED,
            PatronArchetype.TRUE_BELIEVER,
            PatronArchetype.SUSPICIOUS,
            PatronArchetype.TIME_RUSHED,
            PatronArchetype.FALLEN_NOBLE,
    };

    /**
     * Placeholder surname pools, indexed by {@link HouseFlavor#ordinal()}
     * (CORPORATE, FEUDAL, UNDERWORLD, SECTARIAN — never reorder the enum). Eight
     * each so 2–4 houses on a market never collide. Real namesets move to
     * {@code roadmap/campaign/flavors/} when that content pass lands.
     */
    private static final String[][] NAME_POOLS = {
            // CORPORATE
            {"Meridian", "Cytherea", "Ardent", "Halcyon", "Lumen", "Tessaract", "Voss", "Pallas"},
            // FEUDAL
            {"Korvath", "Drennar", "Vashti", "Aurelian", "Sorenson", "Castellan", "Maridia", "Thorne"},
            // UNDERWORLD
            {"Kessar", "Vance", "Holloway", "Marrow", "Pale", "Drift", "Sokol", "Brand"},
            // SECTARIAN
            {"Sale", "Ashen", "Tarn", "Penitent", "Vael", "Cinder", "Hallow", "Reed"},
    };
}
