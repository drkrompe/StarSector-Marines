package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.StrandedDetachment;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import java.util.EnumSet;
import java.util.function.Supplier;

/**
 * Chooses the recovery for each defaulted stationing assignment, once.
 *
 * <p>On a market not hostile to the player the detachment waits for a pickup
 * ({@link StrandedDetachment#strandOnBreach}): breach is routine where houses fall often,
 * Nexerelin campaigns above all, and routine recovery should not demand a fight. On a
 * hostile market, or where hostility cannot be read, the system creates the fought
 * extraction offer as before. The choice is frozen by whichever record it leaves — the
 * stranded mark or the extraction row — so a later diplomatic swing cannot turn a
 * promised pickup into a battle or the reverse.
 */
public final class StationingDefaultExtractionSystem implements CampaignSystem {

    /** Whether the player may land at a market without a fight. */
    interface HostilitySource {
        /** @return true or false, or null when it cannot be read */
        Boolean hostileToPlayer(String marketId);
    }

    private final Supplier<MarineRoster> rosterSource;
    private final HostilitySource hostility;

    public StationingDefaultExtractionSystem() {
        this(StationingDefaultExtractionSystem::liveRoster,
                StationingDefaultExtractionSystem::liveHostility);
    }

    StationingDefaultExtractionSystem(Supplier<MarineRoster> rosterSource,
                                      HostilitySource hostility) {
        this.rosterSource = rosterSource;
        this.hostility = hostility;
    }

    @Override
    public String name() {
        return "StationingDefaultExtraction";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        int parentsAtStart = state.contractCount;
        for (int row = 0; row < parentsAtStart; row++) {
            if (ContractState.fromByte(state.contractState[row]) != ContractState.DEFAULTED) continue;
            if (!ContractType.fromByte(state.contractType[row]).isStationing()) continue;
            if (state.contractMarinesCommitted[row] <= 0 && state.contractCaptainId[row] < 0) continue;

            long parentId = state.contractId[row];
            if (hasFollowup(state, parentId)) continue;
            if (StrandedDetachment.isStranded(state, row)) continue;
            Boolean hostile = hostility.hostileToPlayer(
                    state.marketRegistry.get(state.contractMarketId[row]));
            if (Boolean.FALSE.equals(hostile)
                    && StrandedDetachment.strandOnBreach(state, row, rosterSource.get(), day)) {
                continue;
            }

            long extractionId = state.addContract(
                    state.contractPatronHouseId[row], -1L, -1L,
                    ContractType.EXTRACTION, ContractState.OFFERED,
                    day, -1, -1, (byte) 1, -1,
                    state.contractMarketId[row], state.contractIndustryId[row],
                    state.contractRetainerPerMonth[row], 0,
                    state.contractSalvageBaseline[row], state.contractSalvageNegotiated[row],
                    (byte) 100);
            int extractionRow = state.contractIndex(extractionId);
            state.contractSourceContractId[extractionRow] = parentId;
        }
    }

    private static MarineRoster liveRoster() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster() : null;
    }

    static Boolean liveHostility(String marketId) {
        if (marketId == null || Global.getSector() == null
                || Global.getSector().getEconomy() == null) {
            return null;
        }
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null || market.getFaction() == null) return null;
        return market.getFaction().isHostileTo(Factions.PLAYER);
    }

    private static boolean hasFollowup(CampaignState state, long parentId) {
        for (int row = 0; row < state.contractCount; row++) {
            if (state.contractSourceContractId[row] == parentId
                    && ContractType.fromByte(state.contractType[row]) == ContractType.EXTRACTION) {
                return true;
            }
        }
        return false;
    }
}
