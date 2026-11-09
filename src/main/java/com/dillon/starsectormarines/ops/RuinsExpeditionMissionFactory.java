package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ruins.RuinsExpeditionMissionKey;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.campaign.ruins.RuinsSurvey;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the battle in which the company goes into a world's ruins ({@code ruins-expedition.md}).
 *
 * <p>It is the Ruins family's battle ({@link MissionType#RUINS}): the vault, its breach time from the grade, and the
 * haul out to a lift point. It carries the site's seeded occupant ({@code RuinsOccupants}) for the battle to choose its
 * defenders by; the tier is set by the grade. Until an occupant's own side plays in battle, its defenders are the
 * scavengers' pirates ({@code BattleSetup.createRuins}), so the mission names that faction.
 *
 * <p>The briefing is the stakes, then the orbital survey ({@link RuinsSurvey}): the assessment naming the occupant,
 * the grade's strength band, and the site's survey readings.
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
        List<MissionDialogue.Turn> turns = new ArrayList<>();
        turns.add(MissionDialogue.prose(Strings.format("ruinsExpeditionFlavor", planetName)));
        turns.add(MissionDialogue.say(SURVEY_SPEAKER, Strings.get(survey.assessmentKey()) + " "
                + Strings.format(survey.bandKey(), Strings.get(survey.band().nameKey))));
        for (String reading : survey.readingKeys()) turns.add(MissionDialogue.say(SURVEY_SPEAKER, Strings.get(reading)));
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
        if (serial <= 0 || marketId == null || planetName == null || grade == null || occupant == null) return null;
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
                .dialogue(briefing(planetName, RuinsSurvey.read(surveyPool(occupant), marketId, grade)))
                .mapPosition(0.5f, 0.5f)
                .requiredDrops(tier.drops)
                // Nobody else's boats come into the ruins.
                .employerShuttles(0)
                .targetPlanetName(planetName)
                .targetFactionId(defenders)
                // An uncolonized world's market has no owner to defend it: the occupant's side does.
                .defenderFactionOverride(defenders)
                .ruinsGrade(grade)
                .ruinsOccupant(occupant)
                .contractId(-1L)
                .build();
    }

    /**
     * The faction the battle's defenders are drawn from. Only the scavengers' side plays in battle yet; every other
     * occupant's battle is the scavengers' stand-in ({@code BattleSetup.createRuins}), so it fields pirates too. When
     * an occupant's own side plugs in there, this returns its {@link RuinsOccupant#factionId}.
     */
    static String defenderFactionId(RuinsOccupant occupant) {
        return RuinsOccupant.SCAVENGERS.factionId;
    }

    /**
     * The readings the briefing draws from. An occupant whose own side does not play in battle yet reads as the
     * scavengers it stands in for ({@link #defenderFactionId}), so the survey stays true of the battle the company
     * meets; when that occupant's side plugs in, its own pool does too ({@link RuinsSurvey#poolFor}).
     */
    static RuinsSurvey.Pool surveyPool(RuinsOccupant occupant) {
        RuinsSurvey.Pool own = RuinsSurvey.poolFor(occupant);
        return own != null ? own : RuinsSurvey.SCAVENGERS;
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
