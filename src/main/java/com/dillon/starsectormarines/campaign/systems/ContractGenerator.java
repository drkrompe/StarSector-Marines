package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractEligibility;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.EnumSet;
import java.util.Random;

/**
 * Tick phase 3a: produces fresh contract offers for the player to pick up.
 *
 * <p>Walks active Tier 1-3 patron houses; each rolls a small daily chance to put
 * a rank-gated contract offer on the table.
 * Offers land in {@link CampaignState#contractId contracts[]} with state
 * {@link ContractState#OFFERED OFFERED} and remain eligible until acceptance or
 * expiry policy advances them.
 *
 * <p>Four caps prevent runaway generation and local mission-board saturation:
 * <ul>
 *   <li>{@link #PER_PATRON_OFFER_CAP} — a patron with an outstanding offer doesn't
 *       generate a second one (forces the player to either accept, decline, or
 *       wait — beats a backlog of zombie offers).</li>
 *   <li>{@link #PER_MARKET_OFFER_CAP} — a market presents one ordinary opportunity,
 *       rather than one from every house based there.</li>
 *   <li>{@link #PER_SYSTEM_OFFER_CAP} — nearby markets share a small opportunity
 *       field, so a populated system can hold a handful of jobs without every
 *       planet becoming a contract board.</li>
 *   <li>{@link #GLOBAL_OFFER_CAP} — sector-wide ceiling so the contracts table
 *       doesn't bloat in long games.</li>
 * </ul>
 *
 * <p>RNG is seeded from {@code (day, houseId)} so the same tick reproduces the
 * same rolls — important for save reproducibility.
 *
 * <p>STRIKE, ESCORT, GARRISON, CADRE, and Tier-3 PLANETARY_ASSAULT are supported.
 */
public final class ContractGenerator implements CampaignSystem {

    /** Daily per-patron chance to put a new offer on the table. ~20-day mean inter-arrival. */
    private static final float OFFER_CHANCE_PER_DAY = 0.05f;

    /** Outstanding OFFERED contracts a single patron is allowed to have queued. */
    private static final int PER_PATRON_OFFER_CAP = 1;

    /** Outstanding offers whose meeting/origin is one market. */
    private static final int PER_MARKET_OFFER_CAP = 1;

    /** Outstanding offers across all markets in one star system. */
    private static final int PER_SYSTEM_OFFER_CAP = 3;

    /** Sector-wide cap on OFFERED contracts. */
    private static final int GLOBAL_OFFER_CAP = 20;

    interface MarketSource {
        /** Stable star-system id for a market, or {@code null} when unavailable. */
        String systemId(String marketId);
    }

    private final MarketSource markets;

    public ContractGenerator() {
        this(new SectorMarkets());
    }

    ContractGenerator(MarketSource markets) {
        this.markets = markets;
    }

    @Override
    public String name() {
        return "ContractGenerator";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.HOUSES, CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        int globalOffers = countOpenOffers(state);
        if (globalOffers >= GLOBAL_OFFER_CAP) return;

        for (int i = 0; i < state.houseCount; i++) {
            HouseRank rank = HouseRank.fromByte(state.houseRank[i]);
            if (rank == HouseRank.TIER_4) continue;
            if (HouseStatus.fromByte(state.houseStatus[i]) != HouseStatus.ACTIVE) continue;

            long patronId = state.houseId[i];
            if (!ContractEligibility.patronEligible(state, patronId)) continue;
            if (countOpenOffersForPatron(state, patronId) >= PER_PATRON_OFFER_CAP) continue;
            int originMarket = state.houseMarketId[i];
            if (countOpenOffersForMarket(state, originMarket) >= PER_MARKET_OFFER_CAP) continue;
            String originSystem = systemKey(state, originMarket);
            if (originSystem != null
                    && countOpenOffersForSystem(state, originSystem) >= PER_SYSTEM_OFFER_CAP) {
                continue;
            }

            long seed = ((long) day << 32) ^ patronId;
            Random r = new Random(seed);
            if (r.nextFloat() >= OFFER_CHANCE_PER_DAY) continue;

            ContractOfferTemplate template = ContractOfferTemplate.roll(rank, r);
            if (template == null) continue;

            long targetHouseId = template.type.isStationing() ? -1L : pickTarget(state, i, r);
            if (!template.type.isStationing() && targetHouseId == -1L) continue;

            // Offer-lapse window driven by patron archetype — TIME_RUSHED gives
            // the player days, ESTABLISHED takes its time. Shares the (day, patronId)
            // seed so re-rolls produce the same window for the same offer.
            PatronArchetype archetype = PatronArchetype.fromByte(state.houseArchetype[i]);
            int offerExpiresTick = day + archetype.rollOfferWindowDays(r);

            state.addContract(
                    patronId,
                    targetHouseId,
                    -1L,                                  // no parent chain for first-cut
                    template.type,
                    ContractState.OFFERED,
                    day,
                    -1,                                   // no acceptance-side expiry for mission-mode
                    offerExpiresTick,                     // offer lapses on this day if unaccepted
                    template.phasesTotal,
                    -1,                                   // captain assigned at acceptance
                    originMarket,                         // patron's market is the meeting/origin
                    -1,                                   // industryId resolved at acceptance
                    template.payout,
                    0,                                    // retainer per month = 0 for mission-mode
                    template.salvageBaseline,
                    template.salvageBaseline,             // negotiated defaults to baseline at offer
                    (byte) 100                            // cashMultiplier baseline
            );

            globalOffers++;
            if (globalOffers >= GLOBAL_OFFER_CAP) return;
        }
    }

    /**
     * Picks a random active Tier 1-3 house other than the patron itself. Returns
     * {@code -1L} when no valid target exists (early-sector seed where there's
     * only one standard-contract patron).
     */
    private static long pickTarget(CampaignState state, int patronRow, Random r) {
        long patronId = state.houseId[patronRow];
        int candidates = 0;
        for (int j = 0; j < state.houseCount; j++) {
            if (j == patronRow) continue;
            if (HouseRank.fromByte(state.houseRank[j]) == HouseRank.TIER_4) continue;
            if (HouseStatus.fromByte(state.houseStatus[j]) != HouseStatus.ACTIVE) continue;
            candidates++;
        }
        if (candidates == 0) return -1L;

        int pick = r.nextInt(candidates);
        int seen = 0;
        for (int j = 0; j < state.houseCount; j++) {
            if (j == patronRow) continue;
            if (HouseRank.fromByte(state.houseRank[j]) == HouseRank.TIER_4) continue;
            if (HouseStatus.fromByte(state.houseStatus[j]) != HouseStatus.ACTIVE) continue;
            if (seen++ == pick) return state.houseId[j];
        }
        return patronId; // unreachable; satisfies compiler
    }

    private static int countOpenOffers(CampaignState state) {
        int n = 0;
        for (int i = 0; i < state.contractCount; i++) {
            if (ContractState.fromByte(state.contractState[i]) == ContractState.OFFERED) n++;
        }
        return n;
    }

    private static int countOpenOffersForPatron(CampaignState state, long patronId) {
        int n = 0;
        for (int i = 0; i < state.contractCount; i++) {
            if (state.contractPatronHouseId[i] != patronId) continue;
            if (ContractState.fromByte(state.contractState[i]) == ContractState.OFFERED) n++;
        }
        return n;
    }

    private static int countOpenOffersForMarket(CampaignState state, int marketSlot) {
        int n = 0;
        for (int i = 0; i < state.contractCount; i++) {
            if (state.contractMarketId[i] != marketSlot) continue;
            if (ContractState.fromByte(state.contractState[i]) == ContractState.OFFERED) n++;
        }
        return n;
    }

    private int countOpenOffersForSystem(CampaignState state, String systemKey) {
        int n = 0;
        for (int i = 0; i < state.contractCount; i++) {
            if (ContractState.fromByte(state.contractState[i]) != ContractState.OFFERED) continue;
            if (systemKey.equals(systemKey(state, state.contractMarketId[i]))) n++;
        }
        return n;
    }

    private String systemKey(CampaignState state, int marketSlot) {
        if (marketSlot < 0) return null;
        String marketId = state.marketRegistry.get(marketSlot);
        String systemId = markets != null ? markets.systemId(marketId) : null;
        // A missing live market should not disable the per-market ceiling or make
        // offer generation fail on a legacy/test state. Treat it as an isolated
        // venue until the live topology can resolve it again.
        return systemId != null ? systemId : "market:" + marketSlot;
    }

    private static final class SectorMarkets implements MarketSource {
        @Override
        public String systemId(String marketId) {
            if (marketId == null || Global.getSector() == null) return null;
            MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
            if (market == null) return null;
            StarSystemAPI system = market.getStarSystem();
            return system != null ? system.getId() : null;
        }
    }
}
