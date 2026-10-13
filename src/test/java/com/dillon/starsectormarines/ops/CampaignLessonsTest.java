package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.FirstRefit;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class CampaignLessonsTest {
    static MarineRoster afterCounterattack() {
        var roster = new MarineRoster();
        var squad = roster.createSquad(); roster.recruitToSquad(squad.id(), 12);
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        roster.firstRefit().claim(roster, FirstRefit.Path.STEADY_LINE);
        roster.firstRefit().issue(roster, squad.id(), FirstRefit.Path.STEADY_LINE, EquipmentIssueResources.UNLIMITED);
        roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        return roster;
    }

    @Test void rewardsAndFittingResumeFromTransactionsAfterSave() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            var roster = new MarineRoster();
            var squad = roster.createSquad(); roster.recruitToSquad(squad.id(), 12);
            assertEquals(CampaignLessons.Step.NONE, CampaignLessons.next(roster));
            roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
            assertEquals(CampaignLessons.Step.REFIT, CampaignLessons.next(roster));
            roster.firstRefit().claim(roster, FirstRefit.Path.FORWARD_SCREEN);
            roster = restore(roster);
            assertEquals(CampaignLessons.Step.REFIT, CampaignLessons.next(roster), "Learning is not issuing");
            assertFalse(roster.firstRefit().claim(roster, FirstRefit.Path.STEADY_LINE));
            var model = new FirstRefitModel(new Reactor(), roster, EquipmentIssueResources.UNLIMITED, id -> {});
            model.selectSquad(squad.id()); model.issue();
            assertTrue(roster.firstRefit().complete());
            assertEquals(CampaignLessons.Step.NONE, CampaignLessons.next(roster));
            roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
            roster.mechBay().receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment()));
            roster.mechBay().beginLabLesson();
            roster = restore(roster);
            assertEquals(CampaignLessons.Step.GANTRY, CampaignLessons.next(roster));
            var reopened = new MechLabViewModel(new Reactor(), roster.mechBay());
            reopened.resumeCampaignLesson();
            assertNotNull(reopened.campaignLesson());
            assertEquals(1, roster.mechBay().labLesson(), "Re-entry is not fitting practice");
        }
    }

    @Test void briefingNeedsSelectedReadyFormationsAndActualSupportSelection() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            var roster = afterCounterattack();
            assertEquals(CampaignLessons.Step.INVESTMENTS, CampaignLessons.next(roster));
            roster.campaignLessons().reviewInvestments();
            var second = roster.createSquad(); roster.recruitToSquad(second.id(), 12);
            Set<String> both = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
            var mission = SecondSquadPreparationTest.junction();
            assertNull(CampaignLessons.briefing(mission, roster, Set.of(second.id()), true, true).action());
            assertNull(CampaignLessons.briefing(mission, roster, both, true, false).action());
            var ready = CampaignLessons.briefing(mission, roster, both, true, true);
            assertNotNull(ready.action());
            assertFalse(roster.campaignLessons().junctionPrepared(), "Opening a briefing is not confirmation");
            ready.action().run();
            roster = restore(roster);
            assertTrue(roster.campaignLessons().junctionPrepared());
            assertTrue(roster.secondSquadPreparationSeen());
            assertEquals(24, roster.lineReadySoldiers().size(), "Guidance never creates personnel");
        }
    }

    @Test void deliveryReceiptRequiresCampaignCallAndLandingAndSurvivesSave() throws Exception {
        var roster = afterCounterattack();
        var progress = roster.campaignLessons();
        progress.reviewFieldGuidance(); assertFalse(progress.fieldGuidanceReviewed());
        progress.observeDelivery(false, true, true); assertFalse(progress.supportDelivered());
        progress.observeDelivery(true, false, true); assertFalse(progress.supportDelivered());
        progress.observeDelivery(true, true, false); assertFalse(progress.supportDelivered());
        progress.observeDelivery(true, true, true);
        roster = restore(roster);
        assertTrue(roster.campaignLessons().supportDelivered());
        assertFalse(roster.campaignLessons().fieldGuidanceReviewed());
        roster.campaignLessons().reviewFieldGuidance();
        assertTrue(restore(roster).campaignLessons().fieldGuidanceReviewed());
    }

    static MarineRoster restore(MarineRoster roster) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(roster); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return (MarineRoster) in.readObject(); }
    }
}
