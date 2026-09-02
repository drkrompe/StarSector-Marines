package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.GarrisonDefenseMissionKey;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;


/** Builds a battle mission from a Garrison's already-stationed detachment. */
public final class GarrisonDefenseMissionFactory {

    private GarrisonDefenseMissionFactory() {}

    public static Mission create(GarrisonDefensePayload payload,
                                 String planetName, String factionId) {
        if (payload == null || planetName == null || payload.activeSeats <= 0
                || payload.captainId == null) {
            return null;
        }
        int drops = Math.max(2, Math.min(10, (payload.activeSeats + 9) / 10));
        String title = title(payload.triggerType) + " — " + planetName;
        String flavor = "The stationed Garrison is already under attack. Defend the market "
                + "with the assigned captain and " + payload.committedMarines
                + " committed marines.";
        Mission.Builder builder = Mission.builder()
                .id(GarrisonDefenseMissionKey.encode(payload))
                .name(title)
                .type(MissionType.ASSAULT)
                .source(MissionSource.STATIONING)
                .risk(RiskLevel.HIGH)
                .requirements("Stationed detachment")
                .flavor(flavor)
                .mapPosition(0.5f, 0.5f)
                .requiredDrops(drops)
                .employerShuttles(drops)
                .targetPlanetName(planetName)
                .targetFactionId(factionId)
                .contractId(payload.contractId)
                .salvageBaseline(payload.salvageBaseline)
                .salvageNegotiated(payload.salvageNegotiated)
                .contractSalvageBaseline(payload.salvageBaseline)
                .contractSalvageNegotiated(payload.salvageNegotiated)
                // Without this the raiders wear the defended market's own roster:
                // pirates landing on a Hegemony world arrive in Hegemony kit.
                .defenderFactionOverride(payload.attackerFactionKey);
        // A vanilla raid states how many it is landing, so size the operation off that
        // rather than off the risk label. Every other trigger keeps the builder default.
        if (payload.triggerType == GarrisonDefenseTriggerType.VANILLA_RAID
                && payload.attackerStrength > 0f) {
            builder.tier(OperationTierForStrength.forGroundStrength(payload.attackerStrength));
        }
        return builder.build();
    }

    private static String title(GarrisonDefenseTriggerType type) {
        switch (type) {
            case RIVAL_STRIKE: return "Garrison Defense: Rival Strike";
            case VANILLA_RAID: return "Garrison Defense: Faction Raid";
            case INTERNAL_FLIP: return "Garrison Defense: Internal Revolt";
            case NONE:
            default: return "Garrison Defense";
        }
    }
}
