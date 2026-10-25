package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.BattleRetreatOverlayModel.Phase;
import com.dillon.starsectormarines.ops.BattleRetreatOverlayModel.Presentation;
import com.dillon.starsectormarines.ops.BattleRetreatOverlayModel.RetryOffer;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRetreatOverlayModelTest {

    private static final RetryOffer FREE = new RetryOffer(true, true, "Free restart", "Free retry");
    private static final RetryOffer BROKE =
            new RetryOffer(true, false, "Restart needs a story point", "Retry needs a story point");

    private static final class Counts implements BattleRetreatOverlayModel.Actions {
        int withdrawals;
        int proceeds;
        int retries;

        @Override public void withdraw() { withdrawals++; }
        @Override public void proceed() { proceeds++; }
        @Override public void retry() { retries++; }
    }

    private static BattleRetreatOverlayModel model(Reactor reactor, Counts counts) {
        return new BattleRetreatOverlayModel(reactor, counts, new BattleRetreatOverlayModel.Labels(
                "Retreat", "Continue", "Accept defeat", "Withdraw and accept defeat?",
                "Troops away from the landing zone come home wounded.", "Cancel", "Withdraw"));
    }

    @Test
    void retreatArmsAConfirmationOfferingRestartWithdrawAndCancel() throws Exception {
        Reactor reactor = new Reactor();
        Counts counts = new Counts();
        BattleRetreatOverlayModel model = model(reactor, counts);
        model.update(Phase.ACTIVE, FREE);
        try (MarkupInstance instance = build(reactor, model)) {
            UiDocument document = document(instance, model.presentation());
            assertEquals(Presentation.RETREAT, model.presentation());
            assertFills(instance, "battle-exit-action", model.presentation());
            assertTrue(instance.requireElement("battle-retry-action").hasClass("battle-exit-action-hidden"));

            click(document, instance.requireElement("battle-exit-action"));
            flush(instance, document, model.presentation());
            assertEquals(Presentation.CONFIRM, model.presentation());
            assertFills(instance, "battle-retreat-confirm", model.presentation());
            for (String id : List.of("battle-retreat-restart", "battle-retreat-confirm-action", "battle-retreat-cancel")) {
                assertInside(instance, "battle-retreat-confirm", id);
            }
            assertEquals("Free restart", instance.requireElement("battle-retreat-restart").text());

            click(document, instance.requireElement("battle-retreat-cancel"));
            flush(instance, document, model.presentation());
            assertEquals(Presentation.RETREAT, model.presentation());

            click(document, instance.requireElement("battle-exit-action"));
            flush(instance, document, model.presentation());
            click(document, instance.requireElement("battle-retreat-restart"));
            flush(instance, document, model.presentation());
            assertEquals(1, counts.retries);
            assertEquals(0, counts.withdrawals);

            click(document, instance.requireElement("battle-exit-action"));
            flush(instance, document, model.presentation());
            click(document, instance.requireElement("battle-retreat-confirm-action"));
            flush(instance, document, model.presentation());
            assertEquals(1, counts.withdrawals);
            assertEquals(Presentation.RETREAT, model.presentation());
        }
    }

    @Test
    void lostStoryOperationOffersRetryBesideAcceptDefeat() throws Exception {
        Reactor reactor = new Reactor();
        Counts counts = new Counts();
        BattleRetreatOverlayModel model = model(reactor, counts);
        model.update(Phase.DEFEAT, FREE);
        try (MarkupInstance instance = build(reactor, model)) {
            UiDocument document = document(instance, model.presentation());
            assertEquals(Presentation.DEFEAT_RETRY, model.presentation());
            assertTrue(model.presentation().centred());
            assertEquals("Accept defeat", instance.requireElement("battle-exit-action").text());
            assertEquals("Free retry", instance.requireElement("battle-retry-action").text());
            assertInside(instance, "battle-retreat-overlay", "battle-retry-action");
            assertInside(instance, "battle-retreat-overlay", "battle-exit-action");

            click(document, instance.requireElement("battle-retry-action"));
            assertEquals(1, counts.retries);
            click(document, instance.requireElement("battle-exit-action"));
            assertEquals(1, counts.proceeds);
        }
    }

    @Test
    void anUnaffordableRetryIsShownButDoesNothing() {
        Counts counts = new Counts();
        BattleRetreatOverlayModel model = model(new Reactor(), counts);
        model.update(Phase.DEFEAT, BROKE);
        ((Runnable) model.props().get("retryAction")).run();
        model.update(Phase.ACTIVE, BROKE);
        ((Runnable) model.props().get("action")).run();
        ((Runnable) model.props().get("restartAction")).run();
        assertEquals(0, counts.retries);
        assertEquals(Presentation.CONFIRM, model.presentation());
    }

    @Test
    void victoryAndUnretryableDefeatContinueFromTheCentre() {
        Counts counts = new Counts();
        BattleRetreatOverlayModel model = model(new Reactor(), counts);
        assertEquals(Presentation.CONTINUE, model.update(Phase.VICTORY, RetryOffer.NONE));
        assertTrue(model.presentation().centred());
        assertEquals(Presentation.CONTINUE, model.update(Phase.DEFEAT, RetryOffer.NONE));
        ((Runnable) model.props().get("retryAction")).run();
        ((Runnable) model.props().get("action")).run();
        assertEquals(0, counts.retries);
        assertEquals(1, counts.proceeds);
        assertFalse(Presentation.RETREAT.centred());
    }

    @Test
    void enteringUnitControlCancelsAnArmedRetreatWithoutAbandoningTheBattle() {
        Counts counts = new Counts();
        BattleRetreatOverlayModel model = model(new Reactor(), counts);
        model.update(Phase.ACTIVE, RetryOffer.NONE);
        ((Runnable) model.props().get("action")).run();
        assertEquals(Presentation.CONFIRM, model.presentation());
        model.cancelRetreat();
        assertEquals(Presentation.RETREAT, model.presentation());
        ((Runnable) model.props().get("confirmAction")).run();
        assertEquals(0, counts.withdrawals);
    }

    private static MarkupInstance build(Reactor reactor, BattleRetreatOverlayModel model) throws Exception {
        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                Path.of("mod").resolve(path)), List.of(BattleRetreatOverlay.COMPONENT_PATH));
        loader.reload();
        return loader.build(reactor, BattleRetreatOverlay.COMPONENT, model.props());
    }

    private static void assertFills(MarkupInstance instance, String id, Presentation presentation) {
        var box = instance.requireElement(id).box().borderBox();
        assertEquals(presentation.documentWidth(), box.width(), id + " width");
        assertEquals(presentation.documentHeight(), box.height(), id + " height");
    }

    /** The child's border box lies within the parent's: nothing clips against the document edge. */
    private static void assertInside(MarkupInstance instance, String parentId, String childId) {
        var parent = instance.requireElement(parentId).box().borderBox();
        var child = instance.requireElement(childId).box().borderBox();
        assertTrue(child.width() > 0f && child.height() > 0f, childId + " collapsed");
        assertTrue(child.x() >= parent.x() && child.x() + child.width() <= parent.x() + parent.width()
                && child.y() >= parent.y() && child.y() + child.height() <= parent.y() + parent.height(),
                childId + " escapes " + parentId);
    }

    private static UiDocument document(MarkupInstance instance, Presentation presentation) {
        UiDocument document = new UiDocument(instance.root());
        for (var style : instance.styles()) document.addStyleSheet(style);
        document.theme(MarineOpsThemes.standard());
        document.layout(presentation.documentWidth(), presentation.documentHeight());
        return document;
    }

    private static void flush(MarkupInstance instance, UiDocument document, Presentation presentation) {
        instance.flush();
        document.advance(0f);
        document.layout(presentation.documentWidth(), presentation.documentHeight());
    }

    private static void click(UiDocument document, UiElement element) {
        float x = element.box().borderBox().x() + element.box().borderBox().width() * 0.5f;
        float y = element.box().borderBox().y() + element.box().borderBox().height() * 0.5f;
        assertTrue(document.pointerDown(x, y));
        assertTrue(document.pointerUp(x, y));
    }
}
