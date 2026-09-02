package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.PolityThreatFilter;
import com.dillon.starsectormarines.campaign.systems.VanillaRaidGarrisonSystem.RaidThreat;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongPredicate;

/**
 * Asks, at the moment Marine Ops opens on one of the player's own colonies, which live
 * vanilla raids the company could still meet on the ground there.
 *
 * <p>A query rather than a record: nothing about a pending threat is persisted, because
 * the vanilla intel object already persists the raid and battles are transient. The one
 * thing the caller must remember is which raids have already been fought, which arrives
 * as {@code alreadySettled}.
 *
 * <p>Deliberately thin. It reaches the live sector through the shared readers and so
 * cannot be unit-tested; the judgement it makes lives in {@link PolityThreatFilter},
 * which can.
 */
public final class PolityThreatQuery {

    private static final VanillaRaidStatus LIVE_VANILLA = new VanillaRaidStatus();

    private PolityThreatQuery() {}

    /**
     * The live raids aimed at {@code market} that are still fightable there.
     *
     * @param state          registry owner; threat market and faction slots are its own
     * @param market         the colony Marine Ops was opened on
     * @param alreadySettled tested with each threat's event key; {@code null} settles none
     */
    public static List<RaidThreat> fightableAt(CampaignState state, MarketAPI market,
                                               LongPredicate alreadySettled) {
        if (state == null || market == null || market.getId() == null) {
            return Collections.emptyList();
        }
        List<RaidThreat> live = VanillaRaidGarrisonSystem.readLiveThreats(state);
        if (live == null || live.isEmpty()) return Collections.emptyList();
        String marketId = market.getId();
        int marketSlot = state.marketRegistry.intern(marketId);
        boolean playerOwned = market.isPlayerOwned();
        List<RaidThreat> out = new ArrayList<>();
        for (RaidThreat threat : live) {
            if (threat == null || threat.marketId != marketSlot) continue;
            String attackerFactionId = state.factionRegistry.get(threat.attackerFactionId);
            RaidStatus status = LIVE_VANILLA.status(marketId, attackerFactionId);
            boolean settled = alreadySettled != null && alreadySettled.test(threat.eventKey);
            if (PolityThreatFilter.fightable(playerOwned, status, settled)) out.add(threat);
        }
        return out;
    }
}
