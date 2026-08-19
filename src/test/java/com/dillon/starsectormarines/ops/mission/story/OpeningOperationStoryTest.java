package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.Client;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.fs.starfarer.api.campaign.RepLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningOperationStoryTest {

    private static final Client INDEPENDENT = new Client(
            "independent", "Independent", null, RepLevel.NEUTRAL,
            false, null);

    @Test
    void reliefStartsGreenCompanyLadderAndCounterattackFollowsVictory() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(10);
        StoryEligibilityContext context = context(roster, INDEPENDENT);
        OpeningOperationStory relief = new OpeningOperationStory(
                OpeningOperationKind.RELIEF);
        OpeningOperationStory counterattack = new OpeningOperationStory(
                OpeningOperationKind.COUNTERATTACK);

        assertTrue(relief.isEligible(context));
        assertFalse(counterattack.isEligible(context));

        Mission mission = relief.build(context);
        assertEquals(OpeningOperationKind.RELIEF.missionId, mission.id);
        assertEquals(MissionSource.STORY, mission.source);
        assertEquals(RiskLevel.LOW, mission.risk);
        assertEquals(2, mission.requiredDrops);
        assertEquals(1, mission.employerShuttles);
        assertTrue(mission.clientFighterSupport.isEmpty());
        assertTrue(mission.enemyFighterSupport.isEmpty());

        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        assertFalse(relief.isEligible(context));
        assertTrue(counterattack.isEligible(context));

        Mission followup = counterattack.build(context);
        assertEquals(3, followup.requiredDrops);
        assertEquals(1, followup.employerShuttles);
    }

    @Test
    void firstRungDoesNotFollowARegularOrOversizedCompany() {
        OpeningOperationStory relief = new OpeningOperationStory(
                OpeningOperationKind.RELIEF);
        MarineRoster experienced = new MarineRoster();
        experienced.bootstrapInitialComplement(10);
        experienced.soldiers().get(0).addExperience(100);
        assertFalse(relief.isEligible(context(experienced, INDEPENDENT)));

        MarineRoster oversized = new MarineRoster();
        oversized.bootstrapInitialComplement(19);
        assertFalse(relief.isEligible(context(oversized, INDEPENDENT)));
    }

    @Test
    void ladderBelongsOnlyToIndependentBroker() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(10);
        Client factionClient = new Client("hegemony", "Hegemony", null,
                RepLevel.NEUTRAL, false, null);

        assertFalse(new OpeningOperationStory(OpeningOperationKind.RELIEF)
                .isEligible(context(roster, factionClient)));
    }

    private static StoryEligibilityContext context(
            MarineRoster roster, Client client) {
        return new StoryEligibilityContext(null, client, null,
                roster, 41L);
    }
}
