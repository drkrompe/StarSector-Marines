package com.dillon.starsectormarines.campaign.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.group.FGRaidAction;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the live raids one defended market's pending defence was armed from, in both of
 * vanilla's raid shapes.
 *
 * <p>Two callers ask the same question for opposite reasons: {@link VanillaRaidEnder}
 * ends what it finds after a won defence, and {@link VanillaRaidStatus} reads whether
 * anything is still in the air over a defence nobody answered. One walk, one predicate.
 */
final class VanillaRaidScan {

    private VanillaRaidScan() {}

    /** Whether any raid of either shape by {@code factionId} is still coming for the market. */
    static boolean anyLiveRaidTargeting(String marketId, String factionId) {
        return !liveFleetGroupsTargeting(marketId, factionId).isEmpty()
                || !liveRaidIntelsTargeting(marketId, factionId).isEmpty();
    }

    static List<GenericRaidFGI> liveFleetGroupsTargeting(String marketId, String factionId) {
        if (!readable(marketId, factionId)) return Collections.emptyList();
        List<GenericRaidFGI> matches = new ArrayList<>();
        for (IntelInfoPlugin intel
                : Global.getSector().getIntelManager().getIntel(GenericRaidFGI.class)) {
            if (!(intel instanceof GenericRaidFGI)) continue;
            GenericRaidFGI raid = (GenericRaidFGI) intel;
            GenericRaidFGI.GenericRaidParams params = raid.getParams();
            if (params == null) continue;
            FGRaidAction.FGRaidParams raidParams = params.raidParams;
            // isFailed() as well, matching the reader that arms the defence: a group whose
            // raid action finished with nothing to show is over without ending yet, and
            // abort() fires reportFGIAborted unconditionally on one nobody armed against.
            boolean over = raid.isEnded() || raid.isEnding() || raid.isAborted()
                    || raid.isFailed();
            boolean anyHostile = raidParams != null && raidParams.allowAnyHostileMarket;
            if (targets(marketId, factionId, params.factionId, over,
                    marketIds(raidParams != null ? raidParams.allowedTargets : null),
                    anyHostile, anyHostile && isHostileToRaider(marketId, factionId))) {
                matches.add(raid);
            }
        }
        return matches;
    }

    static List<RaidIntel> liveRaidIntelsTargeting(String marketId, String factionId) {
        if (!readable(marketId, factionId)) return Collections.emptyList();
        List<RaidIntel> matches = new ArrayList<>();
        for (IntelInfoPlugin intel
                : Global.getSector().getIntelManager().getIntel(RaidIntel.class)) {
            if (!(intel instanceof RaidIntel)) continue;
            if (intel instanceof HegemonyInspectionIntel) continue;
            RaidIntel raid = (RaidIntel) intel;
            boolean over = raid.isEnded() || raid.isEnding()
                    || raid.isFailed() || raid.isSucceeded();
            String raidFactionId = raid.getFaction() != null ? raid.getFaction().getId() : null;
            if (targets(marketId, factionId, raidFactionId, over,
                    marketIds(VanillaRaidGarrisonSystem.raidIntelTargets(raid)),
                    false, false)) {
                matches.add(raid);
            }
        }
        return matches;
    }

    /**
     * Whether one raid is a live raid on {@code marketId} by {@code factionId} — the raid
     * a won defence there should end, and the raid an unanswered one is still waiting on.
     *
     * @param marketId              the defended market
     * @param factionId             the attacker the defence was armed against
     * @param raidFactionId         the faction actually running this raid
     * @param alreadyOver           whether the raid has already ended, is ending, was
     *                              aborted, failed, or succeeded
     * @param targetMarketIds       the markets this raid names as targets
     * @param anyHostileMarket      whether the raid may hit any hostile market rather than
     *                              only its named targets (fleet-group shape only)
     * @param marketHostileToRaider whether the defended market's owner is hostile to the
     *                              raider; only consulted when {@code anyHostileMarket}
     */
    static boolean targets(String marketId, String factionId, String raidFactionId,
                           boolean alreadyOver, Collection<String> targetMarketIds,
                           boolean anyHostileMarket, boolean marketHostileToRaider) {
        if (marketId == null || factionId == null || alreadyOver) return false;
        if (!factionId.equals(raidFactionId)) return false;
        if (targetMarketIds != null && targetMarketIds.contains(marketId)) return true;
        return anyHostileMarket && marketHostileToRaider;
    }

    private static boolean readable(String marketId, String factionId) {
        return marketId != null && factionId != null
                && Global.getSector() != null
                && Global.getSector().getIntelManager() != null;
    }

    private static boolean isHostileToRaider(String marketId, String factionId) {
        MarketAPI market = market(marketId);
        if (market == null || market.getFaction() == null) return false;
        return market.getFaction().isHostileTo(factionId);
    }

    static MarketAPI market(String marketId) {
        if (marketId == null || Global.getSector() == null
                || Global.getSector().getEconomy() == null) {
            return null;
        }
        return Global.getSector().getEconomy().getMarket(marketId);
    }

    private static Set<String> marketIds(Collection<MarketAPI> markets) {
        if (markets == null) return Collections.emptySet();
        Set<String> ids = new LinkedHashSet<>();
        for (MarketAPI market : markets) {
            if (market != null && market.getId() != null) ids.add(market.getId());
        }
        return ids;
    }
}
