package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.Client;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.fs.starfarer.api.campaign.RepLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpeningOperationStoryTest {

    private static final Client INDEPENDENT = new Client(
            "independent", "Independent", null, RepLevel.NEUTRAL,
            false, null);

    private static final OpeningOperationStory RELIEF =
            new OpeningOperationStory(OpeningOperationKind.RELIEF);
    private static final OpeningOperationStory COUNTERATTACK =
            new OpeningOperationStory(OpeningOperationKind.COUNTERATTACK);

    @Test
    void reliefComesFirstAndTheDepotFollowsIt() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(10);
        StoryEligibilityContext context = context(roster, seedOffering(RELIEF));

        assertTrue(RELIEF.isEligible(context));

        Mission mission = RELIEF.build(context);
        assertEquals(OpeningOperationKind.RELIEF.missionId, mission.id);
        assertEquals(MissionSource.STORY, mission.source);
        assertEquals(RiskLevel.LOW, mission.risk);
        assertEquals(2, mission.requiredDrops);
        assertEquals(1, mission.employerShuttles);
        assertTrue(mission.clientFighterSupport.isEmpty());
        assertTrue(mission.enemyFighterSupport.isEmpty());

        // The depot job is written as the sequel to a relief job, so it waits
        // for one — the only ordering left in the pair.
        StoryEligibilityContext depotBroker = context(roster, seedOffering(COUNTERATTACK));
        assertFalse(COUNTERATTACK.isEligible(depotBroker));
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        assertTrue(COUNTERATTACK.isEligible(depotBroker));

        Mission followup = COUNTERATTACK.build(depotBroker);
        assertEquals(3, followup.requiredDrops);
        assertEquals(1, followup.employerShuttles);
    }

    /**
     * The point of the change: a company that has grown does not lose access to
     * small work. It declines it because the payout stopped being worth a
     * sortie, which is a judgement, not a predicate.
     */
    @Test
    void aGrownCompanyIsStillOfferedMilitiaWork() {
        // Both halves of the retired gate: too many marines, and kit above
        // starting issue. Either one used to make the job disappear.
        MarineRoster grown = new MarineRoster();
        grown.bootstrapInitialComplement(36);
        assertTrue(grown.allocateArmor(
                soldierIds(grown).get(0), MarineArmorPattern.CHARCOAL));

        assertTrue(RELIEF.isEligible(context(grown, seedOffering(RELIEF))),
                "a company well past the old green gate is still shown the job");
    }

    @Test
    void completingTheWorkDoesNotRetireIt() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(10);
        StoryEligibilityContext context = context(roster, seedOffering(RELIEF));

        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);

        assertTrue(RELIEF.isEligible(context), "militia work recurs");
    }

    @Test
    void notEveryBrokerCarriesIt() {
        long offering = seedOffering(RELIEF);
        long quiet = seedNotOffering(RELIEF);
        assertNotEquals(offering, quiet);

        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(10);
        assertTrue(RELIEF.isEligible(context(roster, offering)));
        assertFalse(RELIEF.isEligible(context(roster, quiet)));

        // Stable per broker, so a board does not reshuffle across revisits.
        assertTrue(OpeningOperationStory.offeredAt(
                offering, OpeningOperationKind.RELIEF.missionId));
        assertTrue(OpeningOperationStory.offeredAt(
                offering, OpeningOperationKind.RELIEF.missionId));
    }

    @Test
    void thisWorkBelongsOnlyToIndependentBrokers() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(10);
        Client factionClient = new Client("hegemony", "Hegemony", null,
                RepLevel.NEUTRAL, false, null);

        assertFalse(RELIEF.isEligible(new StoryEligibilityContext(
                null, factionClient, null, roster, seedOffering(RELIEF))));
    }

    private static List<String> soldierIds(MarineRoster roster) {
        List<String> ids = new ArrayList<>();
        roster.soldiers().forEach(soldier -> ids.add(soldier.id()));
        return ids;
    }

    private static long seedOffering(OpeningOperationStory story) {
        return firstSeedWhere(story, true);
    }

    private static long seedNotOffering(OpeningOperationStory story) {
        return firstSeedWhere(story, false);
    }

    private static long firstSeedWhere(OpeningOperationStory story, boolean offered) {
        for (long seed = 0; seed < 512; seed++) {
            if (OpeningOperationStory.offeredAt(seed, story.id()) == offered) return seed;
        }
        throw new AssertionError("no seed produced offered=" + offered
                + " for " + story.id());
    }

    private static StoryEligibilityContext context(MarineRoster roster, long seed) {
        return new StoryEligibilityContext(null, INDEPENDENT, null, roster, seed);
    }
}
