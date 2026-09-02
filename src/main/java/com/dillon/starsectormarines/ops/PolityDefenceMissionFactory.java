package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.PolityDefenceMissionKey;
import com.dillon.starsectormarines.campaign.systems.VanillaRaidGarrisonSystem.RaidThreat;

/**
 * Builds the battle in which the company meets a vanilla raid on the player's own colony.
 *
 * <p>The same Assault shape a Garrison defence fights — the raiders have landed, destroy
 * their ground force — with the polity as the protected party rather than a patron. The
 * defender-faction override names the raider so they arrive in their own kit instead of
 * the defended colony's, and the tier comes from the raid's own ground strength rather
 * than from a patron's demand.
 *
 * <p>It carries no contract, so no salvage entitlement and no contract economics: the
 * detachment is a briefing-time selection and settlement runs through this mission's own
 * key. See `polity-defence-raid-hook.md`.
 */
public final class PolityDefenceMissionFactory {

    private PolityDefenceMissionFactory() {}

    /**
     * @param threat            the live raid, from {@code PolityThreatQuery}
     * @param marketSlot        {@code CampaignState.marketRegistry} slot of the colony
     * @param attackerFactionId vanilla id of the raiding faction, resolved from
     *                          {@code threat.attackerFactionId} by the caller that owns
     *                          the registries; {@code null} leaves the raiders in the
     *                          defended colony's kit
     * @param planetName        the colony's display name
     * @param marketFactionId   the colony's own faction, which the map is generated from
     */
    public static Mission create(RaidThreat threat, int marketSlot,
                                 String attackerFactionId, String planetName,
                                 String marketFactionId) {
        if (threat == null || planetName == null) return null;
        OperationTier tier = OperationTierForStrength.forGroundStrength(threat.groundStrength);
        String flavor = "A raid has put troops on the ground at " + planetName
                + ". Destroy the landing force before it takes the colony apart.";
        return Mission.builder()
                .id(PolityDefenceMissionKey.encode(marketSlot, threat.eventKey))
                .name("Colony Defence — " + planetName)
                .type(MissionType.ASSAULT)
                .source(MissionSource.POLITY_DEFENCE)
                .risk(RiskLevel.HIGH)
                .tier(tier)
                .requirements("Company detachment")
                .flavor(flavor)
                .mapPosition(0.5f, 0.5f)
                .requiredDrops(tier.drops)
                .employerShuttles(tier.drops)
                .targetPlanetName(planetName)
                .targetFactionId(marketFactionId)
                // No contract stands behind this fight, so nothing downstream should
                // look for a row: -1 is the builder's own "orphan mission" sentinel.
                .contractId(-1L)
                .defenderFactionOverride(attackerFactionId)
                .build();
    }
}
