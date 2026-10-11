package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
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

import static org.junit.jupiter.api.Assertions.*;

class BarracksTurnaroundTest {
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

    @Test void guideDismissalPersistsAndHelpReopensWithoutTouchingCompany() throws Exception {
        CompanyManagementGuide.install(new JSONObject(Files.readString(Path.of("mod", CompanyManagementGuide.PATH))));
        try {
            var roster = new MarineRoster();
            var model = new BarracksViewModel(new Reactor(), roster, () -> 0d).command();
            assertNotEquals("hidden", value(model, "guideClasses"));
            action(model, "guideDismiss");
            assertEquals("hidden", value(model, "guideClasses"));
            var bytes = new ByteArrayOutputStream();
            try (var output = new ObjectOutputStream(bytes)) { output.writeObject(roster); }
            MarineRoster loaded;
            try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                loaded = (MarineRoster) input.readObject();
            }
            assertTrue(loaded.managementGuideDismissed());
            var restored = new BarracksViewModel(new Reactor(), loaded, () -> 0d).command();
            assertEquals("hidden", value(restored, "guideClasses"));
            action(restored, "guideHelp");
            assertNotEquals("hidden", value(restored, "guideClasses"));
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
