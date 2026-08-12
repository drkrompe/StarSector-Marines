package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.campaign.GarrisonDefenseMissionKey;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.GarrisonDefenseTriggerType;

import java.util.Collections;

/** Builds a battle mission from a Garrison's already-stationed detachment. */
public final class GarrisonDefenseMissionFactory {

    private GarrisonDefenseMissionFactory() {}

    public static Mission create(GarrisonDefensePayload payload,
                                 String planetName, String factionId) {
        if (payload == null || planetName == null || payload.committedMarines <= 0
                || payload.captainId == null) {
            return null;
        }
        int drops = Math.max(2, Math.min(10, (payload.committedMarines + 9) / 10));
        String title = title(payload.triggerType) + " — " + planetName;
        String flavor = "The stationed Garrison is already under attack. Defend the market "
                + "with the assigned captain and " + payload.committedMarines
                + " committed marines.";
        return new Mission(GarrisonDefenseMissionKey.encode(payload), title,
                MissionType.ASSAULT, MissionSource.STATIONING, 0, RiskLevel.HIGH,
                "Stationed detachment", flavor, 0.5f, 0.5f,
                FlybyRoster.EMPTY, FlybyRoster.EMPTY, drops, drops,
                planetName, null, factionId, payload.contractId,
                payload.salvageBaseline, payload.salvageNegotiated, (byte) 100,
                payload.salvageBaseline, payload.salvageNegotiated,
                Collections.emptyList());
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
