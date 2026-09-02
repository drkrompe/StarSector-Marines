package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.systems.VanillaRaidGarrisonSystem.RaidThreat;

import java.util.List;

/**
 * Names the faction behind one raid, so a won polity defence knows which raid to send
 * home.
 *
 * <p>A Garrison defence carries its attacker on the contract row it was armed from; a
 * polity defence has no row, so the raid is looked up again at settlement time from
 * whatever is still live in the sector. That is also the honest reading: a raid that has
 * gone away in the meantime has nothing left to end.
 */
public interface PolityRaidLookup {

    /** A lookup that finds nothing, for hosts with no live sector. */
    PolityRaidLookup NONE = (state, eventKey) -> null;

    /**
     * The vanilla faction id raiding under {@code eventKey}, or {@code null} when no live
     * raid carries that key.
     */
    String attackerFactionId(CampaignState state, long eventKey);

    /**
     * The decision itself, over an explicit threat list: the first threat carrying
     * {@code eventKey}, with its attacker slot resolved through the state's own registry.
     *
     * <p>One raid may appear once per market it targets, and every one of those entries
     * carries the same attacker, so the first match is the answer.
     */
    static String attackerIn(CampaignState state, List<RaidThreat> threats, long eventKey) {
        if (state == null || threats == null || eventKey == 0L) return null;
        for (RaidThreat threat : threats) {
            if (threat == null || threat.eventKey != eventKey) continue;
            if (threat.attackerFactionId < 0) return null;
            return state.factionRegistry.get(threat.attackerFactionId);
        }
        return null;
    }
}
