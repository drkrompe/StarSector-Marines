package com.dillon.starsectormarines.campaign.systems;

import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;

/**
 * Ends both of vanilla's raid shapes through vanilla's own paths. Which raids those are
 * is {@link VanillaRaidScan}'s question.
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
        int ended = 0;
        for (GenericRaidFGI raid
                : VanillaRaidScan.liveFleetGroupsTargeting(marketId, factionId)) {
            raid.abort();
            ended++;
        }
        for (RaidIntel raid : VanillaRaidScan.liveRaidIntelsTargeting(marketId, factionId)) {
            raid.forceFail(true);
            ended++;
        }
        return ended;
    }
}
