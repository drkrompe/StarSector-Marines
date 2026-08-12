package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Detects protected markets whose live faction no longer matches their patron. */
public final class InternalFlipGarrisonSystem implements CampaignSystem {

    interface OwnershipSource {
        List<MarketOwnership> currentOwnership(CampaignState state);
    }

    static final class MarketOwnership {
        final long eventKey;
        final int marketId;
        final int factionId;

        MarketOwnership(long eventKey, int marketId, int factionId) {
            this.eventKey = eventKey;
            this.marketId = marketId;
            this.factionId = factionId;
        }
    }

    private final OwnershipSource ownership;

    public InternalFlipGarrisonSystem() {
        this(InternalFlipGarrisonSystem::readLiveOwnership);
    }

    InternalFlipGarrisonSystem(OwnershipSource ownership) {
        this.ownership = ownership;
    }

    @Override
    public String name() {
        return "InternalFlipGarrison";
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
        List<MarketOwnership> markets = ownership.currentOwnership(state);
        if (markets == null) return;
        for (MarketOwnership market : markets) {
            if (market == null) continue;
            GarrisonDefenseTrigger.arm(state, market.eventKey, market.marketId,
                    GarrisonDefenseTriggerType.INTERNAL_FLIP, -1L,
                    market.factionId, day);
        }
    }

    private static List<MarketOwnership> readLiveOwnership(CampaignState state) {
        if (state == null || Global.getSector() == null
                || Global.getSector().getEconomy() == null) {
            return Collections.emptyList();
        }
        Set<Integer> seenMarkets = new HashSet<>();
        List<MarketOwnership> out = new ArrayList<>();
        for (int row = 0; row < state.contractCount; row++) {
            if (ContractType.fromByte(state.contractType[row]) != ContractType.GARRISON
                    || ContractState.fromByte(state.contractState[row]) != ContractState.ACTIVE) {
                continue;
            }
            int marketSlot = state.contractMarketId[row];
            if (marketSlot < 0 || !seenMarkets.add(marketSlot)) continue;
            String marketId = state.marketRegistry.get(marketSlot);
            MarketAPI market = marketId != null
                    ? Global.getSector().getEconomy().getMarket(marketId) : null;
            if (market == null || market.getFactionId() == null) continue;
            String factionId = market.getFactionId();
            int factionSlot = state.factionRegistry.intern(factionId);
            out.add(new MarketOwnership(eventKey(marketId, factionId),
                    marketSlot, factionSlot));
        }
        return out;
    }

    static long eventKey(String marketId, String factionId) {
        long hash = 0x494e54464c49504cL;
        hash = mix(hash, marketId);
        hash = mix(hash, factionId);
        return hash != 0L ? hash : 1L;
    }

    private static long mix(long hash, String value) {
        String text = value != null ? value : "";
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x100000001b3L;
        }
        hash ^= 0xff;
        hash *= 0x100000001b3L;
        return hash;
    }
}
