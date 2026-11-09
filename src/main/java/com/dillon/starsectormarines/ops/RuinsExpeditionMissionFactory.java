package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ruins.RuinsExpeditionMissionKey;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.i18n.Strings;

/**
 * Builds the battle in which the company goes into a world's ruins ({@code ruins-expedition.md}).
 *
 * <p>It is the Ruins family's battle ({@link MissionType#RUINS}): the vault, its breach time from the grade, and the
 * haul out to a lift point. The occupant is still a stand-in pirate scavenger crew, its tier set by the grade, until
 * the site's seeded occupant exists ({@code ruins-occupants.md}).
 *
 * <p>It carries no contract and no employer: no contract economics, no MRB credibility, no salvage entitlement and no
 * employer boats. The ruins' haul comes from the game's own salvage, which a won expedition hands the site to once
 * the battle ends ({@code RuinsExpeditionLedger}).
 */
public final class RuinsExpeditionMissionFactory {

    /** The stand-in occupant: a pirate scavenger crew. */
    static final String STAND_IN_OCCUPANT = "pirates";

    private RuinsExpeditionMissionFactory() {}

    /**
     * @param serial     the serial drawn for this briefing ({@code RuinsExpeditionLedger.issue})
     * @param marketId   the ruins world's market id
     * @param planetName the ruins world, which the battle resolves its target profile from
     * @param grade      the ruins grade, which sets the tier
     */
    public static Mission create(int serial, String marketId, String planetName, RuinsGrade grade) {
        if (serial <= 0 || marketId == null || planetName == null || grade == null) return null;
        OperationTier tier = tierFor(grade);
        return Mission.builder()
                .id(RuinsExpeditionMissionKey.encode(serial, marketId))
                .name(Strings.format("ruinsExpeditionMissionName", planetName))
                .type(MissionType.RUINS)
                .source(MissionSource.RUINS_EXPEDITION)
                .risk(RiskLevel.MEDIUM)
                .tier(tier)
                .requirements(Strings.get("ruinsExpeditionRequirements"))
                .flavor(Strings.format("ruinsExpeditionFlavor", planetName))
                .mapPosition(0.5f, 0.5f)
                .requiredDrops(tier.drops)
                // Nobody else's boats come into the ruins.
                .employerShuttles(0)
                .targetPlanetName(planetName)
                .targetFactionId(STAND_IN_OCCUPANT)
                .ruinsGrade(grade)
                .contractId(-1L)
                .build();
    }

    /** Larger ruins hold a stronger occupant, no lower than the family's own floor. */
    static OperationTier tierFor(RuinsGrade grade) {
        OperationTier tier = switch (grade) {
            case SCATTERED -> OperationTier.FIRST_CONTRACT;
            case WIDESPREAD -> OperationTier.ESTABLISHED;
            case EXTENSIVE -> OperationTier.VETERAN;
            case VAST -> OperationTier.REINFORCED;
        };
        return OperationTier.clampTo(tier, MissionType.RUINS.tierFloor);
    }
}
