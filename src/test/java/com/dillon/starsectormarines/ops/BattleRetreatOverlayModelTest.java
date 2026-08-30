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
            UiDocument document = document(instance);

            assertEquals(BattleRetreatOverlayModel.Presentation.RETREAT,
                    model.presentation());
            assertEquals("Retreat", instance.requireElement("battle-exit-action").text());
            assertTrue(instance.requireElement("battle-retreat-confirm")
                    .hasClass("battle-retreat-confirm-hidden"));

            click(document, instance.requireElement("battle-exit-action"));
            flush(instance, document);
            assertEquals(BattleRetreatOverlayModel.Presentation.CONFIRM,
                    model.presentation());
            assertEquals(0, retreats.get());
            assertFalse(instance.requireElement("battle-retreat-confirm")
                    .hasClass("battle-retreat-confirm-hidden"));

            click(document, instance.requireElement("battle-retreat-cancel"));
            flush(instance, document);
            assertEquals(BattleRetreatOverlayModel.Presentation.RETREAT,
                    model.presentation());
            assertEquals(0, retreats.get());

            click(document, instance.requireElement("battle-exit-action"));
            flush(instance, document);
            click(document, instance.requireElement("battle-retreat-confirm-action"));
            flush(instance, document);
            assertEquals(1, retreats.get());
            assertEquals(BattleRetreatOverlayModel.Presentation.RETREAT,
                    model.presentation());

            model.update(false);
            click(document, instance.requireElement("battle-exit-action"));
            flush(instance, document);
            model.update(true);
            flush(instance, document);
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

    private static UiDocument document(MarkupInstance instance) {
        UiDocument document = new UiDocument(instance.root());
        for (var style : instance.styles()) document.addStyleSheet(style);
        document.theme(MarineOpsThemes.standard());
        document.layout(BattleRetreatOverlayModel.Presentation.CONFIRM.documentWidth(),
                BattleRetreatOverlayModel.Presentation.CONFIRM.documentHeight());
        return document;
    }

    private static void flush(MarkupInstance instance, UiDocument document) {
        instance.flush();
        document.advance(0f);
        document.layout(BattleRetreatOverlayModel.Presentation.CONFIRM.documentWidth(),
                BattleRetreatOverlayModel.Presentation.CONFIRM.documentHeight());
    }

    private static void click(UiDocument document, UiElement element) {
        float x = element.box().borderBox().x() + element.box().borderBox().width() * 0.5f;
        float y = element.box().borderBox().y() + element.box().borderBox().height() * 0.5f;
        assertTrue(document.pointerDown(x, y));
        assertTrue(document.pointerUp(x, y));
    }
}
