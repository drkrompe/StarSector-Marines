package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.setup.DefenderRoster;
import com.dillon.starsectormarines.battle.world.gen.MarketGarrison;
import com.dillon.starsectormarines.campaign.nexerelin.NexInvasionMissionKey;
import com.dillon.starsectormarines.i18n.Strings;

/**
 * Builds the Conquest in which the company fights the player's own Nexerelin invasion of a market.
 *
 * <p>Conquest's own map, whatever the market's size: size reaches the battle only as the population dial and through
 * the garrison estimate ({@code nexerelin-nouns.md}). The defenders are that whole estimate, which launch applies
 * through {@link MissionDefencePolicy#garrisonFaced}; the tier here sizes only the lift and the briefing, read from
 * the same whole headcount. A weak garrison is a light defence on the full map, not a refused invasion.
 *
 * <p>It carries no contract and no employer: no contract economics, no MRB credibility, no employer boats. Settlement
 * runs through the mission's own key ({@code NexInvasionSettlement}).
 */
public final class NexInvasionMissionFactory {

    private NexInvasionMissionFactory() {}

    /**
     * @param serial          the settlement serial drawn for this briefing ({@code NexInvasionLedger.issue})
     * @param marketId        the invaded market's id, read back at settlement
     * @param planetName      the market's planet, which the battle resolves its target profile from
     * @param marketFactionId the market's current owner, who defends it
     * @param garrison        the market's garrison estimate; {@link MarketGarrison#NONE} sizes the floor tier
     */
    public static Mission create(int serial, String marketId, String planetName, String marketFactionId,
                                 MarketGarrison garrison) {
        if (serial <= 0 || marketId == null || planetName == null) return null;
        OperationTier tier = tierFor(garrison);
        return Mission.builder()
                .id(NexInvasionMissionKey.encode(serial, marketId))
                .name(Strings.format("nexInvasionMissionName", planetName))
                .type(MissionType.CONQUEST)
                .source(MissionSource.NEX_INVASION)
                .risk(RiskLevel.HIGH)
                .tier(tier)
                .requirements(Strings.get("nexInvasionRequirements"))
                .flavor(Strings.format("nexInvasionFlavor", planetName))
                .mapPosition(0.5f, 0.5f)
                .requiredDrops(tier.drops)
                // The player's own invasion: nobody else's boats come.
                .employerShuttles(0)
                .targetPlanetName(planetName)
                .targetFactionId(marketFactionId)
                .contractId(-1L)
                .build();
    }

    /**
     * The tier the whole garrison's headcount falls in, no lower than Conquest's own floor (which every Conquest
     * mission is clamped to anyway). It sizes the lift and the briefing only; the defenders are the whole count.
     */
    static OperationTier tierFor(MarketGarrison garrison) {
        return OperationTier.clampTo(
                OperationTierForStrength.forGroundStrength(DefenderRoster.wholeGarrisonCount(garrison)),
                MissionType.CONQUEST.tierFloor);
    }
}
