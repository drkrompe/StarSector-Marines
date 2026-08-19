package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.campaign.AbandonedColonyArchiveOutcome;
import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.SilentColonyMissionKey;

import java.util.Collections;
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
        return new Mission(SilentColonyMissionKey.encode(eventId),
                "Silent Colony Expedition — " + planetName,
                MissionType.EXTRACTION, MissionSource.CAMPAIGN_EVENT,
                0, RiskLevel.HIGH, "Funded blind expedition",
                "The distress burst has gone quiet. Locate any survivors "
                        + "and recover the sealed colony archive.",
                0.2f + random.nextFloat() * 0.6f,
                0.2f + random.nextFloat() * 0.6f,
                FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                4, 0, planetName, null, factionId,
                -1L, eventId, localMarketId,
                state.eventCiviliansAtRisk[row],
                state.eventColonyThreatSeed[row],
                (byte) 0, (byte) 0, (byte) 100,
                (byte) 0, (byte) 0, Collections.emptyList());
    }
}
