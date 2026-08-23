package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.AbandonedColonyArchiveOutcome;
import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.SilentColonyMissionKey;

import java.util.Random;

/** Builds the immutable mission boundary for one funded colony expedition. */
public final class SilentColonyMissionFactory {

    private SilentColonyMissionFactory() {}

    public static Mission create(CampaignState state, long eventId,
                                 int localMarketId, String planetName,
                                 String factionId) {
        if (state == null || eventId <= 0L || localMarketId < 0
                || planetName == null) {
            return null;
        }
        int row = state.eventIndex(eventId);
        if (row < 0
                || CampaignEventType.fromByte(state.eventType[row])
                    != CampaignEventType.SILENT_COLONY
                || CampaignEventState.fromByte(state.eventState[row])
                    != CampaignEventState.COMMITTED
                || state.eventMarketId[row] != localMarketId
                || state.eventCiviliansAtRisk[row] <= 0
                || state.eventColonyThreatSeed[row] < 0L
                || AbandonedColonyArchiveOutcome.fromByte(
                    state.eventColonyArchiveOutcome[row])
                    != AbandonedColonyArchiveOutcome.NONE) {
            return null;
        }

        Random random = new Random(eventId ^ 0x53494C454E54434FL);
        return Mission.builder()
                .id(SilentColonyMissionKey.encode(eventId))
                .name("Silent Colony Expedition — " + planetName)
                .type(MissionType.EXTRACTION)
                .source(MissionSource.CAMPAIGN_EVENT)
                .risk(RiskLevel.HIGH)
                .requirements("Funded blind expedition")
                .flavor("The distress burst has gone quiet. Locate any survivors "
                        + "and recover the sealed colony archive.")
                .mapPosition(0.2f + random.nextFloat() * 0.6f,
                        0.2f + random.nextFloat() * 0.6f)
                .requiredDrops(4)
                .targetPlanetName(planetName)
                .targetFactionId(factionId)
                .campaignEventId(eventId)
                .campaignEventMarketId(localMarketId)
                .civiliansAtRisk(state.eventCiviliansAtRisk[row])
                .campaignEventThreatSeed(state.eventColonyThreatSeed[row])
                .build();
    }
}
