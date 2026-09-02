package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.StationedStrength;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

import java.util.EnumSet;

/**
 * Makes a stationed Garrison detachment count toward the protected market's vanilla
 * ground defences for exactly its term ({@code contracts-nouns.md}, law 11).
 *
 * <p>One attributable flat modifier per active Garrison contract, keyed by contract id,
 * reapplied every day and removed the day the contract leaves its active states. The
 * write is deliberately narrow: the ground-defence stat and nothing else. Market
 * ownership, stability and industries remain vanilla's to settle.
 *
 * <p><b>The sweep is what makes a load safe.</b> Vanilla persists the stat with the
 * market, so a modifier applied on an earlier day is normally still standing — but a
 * contract row can be compacted away ({@code ContractTableCompactor}) or settle while
 * this system is not the thing that notices, and a modifier nobody owns any more would
 * defend the market forever. The persisted applied set is walked every day and any pair
 * whose contract is gone or no longer active is removed rather than trusted.
 */
public final class StationedStrengthSystem implements CampaignSystem {

    /** Modifier id prefix; the contract id makes it attributable and stable across days. */
    static final String MODIFIER_ID_PREFIX = "starsector_marines_stationed_";
    static final String MODIFIER_DESCRIPTION = "Stationed mercenary detachment";

    /** The vanilla ground-defence stat, isolated so a test can record what was written. */
    interface GroundDefenceSink {
        boolean apply(String marketId, String modifierId, float value, String description);

        boolean remove(String marketId, String modifierId);
    }

    /** The live marine roster, isolated so a test can seat a detachment without a sector. */
    interface RosterSource {
        MarineRoster roster();
    }

    private final GroundDefenceSink sink;
    private final RosterSource rosterSource;

    public StationedStrengthSystem() {
        this(new LiveGroundDefenceSink(), StationedStrengthSystem::liveRoster);
    }

    StationedStrengthSystem(GroundDefenceSink sink, RosterSource rosterSource) {
        this.sink = sink;
        this.rosterSource = rosterSource;
    }

    @Override
    public String name() {
        return "StationedStrength";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    /**
     * The value itself lands on vanilla's market stat, which is outside these tables;
     * the applied set that lets the sweep find it again is persisted contract-keyed state.
     */
    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        MarineRoster roster = rosterSource.roster();
        for (int row = 0; row < state.contractCount; row++) {
            if (!isContributing(state, row)) continue;
            long contractId = state.contractId[row];
            int marketSlot = state.contractMarketId[row];
            String marketId = state.marketRegistry.get(marketSlot);
            if (marketId == null) continue;

            float value = StationedStrength.valueFor(state, row, roster);
            if (value > 0f) {
                if (sink.apply(marketId, modifierId(contractId), value, MODIFIER_DESCRIPTION)) {
                    state.recordStationedStrength(contractId, marketSlot);
                }
            } else if (sink.remove(marketId, modifierId(contractId))) {
                state.forgetStationedStrength(contractId);
            }
        }
        sweep(state);
    }

    /** Backwards, because {@code forgetStationedStrengthAt} is swap-and-pop. */
    private void sweep(CampaignState state) {
        for (int i = state.stationedStrengthCount - 1; i >= 0; i--) {
            long contractId = state.stationedStrengthContractId[i];
            int row = state.contractIndex(contractId);
            if (row >= 0 && isContributing(state, row)) continue;
            String marketId = state.marketRegistry.get(state.stationedStrengthMarketId[i]);
            if (marketId == null || sink.remove(marketId, modifierId(contractId))) {
                state.forgetStationedStrengthAt(i);
            }
        }
    }

    private static boolean isContributing(CampaignState state, int row) {
        if (ContractType.fromByte(state.contractType[row]) != ContractType.GARRISON) return false;
        ContractState contractState = ContractState.fromByte(state.contractState[row]);
        return contractState == ContractState.ACTIVE
                || contractState == ContractState.IN_PROGRESS;
    }

    static String modifierId(long contractId) {
        return MODIFIER_ID_PREFIX + contractId;
    }

    private static MarineRoster liveRoster() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster() : null;
    }

    private static final class LiveGroundDefenceSink implements GroundDefenceSink {

        @Override
        public boolean apply(String marketId, String modifierId, float value,
                             String description) {
            MarketAPI market = market(marketId);
            if (market == null) return false;
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                    .modifyFlat(modifierId, value, description);
            return true;
        }

        @Override
        public boolean remove(String marketId, String modifierId) {
            MarketAPI market = market(marketId);
            if (market == null) return false;
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                    .unmodifyFlat(modifierId);
            return true;
        }

        private static MarketAPI market(String marketId) {
            if (marketId == null || Global.getSector() == null
                    || Global.getSector().getEconomy() == null) {
                return null;
            }
            return Global.getSector().getEconomy().getMarket(marketId);
        }
    }
}
