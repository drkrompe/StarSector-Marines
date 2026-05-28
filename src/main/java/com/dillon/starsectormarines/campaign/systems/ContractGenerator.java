package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseStatus;
import com.dillon.starsectormarines.campaign.PatronArchetype;

import java.util.EnumSet;
import java.util.Random;

/**
 * Tick phase 3a: produces fresh contract offers for the player to pick up.
 *
 * <p>Walks {@link HouseRank#TIER_1 T1} active patron houses; each rolls a small
 * daily chance to put a {@link ContractType#STRIKE STRIKE} offer on the table.
 * Offers land in {@link CampaignState#contractId contracts[]} with state
 * {@link ContractState#OFFERED OFFERED} — the briefing UI flips OFFERED → ACTIVE
 * on acceptance.
 *
 * <p>Two caps prevent runaway generation:
 * <ul>
 *   <li>{@link #PER_PATRON_OFFER_CAP} — a patron with an outstanding offer doesn't
 *       generate a second one (forces the player to either accept, decline, or
 *       wait — beats a backlog of zombie offers).</li>
 *   <li>{@link #GLOBAL_OFFER_CAP} — sector-wide ceiling so the contracts table
 *       doesn't bloat in long games.</li>
 * </ul>
 *
 * <p>RNG is seeded from {@code (day, houseId)} so the same tick reproduces the
 * same rolls — important for save reproducibility.
 *
 * <p>Strike-only at v1. PLANETARY_ASSAULT / ESCORT / GARRISON / CADRE follow
 * once Strike's resolution loop is proven through playtest.
 */
public final class ContractGenerator implements CampaignSystem {

    /** Daily per-patron chance to put a new offer on the table. ~20-day mean inter-arrival. */
    private static final float OFFER_CHANCE_PER_DAY = 0.05f;

    /** Outstanding OFFERED contracts a single patron is allowed to have queued. */
    private static final int PER_PATRON_OFFER_CAP = 1;

    /** Sector-wide cap on OFFERED contracts. */
    private static final int GLOBAL_OFFER_CAP = 20;

    /** Default T1 Strike baseline payout in credits. Per economy.md, T1 = 1× baseline. */
    private static final int T1_STRIKE_PAYOUT = 25_000;

    /** Default Strike-Raid salvage cap (per contracts/overview.md §"Salvage Layer 1"). */
    private static final byte STRIKE_RAID_SALVAGE_BASELINE = 60;

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
            if (HouseRank.fromByte(state.houseRank[i]) != HouseRank.TIER_1) continue;
            if (HouseStatus.fromByte(state.houseStatus[i]) != HouseStatus.ACTIVE) continue;

            long patronId = state.houseId[i];
            if (countOpenOffersForPatron(state, patronId) >= PER_PATRON_OFFER_CAP) continue;

            long seed = ((long) day << 32) ^ patronId;
            Random r = new Random(seed);
            if (r.nextFloat() >= OFFER_CHANCE_PER_DAY) continue;

            long targetHouseId = pickStrikeTarget(state, i, r);
            if (targetHouseId == -1L) continue;

            // Offer-lapse window driven by patron archetype — TIME_RUSHED gives
            // the player days, ESTABLISHED takes its time. Shares the (day, patronId)
            // seed so re-rolls produce the same window for the same offer.
            PatronArchetype archetype = PatronArchetype.fromByte(state.houseArchetype[i]);
            int offerExpiresTick = day + archetype.rollOfferWindowDays(r);

            state.addContract(
                    patronId,
                    targetHouseId,
                    -1L,                                  // no parent chain for first-cut
                    ContractType.STRIKE,
                    ContractState.OFFERED,
                    day,
                    -1,                                   // no acceptance-side expiry for mission-mode
                    offerExpiresTick,                     // offer lapses on this day if unaccepted
                    (byte) 1,                             // phasesTotal = 1 for STRIKE
                    -1,                                   // captain assigned at acceptance
                    state.houseMarketId[i],               // patron's market is the meeting/origin
                    -1,                                   // industryId resolved at acceptance
                    T1_STRIKE_PAYOUT,
                    0,                                    // retainer per month = 0 for mission-mode
                    STRIKE_RAID_SALVAGE_BASELINE,
                    STRIKE_RAID_SALVAGE_BASELINE,         // negotiated defaults to baseline at offer
                    (byte) 100                            // cashMultiplier baseline
            );

            globalOffers++;
            if (globalOffers >= GLOBAL_OFFER_CAP) return;
        }
    }

    /**
     * Picks a random T1 active house other than the patron itself. Returns
     * {@code -1L} when no valid target exists (early-sector seed where there's
     * only one T1 patron).
     */
    private static long pickStrikeTarget(CampaignState state, int patronRow, Random r) {
        long patronId = state.houseId[patronRow];
        int candidates = 0;
        for (int j = 0; j < state.houseCount; j++) {
            if (j == patronRow) continue;
            if (HouseRank.fromByte(state.houseRank[j]) != HouseRank.TIER_1) continue;
            if (HouseStatus.fromByte(state.houseStatus[j]) != HouseStatus.ACTIVE) continue;
            candidates++;
        }
        if (candidates == 0) return -1L;

        int pick = r.nextInt(candidates);
        int seen = 0;
        for (int j = 0; j < state.houseCount; j++) {
            if (j == patronRow) continue;
            if (HouseRank.fromByte(state.houseRank[j]) != HouseRank.TIER_1) continue;
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
}
