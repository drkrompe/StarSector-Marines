package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import com.dillon.starsectormarines.ui.retained.reactive.Signal;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BarracksTurnaroundTest {
    @Test void equipmentEntryKeepsSelectionAndUnreadLessonUntilReviewed() {
        var roster = new MarineRoster();
        var squad = roster.createSquad();
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        var model = new BarracksViewModel(new Reactor(), roster, () -> 0d).command();
        var routes = new ArrayList<ScreenId>();
        var context = new MarineOpsContext(null) {
            @Override public void goTo(ScreenId screen) { routes.add(screen); }
        };
        model.equipmentWorkspace(() -> context.openFleetArmoryWorkspaceFrom(ScreenId.BARRACKS));
        model.navigation(() -> {}, context::openSquadEquipmentFromBarracks, () -> {}, false);
        model.toggle(squad.id());
        model.inspect(squad.id());
        assertEquals("EQUIPMENT / NEW", value(model, "equipmentLabel"));
        action(model, "editEquipment");
        assertEquals(squad.id(), context.takeRequestedArmorySquadId());
        assertNull(context.takeRequestedArmorySquadId());
        model.leave();
        context.returnFromFleetArmoryWorkspace();
        assertEquals(List.of(ScreenId.FLEET_ARMORY, ScreenId.BARRACKS), routes);
        assertTrue(model.inspecting());
        assertTrue(model.selected().contains(squad.id()));
        assertEquals("EQUIPMENT / NEW", value(model, "equipmentLabel"));
        action(model, "equipmentWorkspace");
        assertFalse(roster.hasReviewedCompanyNotice(CompanyNotices.Notice.FIRST_REFIT.id));
        CompanyNotices.Notice.FIRST_REFIT.review(roster);
        model.refresh();
        assertEquals("EQUIPMENT", value(model, "equipmentLabel"));
        assertFalse(roster.firstRefit().complete());
    }

    @Test void distinctSelectionsCancelAndCommitThroughTheRoster() {
        var roster = new MarineRoster();
        var recipient = roster.createSquad();
        var donor = roster.createSquad();
        for (int i = 0; i < 6; i++) {
            roster.recruitToSquad(recipient.id()); roster.recruitToSquad(donor.id());
        }
        var model = new BarracksViewModel(new Reactor(), roster, () -> 0d).command();
        model.toggle(recipient.id());
        action(model, "chooseDonors");
        model.toggle(recipient.id());
        assertTrue(model.donors().isEmpty(), "Recipients cannot accidentally become donors");
        model.toggle(donor.id());
        model.reviewTransfers();
        action(model, "cancelTransfers");
        model.applyTransfers();
        assertEquals(6, roster.readyCount(recipient));
        action(model, "chooseDonors");
        model.toggle(donor.id());
        model.reviewTransfers();
        model.applyTransfers();
        assertEquals(12, roster.readyCount(recipient));
        assertEquals(0, roster.readyCount(donor));
        model.inspect(recipient.id());
        model.leave();
        model.showOverview();
        assertTrue(model.selected().contains(recipient.id()), "An equipment visit preserves selection");
    }

    @Test void guideFollowsSuccessfulOrdersAndNeverRequiresWastefulActions() throws Exception {
        CompanyManagementGuide.install(new JSONObject(Files.readString(Path.of("mod", CompanyManagementGuide.PATH))));
        try {
            var roster = new MarineRoster();
            roster.bootstrapInitialComplement(12);
            var recipient = roster.squads().stream().filter(s -> !s.reserve()).findFirst().orElseThrow();
            for (String id : recipient.memberIds().subList(0, 4)) {
                roster.applySoldierOutcome(Map.of(id, MarineSoldierStatus.KIA), 0f, 0f);
            }
            var donor = roster.createSquad();
            roster.recruitToSquad(donor.id(), 2);
            var model = new BarracksViewModel(new Reactor(), roster, () -> 0d).command();
            model.toggle(recipient.id());
            assertEquals("barracks-choose-donors", model.guideTargetId());
            model.applyTransfers();
            assertEquals("barracks-choose-donors", model.guideTargetId(), "An unreviewed order cannot advance the guide");
            action(model, "chooseDonors");
            model.toggle(donor.id());
            assertEquals("barracks-review-transfers", model.guideTargetId());
            model.reviewTransfers();
            assertEquals("barracks-apply-transfers", model.guideTargetId());
            model.applyTransfers();
            assertEquals(10, roster.readyCount(recipient));
            assertEquals("barracks-reinforce", model.guideTargetId());
            model.reinforceSelected();
            assertEquals("barracks-reinforce", model.guideTargetId(), "No available personnel means no false success");
            roster.recruitToSquad(roster.reserveSquad().id(), 2);
            model.reinforceSelected();
            assertEquals(12, roster.readyCount(recipient));
            assertEquals("barracks-issue", model.guideTargetId());
            var profile = roster.armory().saveSquadProfile("Security", SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,
                    SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
            roster.assignSquadProfile(Set.of(recipient.id()), profile.id());
            model.issueSelected();
            assertEquals("page-nav-return", model.guideTargetId());
            action(model, "guidePrevious");
            action(model, "guideNext");
            assertEquals(12, roster.readyCount(recipient), "Manual lesson navigation never performs orders");
        } finally { CompanyManagementGuide.install(new JSONObject("{\"lines\":[]}")); }
    }

    @Test void guideDismissalPersistsAndHelpReopensWithoutTouchingCompany() throws Exception {
        CompanyManagementGuide.install(new JSONObject(Files.readString(Path.of("mod", CompanyManagementGuide.PATH))));
        try {
            var roster = new MarineRoster();
            var model = new BarracksViewModel(new Reactor(), roster, () -> 0d).command();
            assertNotEquals("guide-callout hidden", value(model, "guideClasses"));
            action(model, "guideDismiss");
            assertEquals("guide-callout hidden", value(model, "guideClasses"));
            var bytes = new ByteArrayOutputStream();
            try (var output = new ObjectOutputStream(bytes)) { output.writeObject(roster); }
            MarineRoster loaded;
            try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                loaded = (MarineRoster) input.readObject();
            }
            assertTrue(loaded.managementGuideDismissed());
            var restored = new BarracksViewModel(new Reactor(), loaded, () -> 0d).command();
            assertEquals("guide-callout hidden", value(restored, "guideClasses"));
            action(restored, "guideHelp");
            assertNotEquals("guide-callout hidden", value(restored, "guideClasses"));
            action(restored, "guideNext");
            assertTrue(value(restored, "guideText").toString().contains("wounded"));
            assertEquals(0, loaded.lineReadySoldiers().size());
        } finally { CompanyManagementGuide.install(new JSONObject("{\"lines\":[]}")); }
    }

    @Test void preparationCannotDiscardAnUnsettledClaimAndDoesNotReopenSettlement() {
        var context = new MarineOpsContext(null);
        context.goTo(ScreenId.RESULTS);
        context.finishOperation(true);
        assertEquals(ScreenId.RESULTS, context.getCurrentScreen());
        context.prepareAfterSettlement(true);
        context.forfeitLoot();
        context.finishOperation(true);
        assertEquals(ScreenId.COMPANY_HQ, context.getCurrentScreen(), "No ship available in this fixture");
        assertTrue(context.isLootSettlementStarted());
        assertFalse(context.preparingAfterSettlement());
        assertFalse(context.tryBeginLootSettlement());
    }

    @Test void resultsPreparationReviewsSalvageBeforeLeavingAndNormalExitStillWorks() {
        var context = new MarineOpsContext(null);
        context.setLastOutcome(MissionOutcome.builder().victory(true).missionSource(MissionSource.GENERATED).build());
        var candidate = new com.dillon.starsectormarines.ops.loot.LootCandidate(
                com.dillon.starsectormarines.ops.loot.LootKind.COMMODITY, "supplies", "Supplies", null, 100, 1f, 1f, 1, 1);
        var manifest = new com.dillon.starsectormarines.ops.loot.LootManifest(1L, 100, 100,
                java.util.List.of(new com.dillon.starsectormarines.ops.loot.LootStack(candidate, 1)));
        context.setLootManifest(manifest);
        var screen = new ResultsScreen();
        screen.context = context;
        screen.prepareCompany();
        assertEquals(ScreenId.LOOT, context.getCurrentScreen());
        assertSame(manifest, context.getLootManifest());
        assertFalse(context.isLootSettlementStarted());
        assertTrue(context.preparingAfterSettlement());
        screen.returnToMissions();
        assertEquals(ScreenId.MISSION_SELECT, context.getCurrentScreen());
        assertTrue(context.isLootSettlementStarted());
    }

    @Test void defeatedResultsWithoutLootCanPrepareImmediately() {
        var context = new MarineOpsContext(null);
        context.setLastOutcome(MissionOutcome.builder().victory(false).missionSource(MissionSource.GENERATED).build());
        var screen = new ResultsScreen();
        screen.context = context;
        screen.prepareCompany();
        assertEquals(ScreenId.COMPANY_HQ, context.getCurrentScreen());
        assertNull(context.getLastOutcome());
        assertTrue(context.isLootSettlementStarted());
    }

    private static Object value(BarracksCommandModel model, String key) { return ((Signal<?>) model.props().get(key)).get(); }
    private static void action(BarracksCommandModel model, String key) { ((Runnable) model.props().get(key)).run(); }
}
