package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.FabricationCost;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import java.nio.file.Files;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BoatRecoveryLessonTest {
    @Test void portraitApertureLetsThePlayerPayTheDisplayedBill() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            var roster = new MarineRoster(); roster.boatDeck().reconcile("ship", ShuttleType.HERMES, 1);
            var boat = roster.boatDeck().boats().get(0);
            roster.boatDeck().settleRecovery("return", Map.of(boat.id(), .75f), List.of(), 10f);
            roster.campaignLessons().observeBoatDamage(boat.id());
            int[] spends = {0};
            var resources = new FabricationResources() {
                public int available(String id) { return 100; }
                public String commodityIcon(String id) { return ""; }
                public boolean spend(FabricationCost bill) { spends[0]++; return true; }
            };
            var reactor = new Reactor();
            var model = new BoatDeckViewModel(reactor, roster.boatDeck(), resources, "Second Wind", () -> {}, () -> 10f);
            model.inspect(boat.id());
            var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)), List.of(
                    "data/ui/components/company/company-resource-stock.mlx", "data/ui/components/company/company-resource-cost.mlx",
                    "data/ui/components/marine-ops-page-nav.mlx", "data/ui/components/boat-deck/boat-deck.mlx"));
            try (var markup = loader.reloadAndBuild(reactor, "boat-deck", BoatDeckViewModelTest.props(model))) {
                var document = PreparationGuide.document(markup.root()).theme(MarineOpsThemes.standard());
                markup.styles().forEach(document::addStyleSheet);
                var guide = new PreparationGuide(document, () -> CampaignLessons.boats(roster, model), () -> {},
                        new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                                List.of("data/ui/components/company/preparation-guide.mlx")));
                document.layout(1920, 1080); guide.update();
                var button = markup.requireElement("boat-recovery-expedite").box().borderBox();
                var panel = markup.requireElement("boat-recovery").box().borderBox();
                var callout = find(document.root(), "preparation-callout").box().borderBox();
                assertEquals(0f, callout.intersect(panel).width() * callout.intersect(panel).height(),
                        "Castle must not hide any part of the reviewed repair bill");
                assertTrue(button.height() >= 30 && button.bottom() <= panel.bottom());
                var track = markup.requireElement("boat-recovery-track").box().contentBox();
                var fill = markup.requireElement("boat-recovery-fill").box().borderBox();
                assertEquals(.25f, fill.width() / track.width(), .02f);
                float x = button.x() + button.width() / 2, y = button.y() + button.height() / 2;
                document.pointerMoved(x, y); document.pointerDown(x, y); document.pointerUp(x, y);
                markup.flush(); document.advance(0); guide.update();
                assertEquals(1, spends[0]); assertTrue(boat.airworthy(10f));
                assertTrue(CampaignLessons.boats(roster, model).copy().contains("finished"));
                assertTrue(roster.campaignLessons().boatRecoveryPending(), "Payment never silently acknowledges the lesson");
            }
        }
    }

    @Test void lessonResumesAfterDefeatAndExplainsWaitingWithoutRequiringPayment() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            MarineRoster roster = new MarineRoster();
            roster.boatDeck().reconcile("ship", ShuttleType.HERMES, 1);
            String id = roster.boatDeck().boats().get(0).id();
            roster.boatDeck().settleRecovery("defeat", Map.of(id, .5f), List.of(), 10f);
            roster.campaignLessons().observeBoatDamage(id);
            roster = CampaignLessonsTest.restore(roster);
            assertEquals(CampaignLessons.Step.BOAT_RECOVERY, CampaignLessons.next(roster));
            var model = new BoatDeckViewModel(new Reactor(), roster.boatDeck(), FabricationResources.NONE,
                    "Second Wind", () -> {}, () -> 10f);
            var inspect = CampaignLessons.boats(roster, model);
            inspect.action().run();
            var choice = CampaignLessons.boats(roster, model);
            assertTrue(choice.targets().contains("boat-recovery"));
            assertTrue(model.recovery().get().disabled());
            assertTrue(roster.campaignLessons().boatRecoveryPending());
            assertFalse(roster.boatDeck().boats().get(0).airworthy(10f));
            choice.action().run();
            assertFalse(CampaignLessonsTest.restore(roster).campaignLessons().boatRecoveryPending());
            assertTrue(roster.boatDeck().boats().get(0).airworthy(11f));
        }
    }
    @Test void completedOrMissingAircraftReconcileAnUnfinishedLesson() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            var roster = new MarineRoster(); roster.boatDeck().reconcile("ship", ShuttleType.HERMES, 1);
            var boat = roster.boatDeck().boats().get(0);
            roster.campaignLessons().observeBoatDamage(boat.id());
            var model = new BoatDeckViewModel(new Reactor(), roster.boatDeck(), FabricationResources.NONE,
                    "Second Wind", () -> {}, () -> 20f);
            model.inspect(boat.id());
            assertTrue(CampaignLessons.boats(roster, model).copy().contains("finished"));
            roster.boatDeck().lose(List.of(boat.id())); model.refresh();
            var gone = CampaignLessons.boats(roster, model);
            assertTrue(gone.copy().contains("no longer aboard"));
            gone.action().run();
            assertFalse(roster.campaignLessons().boatRecoveryPending());
            assertTrue(roster.boatDeck().airworthy(20f).isEmpty());
        }
    }
    private static UiElement find(UiElement element, String id) {
        if (id.equals(element.id())) return element;
        for (UiElement child : element.children()) {
            UiElement found = find(child, id);
            if (found != null) return found;
        }
        return null;
    }
}
