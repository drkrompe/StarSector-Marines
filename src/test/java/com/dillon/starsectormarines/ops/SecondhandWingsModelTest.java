package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.campaign.start.SecondhandWingsCampaign;
import com.dillon.starsectormarines.campaign.start.SecondhandWingsCampaign.Offer;
import com.dillon.starsectormarines.marine.BoatDeck;
import com.dillon.starsectormarines.marine.FabricationResources;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.SecondhandWings;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SecondhandWingsModelTest {
    @Test void onlyTheCompletedStoryUnlocksTheLead() {
        var roster = new MarineRoster();
        roster.markStoryComplete("sm_depot_clearance");
        roster.markStoryComplete(OpeningOperationKind.RELIEF.missionId);
        assertFalse(SecondhandWingsCampaign.available(roster));
        roster.markStoryComplete(OpeningOperationKind.COUNTERATTACK.missionId);
        assertTrue(SecondhandWingsCampaign.available(roster));
    }

    @Test void reviewPurchaseAndFittingUseRealControlsAndModalBlocksUnderlyingRoom() throws Exception {
        var reactor = new Reactor();
        var deck = new BoatDeck(); deck.reconcile("second-wind", ShuttleType.HERMES, 2);
        var boats = new BoatDeckViewModel(reactor, deck, FabricationResources.NONE, "Second Wind");
        AtomicInteger credits = new AtomicInteger(7000);
        AtomicInteger reviewed = new AtomicInteger();
        var wings = new SecondhandWingsModel(reactor, deck,
                () -> new Offer(!deck.secondhandWingsPurchased(), SecondhandWings.refusal(deck,
                        true, true, true, true, true, credits.get()), credits.get()),
                review -> SecondhandWings.purchase(deck, review, null,
                        n -> { credits.addAndGet(-n); return true; }), boats::refresh,
                () -> boats.selectBoatAction(0).run(), reviewed::incrementAndGet);
        var props = BoatDeckViewModelTest.props(boats); props.put("wings", wings.row());
        var loader = new MarkupLoader(path -> Files.readString(Path.of(path)), List.of(
                "mod/data/ui/components/company/company-resource-stock.mlx", "mod/data/ui/components/company/company-resource-cost.mlx", "mod/data/ui/components/marine-ops-page-nav.mlx", "mod/data/ui/components/boat-deck/boat-deck.mlx"));
        loader.reload();
        try (var markup = loader.build(reactor, "boat-deck", props)) {
            var document = new UiDocument(markup.root()); markup.styles().forEach(document::addStyleSheet);
            document.theme(MarineOpsThemes.standard());
            TutorialModal.bind(document, markup, wings::visible, "wings-modal", "boat-deck-root", wings::dismiss, () -> { });
            document.layout(1920, 1080);
            assertEquals(0, reviewed.get(), "Constructing or rendering the room does not acknowledge the offer");
            click(document, markup, "wings-resume");
            assertTrue(wings.visible());
            assertEquals(1, reviewed.get(), "Opening Castle's actual conversation acknowledges discovery");
            assertEquals(7000, credits.get(), "Discovery never spends credits");
            var modal = markup.requireElement("wings-dialog").box().borderBox();
            assertTrue(modal.x() >= 0 && modal.right() <= 1920 && modal.y() >= 0 && modal.bottom() <= 1080);
            click(document, markup, "boat:0");
            assertFalse(boats.fittingFocused(), "Backdrop must stop clicks reaching the berth");
            click(document, markup, "wings-action");
            assertEquals(1, wings.row().get().page());
            assertEquals(7000, credits.get(), "Review is not payment");
            click(document, markup, "wings-later");
            assertFalse(wings.visible());
            click(document, markup, "wings-resume");
            click(document, markup, "wings-action");
            click(document, markup, "wings-action");
            assertEquals(2000, credits.get());
            assertEquals(2, wings.row().get().page());
            click(document, markup, "wings-action");
            assertFalse(wings.visible());
            assertTrue(boats.fittingFocused());
            assertEquals(ShuttleType.AEROSHUTTLE, deck.pattern());
        }
    }

    private void click(UiDocument document, MarkupInstance markup, String id) {
        var box = markup.requireElement(id).box().borderBox();
        float x = box.x() + box.width() / 2, y = box.y() + box.height() / 2;
        document.pointerMoved(x, y); document.pointerDown(x, y); document.pointerUp(x, y);
        markup.flush(); document.advance(0);
    }
}
