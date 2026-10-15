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
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.EnumSet;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Notices a Garrison whose market changed hands and strands its detachment — see
 * {@code stranded-detachment-recovery.md}.
 *
 * <p>It reads only market ownership, so every way a market can change hands is covered
 * the same way: a vanilla transfer, decivilisation, or a Nexerelin invasion (which the
 * vanilla raid seam deliberately ignores). No Nexerelin class is referenced. Postings
 * are Garrison rows too, so a player who loses their own market strands its posting.
 *
 * <p>Runs after the absent-defence settlement, so a raid that overran the garrison on the
 * same day is settled as an overrun first, and before the term lifecycle, so a captured
 * market's last day cannot be read as a completed term.
 */
public final class GarrisonMarketLossSystem implements CampaignSystem {

    /** The faction that owns a market now. */
    interface OwnerSource {
        /**
         * @return the owning faction id; {@link #NO_OWNER} when the market is gone or
         *         decivilised; null when ownership cannot be read at all
         */
        String owner(String marketId);
    }

    static final String NO_OWNER = "";

    private final Supplier<MarineRoster> rosterSource;
    private final OwnerSource ownerSource;
    private final Function<String, String> marketName;
    private final Function<String, String> factionName;

    public GarrisonMarketLossSystem() {
        this(GarrisonMarketLossSystem::liveRoster, GarrisonMarketLossSystem::liveOwner,
                StrandedDetachmentSystem::liveMarketName, GarrisonMarketLossSystem::liveFactionName);
    }

    GarrisonMarketLossSystem(Supplier<MarineRoster> rosterSource, OwnerSource ownerSource,
                             Function<String, String> marketName,
                             Function<String, String> factionName) {
        this.rosterSource = rosterSource;
        this.ownerSource = ownerSource;
        this.marketName = marketName;
        this.factionName = factionName;
    }

    @Override
    public String name() {
        return "GarrisonMarketLoss";
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
        MarineRoster roster = null;
        for (int row = 0; row < state.contractCount; row++) {
            if (ContractType.fromByte(state.contractType[row]) != ContractType.GARRISON) continue;
            ContractState contractState = ContractState.fromByte(state.contractState[row]);
            if (contractState != ContractState.ACTIVE
                    && contractState != ContractState.IN_PROGRESS) {
                continue;
            }
            String marketId = state.marketRegistry.get(state.contractMarketId[row]);
            String owner = marketId != null ? ownerSource.owner(marketId) : null;
            if (owner == null) continue;
            int recorded = state.contractMarketOwnerFactionId[row];
            if (recorded < 0) {
                // First sight of a running assignment: whoever holds the market now is
                // the side the garrison was placed with.
                if (!NO_OWNER.equals(owner)) {
                    state.contractMarketOwnerFactionId[row] = state.factionRegistry.intern(owner);
                }
                continue;
            }
            if (owner.equals(state.factionRegistry.get(recorded))) continue;

            if (roster == null) roster = rosterSource.get();
            if (roster == null) return;
            boolean silenced = NO_OWNER.equals(owner);
            StrandedDetachment.strandOnCapture(state, row, roster, day,
                    silenced ? -1 : state.factionRegistry.intern(owner),
                    marketName.apply(marketId), silenced ? null : factionName.apply(owner));
        }
    }

    static String liveOwner(String marketId) {
        if (Global.getSector() == null || Global.getSector().getEconomy() == null) return null;
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null || market.isPlanetConditionMarketOnly()
                || market.getFactionId() == null) {
            return NO_OWNER;
        }
        return market.getFactionId();
    }

    private static String liveFactionName(String factionId) {
        if (Global.getSector() == null) return null;
        FactionAPI faction = Global.getSector().getFaction(factionId);
        return faction != null ? faction.getDisplayName() : null;
    }

    private static MarineRoster liveRoster() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster() : null;
    }
}
