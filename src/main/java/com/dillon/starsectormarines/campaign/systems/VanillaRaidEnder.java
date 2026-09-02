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
 * Ends both of vanilla's raid shapes through vanilla's own paths.
 *
 * <p>A fleet group is sent home with {@code FleetGroupIntel.abort}, which expires its
 * route, gives every spawned fleet a return assignment, and fires the group's listener —
 * the hostile-activity respite included.
 *
 * <p>A {@link com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel} is force-failed.
 * <b>{@code forceFail} alone does not give straggler fleets return orders.</b> It marks the
 * failing stage, sets the fail index, and calls {@code endAfterDelay}; from that moment
 * {@code BaseIntelPlugin.advance} short-circuits, so the action stage never advances again
 * and never raids the market — but the return path,
 * {@code BaseRaidStage.giveReturnOrdersToStragglers}, is reachable only from a stage's own
 * {@code updateStatus}, which has just been switched off. {@code RaidIntel.failedAtStage}
 * is an empty hook that no shipped subclass overrides, and the routes registered under
 * {@code getRouteSourceId} are left as they were. The live pass must therefore watch
 * whether already-spawned raider fleets loiter in-system after a forced fail; if they do,
 * this ender needs a second step over
 * {@code RouteManager.getInstance().getRoutesForSource(raid.getRouteSourceId())} —
 * expiring and removing each route the way {@code FleetGroupIntel.finish} does — because
 * the stage's own return path cannot be called from outside the package.
 */
public final class VanillaRaidEnder implements RaidEnder {

    @Override
    public int endRaidsTargeting(String marketId, String factionId) {
        if (marketId == null || factionId == null
                || Global.getSector() == null
                || Global.getSector().getIntelManager() == null) {
            return 0;
        }
        return endFleetGroups(marketId, factionId) + endRaidIntels(marketId, factionId);
    }

    private int endFleetGroups(String marketId, String factionId) {
        int ended = 0;
        List<IntelInfoPlugin> matches = new ArrayList<>();
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
            if (!shouldEnd(marketId, factionId, params.factionId, over,
                    marketIds(raidParams != null ? raidParams.allowedTargets : null),
                    anyHostile, anyHostile && isHostileToRaider(marketId, factionId))) {
                continue;
            }
            matches.add(raid);
        }
        for (IntelInfoPlugin raid : matches) {
            ((GenericRaidFGI) raid).abort();
            ended++;
        }
        return ended;
    }

    private int endRaidIntels(String marketId, String factionId) {
        int ended = 0;
        List<IntelInfoPlugin> matches = new ArrayList<>();
        for (IntelInfoPlugin intel
                : Global.getSector().getIntelManager().getIntel(RaidIntel.class)) {
            if (!(intel instanceof RaidIntel)) continue;
            if (intel instanceof HegemonyInspectionIntel) continue;
            RaidIntel raid = (RaidIntel) intel;
            boolean over = raid.isEnded() || raid.isEnding()
                    || raid.isFailed() || raid.isSucceeded();
            String raidFactionId = raid.getFaction() != null ? raid.getFaction().getId() : null;
            if (!shouldEnd(marketId, factionId, raidFactionId, over,
                    marketIds(VanillaRaidGarrisonSystem.raidIntelTargets(raid)),
                    false, false)) {
                continue;
            }
            matches.add(raid);
        }
        for (IntelInfoPlugin raid : matches) {
            ((RaidIntel) raid).forceFail(true);
            ended++;
        }
        return ended;
    }

    private static boolean isHostileToRaider(String marketId, String factionId) {
        if (Global.getSector().getEconomy() == null) return false;
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null || market.getFaction() == null) return false;
        return market.getFaction().isHostileTo(factionId);
    }

    private static Set<String> marketIds(Collection<MarketAPI> markets) {
        if (markets == null) return Collections.emptySet();
        Set<String> ids = new LinkedHashSet<>();
        for (MarketAPI market : markets) {
            if (market != null && market.getId() != null) ids.add(market.getId());
        }
        return ids;
    }

    /**
     * Whether one raid is the raid a won defence at {@code marketId} should end.
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
    static boolean shouldEnd(String marketId, String factionId, String raidFactionId,
                             boolean alreadyOver, Collection<String> targetMarketIds,
                             boolean anyHostileMarket, boolean marketHostileToRaider) {
        if (marketId == null || factionId == null || alreadyOver) return false;
        if (!factionId.equals(raidFactionId)) return false;
        if (targetMarketIds != null && targetMarketIds.contains(marketId)) return true;
        return anyHostileMarket && marketHostileToRaider;
    }
}
