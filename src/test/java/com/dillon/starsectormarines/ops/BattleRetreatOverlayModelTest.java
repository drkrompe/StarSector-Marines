package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRetreatOverlayModelTest {

    @Test
    void mlxStagesRetreatConfirmationAndKeepsCompletedBattleOnContinuePath()
            throws Exception {
        Reactor reactor = new Reactor();
        AtomicInteger retreats = new AtomicInteger();
        AtomicInteger continues = new AtomicInteger();
        BattleRetreatOverlayModel model = new BattleRetreatOverlayModel(
                reactor, retreats::incrementAndGet, continues::incrementAndGet,
                "Retreat", "Continue", "Abandon operation?", "Cancel", "Retreat");

        MarkupLoader loader = new MarkupLoader(path -> Files.readString(
                Path.of("mod").resolve(path)), List.of(BattleRetreatOverlay.COMPONENT_PATH));
        loader.reload();
        try (MarkupInstance instance = loader.build(
                reactor, BattleRetreatOverlay.COMPONENT, model.props())) {
            UiDocument document = document(instance, model.presentation());

            assertEquals(BattleRetreatOverlayModel.Presentation.RETREAT,
                    model.presentation());
            assertEquals("Retreat", instance.requireElement("battle-exit-action").text());
            assertEquals(model.presentation().documentWidth(),
                    instance.requireElement("battle-exit-action")
                            .box().borderBox().width());
            assertEquals(model.presentation().documentHeight(),
                    instance.requireElement("battle-exit-action")
                            .box().borderBox().height());
            assertTrue(instance.requireElement("battle-retreat-confirm")
                    .hasClass("battle-retreat-confirm-hidden"));

            click(document, instance.requireElement("battle-exit-action"));
            flush(instance, document, model.presentation());
            assertEquals(BattleRetreatOverlayModel.Presentation.CONFIRM,
                    model.presentation());
            assertEquals(0, retreats.get());
            assertFalse(instance.requireElement("battle-retreat-confirm")
                    .hasClass("battle-retreat-confirm-hidden"));
            assertEquals(model.presentation().documentWidth(),
                    instance.requireElement("battle-retreat-confirm")
                            .box().borderBox().width());
            assertEquals(model.presentation().documentHeight(),
                    instance.requireElement("battle-retreat-confirm")
                            .box().borderBox().height());

            click(document, instance.requireElement("battle-retreat-cancel"));
            flush(instance, document, model.presentation());
            assertEquals(BattleRetreatOverlayModel.Presentation.RETREAT,
                    model.presentation());
            assertEquals(0, retreats.get());

            click(document, instance.requireElement("battle-exit-action"));
            flush(instance, document, model.presentation());
            click(document, instance.requireElement("battle-retreat-confirm-action"));
            flush(instance, document, model.presentation());
            assertEquals(1, retreats.get());
            assertEquals(BattleRetreatOverlayModel.Presentation.RETREAT,
                    model.presentation());

            model.update(false);
            click(document, instance.requireElement("battle-exit-action"));
            flush(instance, document, model.presentation());
            model.update(true);
            flush(instance, document, model.presentation());
            assertEquals(BattleRetreatOverlayModel.Presentation.CONTINUE,
                    model.presentation());
            assertTrue(instance.requireElement("battle-retreat-confirm")
                    .hasClass("battle-retreat-confirm-hidden"));
            assertEquals("Continue", instance.requireElement("battle-exit-action").text());

            click(document, instance.requireElement("battle-exit-action"));
            assertEquals(1, continues.get());
            assertEquals(1, retreats.get());
        }
    }

    @Test
    void enteringUnitControlCancelsAnArmedRetreatWithoutAbandoningTheBattle() {
        AtomicInteger retreats = new AtomicInteger();
        BattleRetreatOverlayModel model = new BattleRetreatOverlayModel(new Reactor(),
                retreats::incrementAndGet, () -> {}, "Retreat", "Continue",
                "Abandon operation?", "Cancel", "Retreat");
        ((Runnable) model.props().get("action")).run();
        assertEquals(BattleRetreatOverlayModel.Presentation.CONFIRM, model.presentation());
        model.cancelRetreat();
        assertEquals(BattleRetreatOverlayModel.Presentation.RETREAT, model.presentation());
        ((Runnable) model.props().get("confirmAction")).run();
        assertEquals(0, retreats.get());
    }

    private static UiDocument document(
            MarkupInstance instance, BattleRetreatOverlayModel.Presentation presentation) {
        UiDocument document = new UiDocument(instance.root());
        for (var style : instance.styles()) document.addStyleSheet(style);
        document.theme(MarineOpsThemes.standard());
        document.layout(presentation.documentWidth(), presentation.documentHeight());
        return document;
    }

    private static void flush(MarkupInstance instance, UiDocument document,
                              BattleRetreatOverlayModel.Presentation presentation) {
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
