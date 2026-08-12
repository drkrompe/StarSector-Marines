package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CivilianRescueMissionKey;

import java.util.Collections;
import java.util.Random;

/** Builds the immutable mission boundary for one committed rescue event. */
public final class CivilianRescueMissionFactory {

    private CivilianRescueMissionFactory() {}

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
                    != CampaignEventType.CIVILIAN_RESCUE
                || CampaignEventState.fromByte(state.eventState[row])
                    != CampaignEventState.COMMITTED
                || state.eventMarketId[row] != localMarketId
                || state.eventCiviliansAtRisk[row] <= 0) {
            return null;
        }

        Random random = new Random(eventId ^ 0x4356494C52455343L);
        String missionId = CivilianRescueMissionKey.encode(eventId);
        String name = "Civilian Evacuation — " + planetName;
        String flavor = "Relief stores are committed. Hold the evacuation "
                + "corridor until the civilian lifts are clear.";
        return new Mission(missionId, name, MissionType.EXTRACTION,
                MissionSource.CAMPAIGN_EVENT, 0, RiskLevel.HIGH,
                "Committed relief response", flavor,
                0.2f + random.nextFloat() * 0.6f,
                0.2f + random.nextFloat() * 0.6f,
                FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                4, 0, planetName, null, factionId,
                -1L, eventId, localMarketId,
                state.eventCiviliansAtRisk[row],
                (byte) 0, (byte) 0, (byte) 100,
                (byte) 0, (byte) 0, Collections.emptyList());
    }
}
