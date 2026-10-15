package com.dillon.starsectormarines.campaign.nexerelin;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

/**
 * Hands a Marine Operations invasion's result to Nexerelin, once ({@code nex-invasion-conquest.md}, slice 3).
 *
 * <p>The order matters. The ledger is claimed first, so nothing can settle the same invasion twice even if Nexerelin
 * throws part way. Then the commitment that Nexerelin's own Invade applies when the player confirms is mirrored: the
 * reputation hit, the hostile timeout, the recently-invaded flag and the raid cooldown. The handoff battle skips that
 * confirm step. Then the handoff battle ends with the result. The company's own writeback (personnel, boats) does not
 * depend on any of this and runs whatever happens here.
 */
public final class NexInvasionSettlement {

    private static final Logger LOG = Global.getLogger(NexInvasionSettlement.class);

    /** Nexerelin's flag that stops another invasion of the same market for a while; set as its Invade sets it. */
    static final String RECENTLY_INVADED_FLAG = "$nex_recentlyInvaded";
    static final float COMMITMENT_DAYS = 60f;
    static final float REPUTATION_PER_SIZE = -0.01f;

    public enum Result {
        /** Nexerelin applied the result. */
        SETTLED,
        /** This invasion, or a later one, already settled: nothing further happens. */
        ALREADY_SETTLED,
        /** The result could not be handed over. The player is told, and the company's writeback still runs. */
        NOT_APPLIED
    }

    private NexInvasionSettlement() {}

    public static Result apply(CampaignState state, String missionId, boolean victory) {
        NexInvasionMissionKey key = NexInvasionMissionKey.parse(missionId);
        if (key == null) return Result.NOT_APPLIED;
        if (!NexInvasionLedger.claim(state, key.serial())) return Result.ALREADY_SETTLED;
        MarketAPI market = Global.getSector() != null
                ? Global.getSector().getEconomy().getMarket(key.marketId()) : null;
        if (market == null || !Nexerelin.isEnabled()) {
            LOG.warn("MarineOps: invasion " + missionId + " has no market or no Nexerelin to settle with");
            return Result.NOT_APPLIED;
        }
        try {
            if (NexGroundBattleHandoff.battleOngoing(market)) {
                LOG.warn("MarineOps: Nexerelin already has a ground battle at " + market.getId()
                        + "; invasion " + missionId + " not handed over");
                return Result.NOT_APPLIED;
            }
            applyCommitment(market);
            NexGroundBattleHandoff.end(market, victory);
            LOG.info("MarineOps: invasion " + missionId + " handed to Nexerelin as "
                    + (victory ? "attacker" : "defender") + " victory");
            return Result.SETTLED;
        } catch (Throwable t) {
            // A LinkageError is a Nexerelin signature this build does not know; anything else is Nexerelin's own
            // failure part way through. Either way the ledger is already claimed, so it is never retried.
            LOG.error("MarineOps: invasion " + missionId + " could not be handed to Nexerelin", t);
            return Result.NOT_APPLIED;
        }
    }

    /** What Nexerelin's Invade applies when the player confirms it, which the handoff battle never reaches. */
    private static void applyCommitment(MarketAPI market) {
        FactionAPI owner = market.getFaction();
        CustomRepImpact impact = new CustomRepImpact();
        impact.delta = market.getSize() * REPUTATION_PER_SIZE;
        impact.ensureAtBest = RepLevel.HOSTILE;
        Global.getSector().adjustPlayerReputation(
                new RepActionEnvelope(RepActions.CUSTOM, impact, null, null, true, true), owner.getId());
        Misc.increaseMarketHostileTimeout(market, COMMITMENT_DAYS);
        Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), RECENTLY_INVADED_FLAG,
                Factions.PLAYER, true, COMMITMENT_DAYS);
        // Vanilla's own raid cooldown, which Nexerelin's Invade also starts.
        Global.getSector().getMemoryWithoutUpdate().set("$raid_cooldown", true,
                Global.getSettings().getFloat("raidCooldownDays"));
    }
}
