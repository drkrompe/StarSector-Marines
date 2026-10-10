package com.dillon.starsectormarines.ops.mission.story;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.Client;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.fs.starfarer.api.campaign.RepLevel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecurringMilitiaWorkTest {
    @Test void distinctPostingsUnlockOnlyAfterTheArcAndKeepTheirBattleSetup() {
        MarineRoster roster = new MarineRoster();
        Client broker = new Client("independent", "Broker", null, RepLevel.NEUTRAL, false, null);
        StoryEligibilityContext context = new StoryEligibilityContext(null, broker, null, roster, 17, false);
        for (OpeningOperationKind kind : OpeningOperationKind.values()) {
            assertFalse(new RecurringMilitiaWork(kind).isEligible(context));
        }
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        assertFalse(new RecurringMilitiaWork(OpeningOperationKind.RELIEF).isEligible(context));
        roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        for (OpeningOperationKind kind : OpeningOperationKind.values()) {
            RecurringMilitiaWork work = new RecurringMilitiaWork(kind);
            assertTrue(work.isEligible(context));
            Mission job = work.build(context);
            assertEquals(MissionSource.RECURRING, job.source);
            assertNotEquals(kind.missionId, job.id);
            assertEquals(kind, OpeningOperationKind.fromMission(job));
            assertEquals(kind.payout, job.payout);
            assertEquals(kind == OpeningOperationKind.RELIEF ? 2 : 3, job.requiredDrops);
            assertEquals(1, job.employerShuttles);
            roster.markStoryComplete(job.id);
            assertTrue(work.isEligible(context), "even an old erroneous completion key cannot retire ordinary work");
        }
    }

    @Test void restrictedClientsCannotBorrowRecurringWork() {
        MarineRoster roster = new MarineRoster();
        roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        RecurringMilitiaWork work = new RecurringMilitiaWork(OpeningOperationKind.RELIEF);
        assertFalse(work.isEligible(null));
        for (Client client : new Client[] {
                new Client("hegemony", "Faction", null, RepLevel.NEUTRAL, false, null),
                new Client("independent", "Locked", null, RepLevel.HOSTILE, true, "hostile"),
                new Client("independent", "Patron", null, RepLevel.NEUTRAL, false, null, 42L)}) {
            assertFalse(work.isEligible(new StoryEligibilityContext(null, client, null, roster, 17, true)));
        }
    }
}
