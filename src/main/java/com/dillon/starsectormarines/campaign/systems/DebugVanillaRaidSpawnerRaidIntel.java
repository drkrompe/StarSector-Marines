package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.DebugOnly;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionIntel;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionManager.PunExGoal;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionManager.PunExReason;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionManager.PunExType;

/**
 * Spawns one real {@link com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel}
 * against a colony the player owns — the staged-raid shape that
 * {@code VanillaRaidGarrisonSystem.readRaidIntelThreats} reads — as a punitive
 * expedition against one of the colony's industries.
 *
 * <p>A punitive expedition because the pirate raid cannot be had this way. Vanilla's
 * pirate {@code RaidIntel} is built only by {@code PirateBaseIntel.startRaid}, which is
 * an instance method over a base's own market, gather jump point and raid timeout — and
 * which opens by refusing outright any target system holding a player market, because
 * raids on the player are Colony Crises' business. Reaching that shape would mean
 * standing up a pirate base and then rebuilding the method's stage chain around its
 * guard; {@link PunitiveExpeditionIntel} is the same {@code RaidIntel} ladder
 * (organize, assemble, travel, action, return), is aimed at a player colony by
 * construction, and adds itself to the intel manager in its own constructor.
 */
@DebugOnly
public final class DebugVanillaRaidSpawnerRaidIntel {

    /** Fleet points the expedition assembles, near vanilla's own floor for one. */
    private static final float EXPEDITION_FP = 100f;

    /** Days spent organising before the fleets assemble; vanilla rolls 20-40. */
    private static final float ORGANIZE_DAYS = 1f;

    private DebugVanillaRaidSpawnerRaidIntel() {}

    /**
     * Adds the expedition intel and returns a one-line account of what was spawned, or
     * of why nothing was.
     */
    public static String spawn() {
        if (Global.getSector() == null || Global.getSector().getIntelManager() == null) {
            return "No sector: cannot spawn a punitive expedition.";
        }
        MarketAPI target = DebugVanillaRaidSpawnerTargets.playerTarget();
        if (target == null) {
            return "No player-owned colony to raid.";
        }
        if (target.getStarSystem() == null) {
            return target.getName() + " is not in a star system; nothing to raid.";
        }
        MarketAPI from = DebugVanillaRaidSpawnerTargets.hostileSource(target);
        if (from == null || from.getFaction() == null) {
            return "No hostile faction holds a market to mount an expedition from.";
        }
        Industry industry = DebugVanillaRaidSpawnerTargets.raidableIndustry(target);
        if (industry == null) {
            return target.getName() + " has no raidable industry to target.";
        }

        PunitiveExpeditionIntel intel = new PunitiveExpeditionIntel(
                from.getFaction(), from, target, EXPEDITION_FP, ORGANIZE_DAYS,
                PunExGoal.RAID_PRODUCTION, industry, new PunExReason(PunExType.TERRITORIAL));
        if (intel.isDone()) {
            return "Expedition from " + from.getName() + " could not find a gather point"
                    + " or a jump point to " + target.getName() + ".";
        }

        return String.format(
                "%s punitive expedition: %s -> %s, raiding %s, %.0f FP,"
                        + " organising %.0fd, on target in ~%.1fd.",
                from.getFaction().getDisplayName(), from.getName(), target.getName(),
                industry.getCurrentName(), EXPEDITION_FP, ORGANIZE_DAYS, intel.getETA());
    }
}
