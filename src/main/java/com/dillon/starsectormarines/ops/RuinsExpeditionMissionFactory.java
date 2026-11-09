package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ruins.RuinsExpeditionMissionKey;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.campaign.ruins.RuinsSiteFinds;
import com.dillon.starsectormarines.campaign.ruins.RuinsSurvey;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrize;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrizes;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds the battle in which the company goes into a world's ruins ({@code ruins-expedition.md}).
 *
 * <p>It is the Ruins family's battle ({@link MissionType#RUINS}): the vault, its breach time from the grade, and the
 * haul out to a lift point. It carries the site's seeded occupant ({@code RuinsOccupants}) for the battle to choose its
 * defenders by ({@code RuinsDefence}); the tier is set by the grade. The mission names the occupant's faction, or none
 * for an empty site or the Rav.
 *
 * <p>The briefing is the stakes, then the orbital survey ({@link RuinsSurvey}): the assessment naming the occupant,
 * the grade's strength band, the site's survey readings, and the tell of what its vault holds
 * ({@link RuinsVaultPrizes#tellKeys}).
 *
 * <p>It carries no contract and no employer: no contract economics, no MRB credibility, no salvage entitlement and no
 * employer boats. The ruins' haul comes from the game's own salvage, which a won expedition hands the site to once
 * the battle ends ({@code RuinsExpeditionLedger}).
 */
public final class RuinsExpeditionMissionFactory {

    static final String SURVEY_SPEAKER = "@ruinsSurveySpeaker";

    private RuinsExpeditionMissionFactory() {}

    /** The stakes, then the survey: the assessment with its strength, and one turn per reading. */
    static List<MissionDialogue.Turn> briefing(String planetName, RuinsSurvey.Reading survey) {
        return briefing(planetName, survey, List.of());
    }

    /** The briefing with the vault's tell after the readings, one turn per tell key. */
    static List<MissionDialogue.Turn> briefing(String planetName, RuinsSurvey.Reading survey, List<String> tellKeys) {
        List<MissionDialogue.Turn> turns = new ArrayList<>();
        turns.add(MissionDialogue.prose(Strings.format("ruinsExpeditionFlavor", planetName)));
        turns.add(MissionDialogue.say(SURVEY_SPEAKER, Strings.get(survey.assessmentKey()) + " "
                + Strings.format(survey.bandKey(), Strings.get(survey.band().nameKey))));
        for (String reading : survey.readingKeys()) turns.add(MissionDialogue.say(SURVEY_SPEAKER, Strings.get(reading)));
        for (String tell : tellKeys) turns.add(MissionDialogue.say(SURVEY_SPEAKER, Strings.get(tell)));
        return List.copyOf(turns);
    }

    /**
     * @param serial     the serial drawn for this briefing ({@code RuinsExpeditionLedger.issue})
     * @param marketId   the ruins world's market id
     * @param planetName the ruins world, which the battle resolves its target profile from
     * @param grade      the ruins grade, which sets the tier
     * @param occupant   who holds the site ({@code SectorRuinsOccupants.of}), which chooses the defenders
     */
    public static Mission create(int serial, String marketId, String planetName, RuinsGrade grade,
                                 RuinsOccupant occupant) {
        return create(serial, marketId, planetName, grade, occupant, Set.of());
    }

    /**
     * @param takenFinds the ids of the site's finds already brought home ({@link RuinsSiteFinds}), left out of the
     *                   battle; the battle's seed is the site's, so every other find is where it was
     */
    public static Mission create(int serial, String marketId, String planetName, RuinsGrade grade,
                                 RuinsOccupant occupant, Set<String> takenFinds) {
        return create(serial, marketId, planetName, grade, occupant, takenFinds, null);
    }

    /**
     * @param sectorSeed the save's sector seed, which with the site draws its vault prize ({@link RuinsVaultPrizes})
     *                   and so the briefing's tell; null leaves the tell out
     */
    public static Mission create(int serial, String marketId, String planetName, RuinsGrade grade,
                                 RuinsOccupant occupant, Set<String> takenFinds, Long sectorSeed) {
        if (serial <= 0 || marketId == null || planetName == null || grade == null || occupant == null) return null;
        // The vault's prize, drawn beside the occupant from the same site facts.
        RuinsVaultPrize prize = sectorSeed != null
                ? RuinsVaultPrizes.draw(sectorSeed, marketId, grade, occupant) : null;
        List<String> tells = prize != null ? RuinsVaultPrizes.tellKeys(sectorSeed, marketId, prize) : List.of();
        OperationTier tier = tierFor(grade);
        String defenders = defenderFactionId(occupant);
        return Mission.builder()
                .id(RuinsExpeditionMissionKey.encode(serial, marketId))
                .name(Strings.format("ruinsExpeditionMissionName", planetName))
                .type(MissionType.RUINS)
                .source(MissionSource.RUINS_EXPEDITION)
                .risk(RiskLevel.MEDIUM)
                .tier(tier)
                .requirements(Strings.get("ruinsExpeditionRequirements"))
                .dialogue(briefing(planetName, RuinsSurvey.read(surveyPool(occupant), marketId, grade), tells))
                .mapPosition(0.5f, 0.5f)
                .requiredDrops(tier.drops)
                // Nobody else's boats come into the ruins.
                .employerShuttles(0)
                .targetPlanetName(planetName)
                .targetFactionId(defenders)
                // An uncolonized world's market has no owner to defend it: the occupant's side does.
                .defenderFactionOverride(defenders)
                .ruinsGrade(grade)
                .ruinsTakenFinds(takenFinds == null ? Set.of() : takenFinds)
                .ruinsPrize(prize)
                // The same ground on every expedition: retry meets the same ruins (ruins-expedition.md).
                .battleSeed(RuinsSiteFinds.battleSeed(marketId))
                .ruinsOccupant(occupant)
                .contractId(-1L)
                .build();
    }

    /** The faction the battle's defenders are drawn from; none for nobody or the Rav, which no faction stands for. */
    static String defenderFactionId(RuinsOccupant occupant) {
        return occupant.factionId;
    }

    /** The readings the briefing draws from: the occupant's own, since its own side (or no one) is what is met. */
    static RuinsSurvey.Pool surveyPool(RuinsOccupant occupant) {
        return RuinsSurvey.poolFor(occupant);
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
