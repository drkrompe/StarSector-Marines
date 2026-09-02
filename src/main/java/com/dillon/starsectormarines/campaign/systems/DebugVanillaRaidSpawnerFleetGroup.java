package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.DebugOnly;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI.GenericRaidParams;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission.FleetStyle;
import com.fs.starfarer.api.util.Misc;

import java.util.Random;

/**
 * Spawns one real {@link com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI}
 * against a colony the player owns — the fleet-group raid shape that
 * {@code VanillaRaidGarrisonSystem.readFleetGroupThreats} reads.
 *
 * <p>Built the way {@code PirateHostileActivityFactor.startRaid} builds one, with two
 * differences that exist only to make the arc watchable: the prep and the fleet count
 * are fixed small rather than rolled from event magnitude, and the source is the
 * <em>nearest</em> hostile-held market rather than one weighted at sector scale, because
 * travel time is distance and vanilla's own pick can be half a sector away.
 */
@DebugOnly
public final class DebugVanillaRaidSpawnerFleetGroup {

    /** Days spent in orbit at the source before the group departs. */
    private static final float PREP_DAYS = 1f;

    /** Days the group is allowed to operate in the target system before giving up. */
    private static final float PAYLOAD_DAYS = 20f;

    private DebugVanillaRaidSpawnerFleetGroup() {}

    /**
     * Adds the raid intel and returns a one-line account of what was spawned, or of why
     * nothing was.
     */
    public static String spawn() {
        if (Global.getSector() == null || Global.getSector().getIntelManager() == null) {
            return "No sector: cannot spawn a fleet-group raid.";
        }
        MarketAPI target = DebugVanillaRaidSpawnerTargets.playerTarget();
        if (target == null) {
            return "No player-owned colony to raid.";
        }
        StarSystemAPI where = target.getStarSystem();
        if (where == null) {
            return target.getName() + " is not in a star system; nothing to raid.";
        }
        MarketAPI source = DebugVanillaRaidSpawnerTargets.hostileSource(target);
        if (source == null) {
            return "No hostile faction holds a market to raid from.";
        }

        GenericRaidParams params = new GenericRaidParams(new Random(Misc.genRandomSeed()), true);
        params.factionId = source.getFactionId();
        params.source = source;
        params.prepDays = PREP_DAYS;
        params.payloadDays = PAYLOAD_DAYS;
        params.style = FleetStyle.STANDARD;
        params.raidParams.where = where;
        params.raidParams.allowNonHostileTargets = true;
        for (MarketAPI market : Misc.getMarketsInLocation(where)) {
            if (market != null && market.isPlayerOwned()) {
                params.raidParams.allowedTargets.add(market);
            }
        }
        if (params.raidParams.allowedTargets.isEmpty()) {
            params.raidParams.allowedTargets.add(target);
        }
        params.fleetSizes.add(5);
        params.fleetSizes.add(3);

        GenericRaidFGI raid = new GenericRaidFGI(params);
        Global.getSector().getIntelManager().addIntel(raid);

        float eta = raid.getETAUntil(GenericRaidFGI.PAYLOAD_ACTION);
        return String.format("%s raid: %s -> %s (%s), %d fleets, prep %.0fd, on target in ~%.1fd.",
                source.getFaction().getDisplayName(), source.getName(), target.getName(),
                where.getNameWithNoType(), params.fleetSizes.size(), PREP_DAYS, eta);
    }
}
