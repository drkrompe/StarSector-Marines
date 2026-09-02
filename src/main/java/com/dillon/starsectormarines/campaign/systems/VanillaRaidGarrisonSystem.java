package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.intel.group.FGRaidAction;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionIntel;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Arms pending Garrison defenses from vanilla's two raid shapes: the fleet-group
 * {@link com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI}, which reaches
 * the player's own colonies, and the older
 * {@link com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel} — pirate-base raids and
 * punitive expeditions — which is the shape that reaches a patron's market.
 */
public final class VanillaRaidGarrisonSystem implements CampaignSystem {

    interface ThreatSource {
        List<RaidThreat> activeThreats(CampaignState state);
    }

    static final class RaidThreat {
        final long eventKey;
        final int marketId;
        final int attackerFactionId;
        /** Attacker ground strength in vanilla's raid-strength units; 0 when unestimated. */
        final float groundStrength;

        RaidThreat(long eventKey, int marketId, int attackerFactionId, float groundStrength) {
            this.eventKey = eventKey;
            this.marketId = marketId;
            this.attackerFactionId = attackerFactionId;
            this.groundStrength = groundStrength;
        }
    }

    private final ThreatSource threats;

    public VanillaRaidGarrisonSystem() {
        this(VanillaRaidGarrisonSystem::readLiveThreats);
    }

    VanillaRaidGarrisonSystem(ThreatSource threats) {
        this.threats = threats;
    }

    @Override
    public String name() {
        return "VanillaRaidGarrison";
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
        List<RaidThreat> active = threats.activeThreats(state);
        if (active == null) return;
        for (RaidThreat threat : active) {
            if (threat == null) continue;
            GarrisonDefenseTrigger.arm(state, threat.eventKey, threat.marketId,
                    GarrisonDefenseTriggerType.VANILLA_RAID, -1L,
                    threat.attackerFactionId, threat.groundStrength, day);
        }
    }

    private static List<RaidThreat> readLiveThreats(CampaignState state) {
        if (Global.getSector() == null || Global.getSector().getIntelManager() == null) {
            return Collections.emptyList();
        }
        List<RaidThreat> out = new ArrayList<>();
        readFleetGroupThreats(state, out);
        readRaidIntelThreats(state, out);
        return out;
    }

    private static void readFleetGroupThreats(CampaignState state, List<RaidThreat> out) {
        for (IntelInfoPlugin intel
                : Global.getSector().getIntelManager().getIntel(GenericRaidFGI.class)) {
            if (!(intel instanceof GenericRaidFGI)) continue;
            GenericRaidFGI raid = (GenericRaidFGI) intel;
            if (raid.isEnded() || raid.isEnding() || raid.isAborted() || raid.isFailed()
                    || !raid.isCurrent(GenericRaidFGI.PAYLOAD_ACTION)) {
                continue;
            }
            GenericRaidFGI.GenericRaidParams params = raid.getParams();
            FGRaidAction.FGRaidParams raidParams = params != null ? params.raidParams : null;
            if (raidParams == null || raidParams.allowedTargets == null) continue;
            String factionId = params.factionId;
            int factionSlot = factionId != null ? state.factionRegistry.intern(factionId) : -1;
            long visible = raid.getPlayerVisibleTimestamp() != null
                    ? raid.getPlayerVisibleTimestamp() : 0L;
            float strength = fleetGroupGroundStrength(raid);
            for (MarketAPI target : raidParams.allowedTargets) {
                if (target == null || target.getId() == null) continue;
                int marketSlot = state.marketRegistry.intern(target.getId());
                long key = eventKey(visible, factionId, target.getId(), params.memoryKey);
                out.add(new RaidThreat(key, marketSlot, factionSlot, strength));
            }
        }
    }

    /**
     * Ground strength the fleet group can land: the sum over its spawned fleets once they
     * exist, and otherwise the route-strength estimate that
     * {@code FGRaidAction.performRaid} itself falls back on for an unspawned group.
     */
    private static float fleetGroupGroundStrength(GenericRaidFGI raid) {
        float spawned = 0f;
        List<CampaignFleetAPI> fleets = raid.getFleets();
        if (fleets != null) {
            for (CampaignFleetAPI fleet : fleets) {
                if (fleet != null) spawned += MarketCMD.getRaidStr(fleet);
            }
        }
        if (spawned > 0f) return spawned;
        RouteData route = raid.getRoute();
        OptionalFleetData extra = route != null ? route.getExtra() : null;
        if (extra == null) return 0f;
        int numFleets = Math.max(1, raid.getApproximateNumberOfFleets());
        return extra.getStrengthModifiedByDamage() / numFleets
                * Misc.FP_TO_GROUND_RAID_STR_APPROX_MULT;
    }

    private static void readRaidIntelThreats(CampaignState state, List<RaidThreat> out) {
        for (IntelInfoPlugin intel
                : Global.getSector().getIntelManager().getIntel(RaidIntel.class)) {
            if (!(intel instanceof RaidIntel)) continue;
            // A Hegemony inspection raids only when the player resists it, and its
            // confiscation already reads the ground-defence stat; fighting a resisted
            // inspection is a separate mechanism and out of scope here.
            if (intel instanceof HegemonyInspectionIntel) continue;
            RaidIntel raid = (RaidIntel) intel;
            if (raid.isEnded() || raid.isEnding() || raid.isFailed() || raid.isSucceeded()) {
                continue;
            }
            if (!hasReachedActionStage(raid)) continue;
            String factionId = raid.getFaction() != null ? raid.getFaction().getId() : null;
            int factionSlot = factionId != null ? state.factionRegistry.intern(factionId) : -1;
            long visible = raid.getPlayerVisibleTimestamp() != null
                    ? raid.getPlayerVisibleTimestamp() : 0L;
            String memoryKey = raid.getClass().getSimpleName() + ":" + raid.getRouteSourceId();
            float strength = raidIntelGroundStrength(raid);
            for (MarketAPI target : raidIntelTargets(raid)) {
                if (target == null || target.getId() == null) continue;
                int marketSlot = state.marketRegistry.intern(target.getId());
                long key = eventKey(visible, factionId, target.getId(), memoryKey);
                out.add(new RaidThreat(key, marketSlot, factionSlot, strength));
            }
        }
    }

    /**
     * A raid is a ground threat once it is at or past its own action stage; before that it
     * is still organising, assembling, or in transit.
     */
    private static boolean hasReachedActionStage(RaidIntel raid) {
        ActionStage action = raid.getActionStage();
        if (action == null) return false;
        int actionIndex = raid.getStageIndex(action);
        return actionIndex >= 0 && raid.getCurrentStage() >= actionIndex;
    }

    /**
     * Mirrors {@code PirateRaidActionStage.getTargets} — every market in the raided system
     * hostile to the raider — because that method is protected. A punitive expedition
     * names its one target directly instead.
     */
    static List<MarketAPI> raidIntelTargets(RaidIntel raid) {
        if (raid instanceof PunitiveExpeditionIntel) {
            MarketAPI target = ((PunitiveExpeditionIntel) raid).getTarget();
            return target != null
                    ? Collections.singletonList(target) : Collections.<MarketAPI>emptyList();
        }
        if (raid.getSystem() == null || raid.getFaction() == null) {
            return Collections.emptyList();
        }
        List<MarketAPI> targets = new ArrayList<>();
        for (MarketAPI market : Misc.getMarketsInLocation(raid.getSystem())) {
            if (market != null && market.getFaction() != null
                    && market.getFaction().isHostileTo(raid.getFaction())) {
                targets.add(market);
            }
        }
        return targets;
    }

    /** {@code getRaidStr} walks the assemble stage, which a malformed raid may not have. */
    private static float raidIntelGroundStrength(RaidIntel raid) {
        if (raid.getAssembleStage() == null) return 0f;
        return raid.getRaidStr();
    }

    static long eventKey(long visibleTimestamp, String factionId,
                         String marketId, String memoryKey) {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, Long.toString(visibleTimestamp));
        hash = mix(hash, factionId);
        hash = mix(hash, marketId);
        hash = mix(hash, memoryKey);
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
