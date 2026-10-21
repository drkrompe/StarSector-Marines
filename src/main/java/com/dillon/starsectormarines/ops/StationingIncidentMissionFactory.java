package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.campaign.StationingIncidentMissionKey;
import com.dillon.starsectormarines.campaign.StationingIncidentPayload;
import com.dillon.starsectormarines.campaign.StationingIncidentType;


/** Builds a battle mission whose personnel and local lifts come from a Cadre payload. */
public final class StationingIncidentMissionFactory {

    private StationingIncidentMissionFactory() {}

    public static Mission create(StationingIncidentPayload payload,
                                 String planetName, String factionId) {
        if (payload == null || planetName == null || payload.activeSeats <= 0
                || payload.captainId == null) {
            return null;
        }
        MissionType missionType = missionType(payload.type);
        RiskLevel risk = risk(payload.type);
        int drops = Math.max(2, Math.min(10, (payload.activeSeats + 9) / 10));
        String title = Strings.format("stationingIncidentTitle", title(payload.type), planetName);
        String flavor = Strings.format("stationingIncidentFlavor", payload.committedMarines);
        return Mission.builder()
                .id(StationingIncidentMissionKey.encode(payload))
                .name(title)
                .type(missionType)
                .source(MissionSource.STATIONING)
                .risk(risk)
                .requirements(Strings.get("stationingIncidentRequirements"))
                .flavor(flavor)
                .mapPosition(0.5f, 0.5f)
                .requiredDrops(drops)
                .employerShuttles(drops)
                .targetPlanetName(planetName)
                .targetFactionId(factionId)
                .contractId(payload.contractId)
                .salvageBaseline(5)
                .salvageNegotiated(5)
                .contractSalvageBaseline(5)
                .contractSalvageNegotiated(5)
                .build();
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
            case FACTORY_ACCIDENT: return Strings.get("stationingIncidentTitleFactoryAccident");
            case LIVE_FIRE_RAID: return Strings.get("stationingIncidentTitleLiveFireRaid");
            case DEFECTOR_LEAD: return Strings.get("stationingIncidentTitleDefectorLead");
            case NONE:
            default: return Strings.get("stationingIncidentTitleDefault");
        }
    }
}
