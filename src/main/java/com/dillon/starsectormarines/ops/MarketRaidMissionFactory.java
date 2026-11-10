package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.setup.DefenderRoster;
import com.dillon.starsectormarines.battle.world.gen.MarketGarrison;
import com.dillon.starsectormarines.campaign.raid.MarketRaidMissionKey;
import com.dillon.starsectormarines.i18n.Strings;

/**
 * Builds the Raid in which the company fights the player's own raid on a market ({@code raid-resolution.md}).
 *
 * <p>The tier is read from the market's whole garrison headcount, no lower than Raid's own floor, and the
 * defenders are the share of that garrison the tier engages, as for any market-backed operation: choosing the
 * battle must not make the market harder or easier. A raid to disrupt an industry targets that industry.
 *
 * <p>It carries no contract and no employer: no contract economics, no MRB credibility, no salvage entitlement
 * and no employer boats. The raid's loot and its consequences come from the game's own raid command, which the
 * outcome is handed to once the battle ends ({@code MarketRaidLedger}).
 */
public final class MarketRaidMissionFactory {

    private MarketRaidMissionFactory() {}

    /**
     * @param serial          the serial drawn for this briefing ({@code MarketRaidLedger.issue})
     * @param marketId        the raided market's id
     * @param planetName      the market's planet, which the battle resolves its target profile from
     * @param marketFactionId the market's current owner, who defends it
     * @param industryId      the industry a disrupting raid targets, or null for a raid for valuables
     * @param garrison        the market's garrison estimate; {@link MarketGarrison#NONE} sizes the floor tier
     */
    public static Mission create(int serial, String marketId, String planetName, String marketFactionId,
                                 String industryId, MarketGarrison garrison) {
        return create(serial, marketId, planetName, marketFactionId, industryId, garrison, null);
    }

    /**
     * The same, with the industry's Raid site's line appended to the briefing: what the target is to the planet
     * ({@code raid-site-templates.md}, The planet's chokepoint), or {@code null} for none.
     */
    public static Mission create(int serial, String marketId, String planetName, String marketFactionId,
                                 String industryId, MarketGarrison garrison, String chokepoint) {
        if (serial <= 0 || marketId == null || planetName == null) return null;
        OperationTier tier = tierFor(garrison);
        return Mission.builder()
                .id(MarketRaidMissionKey.encode(serial, marketId))
                .name(Strings.format("marketRaidMissionName", planetName))
                .type(MissionType.RAID)
                .source(MissionSource.MARKET_RAID)
                .risk(RiskLevel.MEDIUM)
                .tier(tier)
                .requirements(Strings.get("marketRaidRequirements"))
                .flavor(flavor(planetName, industryId, chokepoint))
                .mapPosition(0.5f, 0.5f)
                .requiredDrops(tier.drops)
                // The player's own raid: nobody else's boats come.
                .employerShuttles(0)
                .targetPlanetName(planetName)
                .targetIndustryId(industryId)
                .targetFactionId(marketFactionId)
                .contractId(-1L)
                .build();
    }

    static String flavor(String planetName, String industryId, String chokepoint) {
        String base = Strings.format(industryId != null ? "marketRaidFlavorDisrupt" : "marketRaidFlavorValuables",
                planetName);
        return chokepoint == null || chokepoint.isEmpty() ? base : base + " " + chokepoint;
    }

    /** The tier the whole garrison's headcount falls in, no lower than Raid's own floor. */
    static OperationTier tierFor(MarketGarrison garrison) {
        return OperationTier.clampTo(
                OperationTierForStrength.forGroundStrength(DefenderRoster.wholeGarrisonCount(garrison)),
                MissionType.RAID.tierFloor);
    }
}
