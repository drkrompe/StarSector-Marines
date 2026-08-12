package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.group.FGRaidAction;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Converts explicit GenericRaidFGI payload targets into pending Garrison defenses. */
public final class VanillaRaidGarrisonSystem implements CampaignSystem {

    interface ThreatSource {
        List<RaidThreat> activeThreats(CampaignState state);
    }

    static final class RaidThreat {
        final long eventKey;
        final int marketId;
        final int attackerFactionId;

        RaidThreat(long eventKey, int marketId, int attackerFactionId) {
            this.eventKey = eventKey;
            this.marketId = marketId;
            this.attackerFactionId = attackerFactionId;
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
                    threat.attackerFactionId, day);
        }
    }

    private static List<RaidThreat> readLiveThreats(CampaignState state) {
        if (Global.getSector() == null || Global.getSector().getIntelManager() == null) {
            return Collections.emptyList();
        }
        List<RaidThreat> out = new ArrayList<>();
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(GenericRaidFGI.class)) {
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
            for (MarketAPI target : raidParams.allowedTargets) {
                if (target == null || target.getId() == null) continue;
                int marketSlot = state.marketRegistry.intern(target.getId());
                long key = eventKey(visible, factionId, target.getId(), params.memoryKey);
                out.add(new RaidThreat(key, marketSlot, factionSlot));
            }
        }
        return out;
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
