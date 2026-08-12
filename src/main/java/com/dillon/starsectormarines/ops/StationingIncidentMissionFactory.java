package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.campaign.StationingIncidentMissionKey;
import com.dillon.starsectormarines.campaign.StationingIncidentPayload;
import com.dillon.starsectormarines.campaign.StationingIncidentType;

import java.util.Collections;

/** Builds a battle mission whose personnel and local lifts come from a Cadre payload. */
public final class StationingIncidentMissionFactory {

    private StationingIncidentMissionFactory() {}

    public static Mission create(StationingIncidentPayload payload,
                                 String planetName, String factionId) {
        if (payload == null || planetName == null || payload.committedMarines <= 0
                || payload.captainId == null) {
            return null;
        }
        MissionType missionType = missionType(payload.type);
        RiskLevel risk = risk(payload.type);
        int drops = Math.max(2, Math.min(10, (payload.committedMarines + 9) / 10));
        String title = title(payload.type) + " — " + planetName;
        String flavor = "The stationed Cadre is already on site. Respond with the assigned "
                + "captain and " + payload.committedMarines + " committed marines.";
        return new Mission(StationingIncidentMissionKey.encode(payload), title,
                missionType, MissionSource.STATIONING, 0, risk,
                "Stationed detachment", flavor, 0.5f, 0.5f,
                FlybyRoster.EMPTY, FlybyRoster.EMPTY, drops, drops,
                planetName, null, factionId, payload.contractId,
                (byte) 5, (byte) 5, (byte) 100,
                (byte) 5, (byte) 5, Collections.emptyList());
    }

    private static MissionType missionType(StationingIncidentType type) {
        switch (type) {
            case LIVE_FIRE_RAID: return MissionType.ASSAULT;
            case FACTORY_ACCIDENT:
            case DEFECTOR_LEAD:
            case NONE:
            default: return MissionType.EXTRACTION;
        }
    }

    private static RiskLevel risk(StationingIncidentType type) {
        return type == StationingIncidentType.FACTORY_ACCIDENT
                ? RiskLevel.LOW : RiskLevel.MEDIUM;
    }

    private static String title(StationingIncidentType type) {
        switch (type) {
            case FACTORY_ACCIDENT: return "Cadre Incident: Factory Accident";
            case LIVE_FIRE_RAID: return "Cadre Incident: Live-Fire Raid";
            case DEFECTOR_LEAD: return "Cadre Incident: Defector Lead";
            case NONE:
            default: return "Cadre Incident";
        }
    }
}
