package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
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

    private static final OpeningOperationStory RELIEF =
            new OpeningOperationStory(OpeningOperationKind.RELIEF);
    private static final OpeningOperationStory COUNTERATTACK =
            new OpeningOperationStory(OpeningOperationKind.COUNTERATTACK);

    @Test
    void reliefComesFirstAndTheDepotFollowsIt() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(10);
        StoryEligibilityContext context = context(roster, 17L);

        assertTrue(RELIEF.isEligible(context));

        Mission mission = RELIEF.build(context);
        assertEquals(OpeningOperationKind.RELIEF.missionId, mission.id);
        assertEquals(MissionSource.STORY, mission.source);
        assertEquals(RiskLevel.LOW, mission.risk);
        assertTrue(mission.flavor.contains("Mara Venn"));
        assertTrue(mission.flavor.contains("Wayline"));
        assertEquals(2, mission.requiredDrops);
        assertEquals(1, mission.employerShuttles);
        assertTrue(mission.clientFighterSupport.isEmpty());
        assertTrue(mission.enemyFighterSupport.isEmpty());

        // The depot job is written as the sequel to a relief job, so it waits
        // for one — the only ordering left in the pair.
        StoryEligibilityContext depotBroker = context(roster, 29L);
        assertFalse(COUNTERATTACK.isEligible(depotBroker));
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        assertTrue(COUNTERATTACK.isEligible(depotBroker));

        Mission followup = COUNTERATTACK.build(depotBroker);
        assertTrue(followup.flavor.contains("You kept Wayline's line intact"));
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
        assertEquals(SquadEquipmentResult.APPLIED, grown.applySquadEquipment(
                grown.squads().get(0).id(),
                SquadEquipmentDoctrines.LINE_INFANTRY_WEAPONS,
                SquadEquipmentDoctrines.LEAGUE_LINE_ARMOR));

        assertTrue(RELIEF.isEligible(context(grown, 17L)),
                "a company well past the old green gate is still shown the job");
    }

    @Test
    void completingTheWorkDoesNotRetireIt() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(10);
        StoryEligibilityContext context = context(roster, 17L);

        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);

        assertTrue(RELIEF.isEligible(context), "militia work recurs");
    }

    @Test
    void theOpeningBrokerCarriesReliefAtEverySeedBeforeTheFirstSquad() {
        MarineRoster roster = new MarineRoster();
        for (long seed : new long[]{0, 1, 2, 17, 29, -19, Long.MAX_VALUE}) {
            StoryEligibilityContext context = context(roster, seed);
            assertTrue(RELIEF.isEligible(context), "seed must not hide introductory work");
            assertFalse(COUNTERATTACK.isEligible(context));
            Mission first = RELIEF.build(context);
            Mission revisit = RELIEF.build(context);
            assertEquals(first.normalizedX, revisit.normalizedX);
            assertEquals(first.normalizedY, revisit.normalizedY);
        }
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        for (long seed : new long[]{0, 1, 2, -19}) {
            assertTrue(RELIEF.isEligible(context(roster, seed)));
            assertTrue(COUNTERATTACK.isEligible(context(roster, seed)));
        }
    }

    @Test
    void missingCompanyOrClientDoesNotCreateAnOffer() {
        assertFalse(RELIEF.isEligible(null));
        assertFalse(RELIEF.isEligible(context(null, 0)));
        assertFalse(RELIEF.isEligible(new StoryEligibilityContext(null, null, null, new MarineRoster(), 0)));
    }

    @Test
    void thisWorkBelongsOnlyToIndependentBrokers() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(10);
        Client factionClient = new Client("hegemony", "Hegemony", null,
                RepLevel.NEUTRAL, false, null);

        assertFalse(RELIEF.isEligible(new StoryEligibilityContext(
                null, factionClient, null, roster, 17L)));
    }

    @Test
    void otherBrokersWaitForTheWholeIntroductionButExistingVictoriesStillCount() {
        MarineRoster roster = new MarineRoster();
        StoryEligibilityContext remote = new StoryEligibilityContext(null, INDEPENDENT, null, roster, 17L, false);
        assertFalse(RELIEF.isEligible(remote));
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        assertFalse(RELIEF.isEligible(remote));
        assertFalse(COUNTERATTACK.isEligible(remote));
        assertTrue(COUNTERATTACK.isEligible(context(roster, 17L)));
        roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        assertTrue(RELIEF.isEligible(remote));
        assertTrue(COUNTERATTACK.isEligible(remote));
        assertFalse(RELIEF.build(remote).flavor.contains("Mara Venn"));
    }

    @Test
    void patronsAndLockedClientsNeverBorrowTheOpeningSequence() {
        MarineRoster roster = new MarineRoster();
        for (Client client : new Client[]{
                new Client("independent", "House", null, RepLevel.NEUTRAL, false, null, 42L),
                new Client("independent", "Locked", null, RepLevel.HOSTILE, true, "hostile")}) {
            assertFalse(RELIEF.isEligible(new StoryEligibilityContext(null, client, null, roster, 0, true)));
        }
    }

    private static StoryEligibilityContext context(MarineRoster roster, long seed) {
        return new StoryEligibilityContext(null, INDEPENDENT, null, roster, seed, true);
    }
}
