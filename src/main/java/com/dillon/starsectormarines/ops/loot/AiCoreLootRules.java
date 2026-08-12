package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

/** Pure mission/context gates for rare AI-core recovery candidates. */
public final class AiCoreLootRules {

    private AiCoreLootRules() {}

    public static boolean permitsGamma(LootRollRequest request) {
        return hasEligibleContext(request);
    }

    public static boolean permitsBeta(LootRollRequest request) {
        return hasEligibleContext(request) && request.risk != RiskLevel.LOW;
    }

    public static boolean permitsAlpha(LootRollRequest request) {
        return hasEligibleContext(request) && request.risk == RiskLevel.HIGH;
    }

    private static boolean hasEligibleContext(LootRollRequest request) {
        if (request == null || !isDecapitationStrike(request.missionType)) return false;
        return isAiLinkedFaction(request.targetFactionId)
                || isAiLinkedIndustry(request.targetIndustryId);
    }

    private static boolean isDecapitationStrike(MissionType type) {
        return type == MissionType.ASSAULT
                || type == MissionType.RAID
                || type == MissionType.SABOTAGE;
    }

    private static boolean isAiLinkedFaction(String factionId) {
        return Factions.REMNANTS.equals(factionId) || Factions.TRITACHYON.equals(factionId);
    }

    private static boolean isAiLinkedIndustry(String industryId) {
        return Industries.TECHMINING.equals(industryId)
                || Industries.ORBITALWORKS.equals(industryId)
                || Industries.HIGHCOMMAND.equals(industryId);
    }
}
