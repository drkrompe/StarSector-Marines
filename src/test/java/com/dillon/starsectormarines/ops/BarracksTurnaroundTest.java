package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.ops.loot.LootCandidate;
import com.dillon.starsectormarines.ops.loot.LootKind;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import com.dillon.starsectormarines.ops.loot.LootStack;
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
        model.navigation(context::openSquadEquipmentFromBarracks, () -> {}, false);
        model.toggle(squad.id());
        assertEquals("EQUIPMENT / NEW", value(model, "equipmentLabel"));
        ((Runnable) model.cards().get(0).get("loadout")).run();
        assertEquals(squad.id(), context.takeRequestedArmorySquadId());
        assertNull(context.takeRequestedArmorySquadId());
        model.leave();
        context.returnFromFleetArmoryWorkspace();
        assertEquals(List.of(ScreenId.FLEET_ARMORY, ScreenId.BARRACKS), routes);
        assertTrue(model.selected().contains(squad.id()));
        assertEquals("EQUIPMENT / NEW", value(model, "equipmentLabel"));
        action(model, "equipmentWorkspace");
        assertFalse(roster.hasReviewedCompanyNotice(CompanyNotices.Notice.FIRST_REFIT.id));
        CompanyNotices.Notice.FIRST_REFIT.review(roster);
        model.refresh();
        assertEquals("EQUIPMENT", value(model, "equipmentLabel"));
        assertFalse(roster.firstRefit().complete());
    }

    @Test void anEquipmentVisitPreservesSelection() {
        var roster = new MarineRoster();
        var squad = roster.createSquad();
        roster.recruitToSquad(squad.id(), 6);
        var model = new BarracksViewModel(new Reactor(), roster, () -> 0d).command();
        model.toggle(squad.id());
        model.leave();
        model.showOverview();
        assertTrue(model.selected().contains(squad.id()), "An equipment visit preserves selection");
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
            var model = new BarracksViewModel(new Reactor(), roster, () -> 0d).command();
            model.toggle(recipient.id());
            assertEquals("barracks-fill-selected", model.guideTargetId());
            model.reinforceSelected();
            assertEquals("barracks-fill-selected", model.guideTargetId(), "No available personnel means no false success");
            roster.recruitToSquad(roster.reserveSquad().id(), 4);
            model.reinforceSelected();
            assertEquals(12, roster.readyCount(recipient));
            assertEquals("page-nav-return", model.guideTargetId(), "Replacements need no separate issue order");
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

    @Test void theDebriefsEquipmentLessonSettlesTheOperationBeforeOpeningEquipment() {
        var context = new MarineOpsContext(null);
        context.setLastOutcome(MissionOutcome.builder().victory(true).missionSource(MissionSource.GENERATED).build());
        var screen = new ResultsScreen();
        screen.context = context;
        screen.refit();
        assertEquals(ScreenId.FLEET_ARMORY, context.getCurrentScreen());
        assertTrue(context.isLootSettlementStarted());
        assertNull(context.getLastOutcome(), "No company page sees a fought mission");
        context.returnFromFleetArmoryWorkspace();
        assertEquals(ScreenId.COMPANY_HQ, context.getCurrentScreen(), "No ship available in this fixture");
    }

    @Test void theDebriefsEquipmentLessonReviewsSalvageFirst() {
        var context = new MarineOpsContext(null);
        context.setLastOutcome(MissionOutcome.builder().victory(true).missionSource(MissionSource.GENERATED).build());
        var candidate = new LootCandidate(
                LootKind.COMMODITY, "supplies", "Supplies", null, 100, 1f, 1f, 1, 1);
        context.setLootManifest(new LootManifest(1L, 100, 100,
                List.of(new LootStack(candidate, 1))));
        var screen = new ResultsScreen();
        screen.context = context;
        screen.refit();
        assertEquals(ScreenId.LOOT, context.getCurrentScreen());
        assertFalse(context.isLootSettlementStarted());
        assertTrue(context.refittingAfterSettlement());
        context.forfeitLoot();
        context.finishOperation(context.preparingAfterSettlement());
        assertEquals(ScreenId.FLEET_ARMORY, context.getCurrentScreen());
        assertFalse(context.refittingAfterSettlement());
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
