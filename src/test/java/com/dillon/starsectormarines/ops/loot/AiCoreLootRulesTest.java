package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCoreLootRulesTest {

    @Test
    void riskUnlocksCoreTiersForAiLinkedStrike() {
        LootRollRequest low = request(MissionType.RAID, RiskLevel.LOW,
                Factions.TRITACHYON, Industries.SPACEPORT);
        LootRollRequest medium = request(MissionType.RAID, RiskLevel.MEDIUM,
                Factions.TRITACHYON, Industries.SPACEPORT);
        LootRollRequest high = request(MissionType.RAID, RiskLevel.HIGH,
                Factions.TRITACHYON, Industries.SPACEPORT);

        assertTrue(AiCoreLootRules.permitsGamma(low));
        assertFalse(AiCoreLootRules.permitsBeta(low));
        assertTrue(AiCoreLootRules.permitsBeta(medium));
        assertFalse(AiCoreLootRules.permitsAlpha(medium));
        assertTrue(AiCoreLootRules.permitsAlpha(high));
    }

    @Test
    void aiLinkedIndustryQualifiesOrdinaryFaction() {
        LootRollRequest request = request(MissionType.SABOTAGE, RiskLevel.MEDIUM,
                Factions.HEGEMONY, Industries.TECHMINING);

        assertTrue(AiCoreLootRules.permitsGamma(request));
        assertTrue(AiCoreLootRules.permitsBeta(request));
    }

    @Test
    void ordinaryTargetAndNonStrikeMissionAreExcluded() {
        LootRollRequest ordinary = request(MissionType.ASSAULT, RiskLevel.HIGH,
                Factions.HEGEMONY, Industries.SPACEPORT);
        LootRollRequest conquest = request(MissionType.CONQUEST, RiskLevel.HIGH,
                Factions.REMNANTS, Industries.HIGHCOMMAND);

        assertFalse(AiCoreLootRules.permitsGamma(ordinary));
        assertFalse(AiCoreLootRules.permitsGamma(conquest));
        assertFalse(AiCoreLootRules.permitsGamma(null));
    }

    private static LootRollRequest request(MissionType type, RiskLevel risk,
                                           String factionId, String industryId) {
        return new LootRollRequest("mission-core", type, risk, factionId, industryId,
                25_000, 50);
    }
}
