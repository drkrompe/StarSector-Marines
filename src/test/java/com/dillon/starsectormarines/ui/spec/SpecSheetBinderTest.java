package com.dillon.starsectormarines.ui.spec;

import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a hover does, and what it deliberately does not do again. */
class SpecSheetBinderTest {

    @Test
    void hoveringABoundElementOpensItsSheet() {
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            SpecSheetBinder binder = new SpecSheetBinder(document, layer);
            UiElement anchor = instance.requireElement("near-left");
            binder.bind(anchor, SpecSheetDocuments.sheet("armor"));

            hover(document, anchor);
            binder.update();

            assertTrue(layer.visible());
            assertSame(anchor, binder.openTarget());
            assertTrue(anchor.hovered(), "the overlay took the hover that opened it");
        }
    }

    @Test
    void movingOffABoundElementClosesTheSheet() {
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            SpecSheetBinder binder = new SpecSheetBinder(document, layer);
            UiElement anchor = instance.requireElement("near-left");
            binder.bind(anchor, SpecSheetDocuments.sheet("armor"));
            hover(document, anchor);
            binder.update();

            document.pointerMoved(600f, 500f);
            binder.update();

            assertFalse(layer.visible());
            assertNull(binder.openTarget());
        }
    }

    /** An unbound element is not a subject, however hoverable it is. */
    @Test
    void anUnboundElementOpensNothing() {
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            SpecSheetBinder binder = new SpecSheetBinder(document, layer);
            binder.bind(instance.requireElement("near-left"), SpecSheetDocuments.sheet("armor"));

            hover(document, instance.requireElement("near-right"));
            binder.update();

            assertFalse(layer.visible());
            assertNull(binder.openTarget());
        }
    }

    /**
     * An unchanged hover is not an event. The supplier is asked once per open,
     * not once per frame — the overlay relays the document out when it is filled,
     * so a per-frame refill would be a per-frame layout pass.
     */
    @Test
    void anUnchangedHoverDoesNotRefillTheSheet() {
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            SpecSheetBinder binder = new SpecSheetBinder(document, layer);
            UiElement anchor = instance.requireElement("near-left");
            AtomicInteger asked = new AtomicInteger();
            binder.bind(anchor, () -> {
                asked.incrementAndGet();
                return SpecSheetDocuments.sheet("armor");
            });

            hover(document, anchor);
            binder.update();
            int passes = document.layoutPasses();
            binder.update();
            binder.update();

            assertEquals(1, asked.get());
            assertEquals(passes, document.layoutPasses(), "a settled hover relaid the document out");
        }
    }

    /** The deepest bound element in the hover chain wins, because hover is a chain. */
    @Test
    void theDeepestBoundElementInTheChainWins() {
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            SpecSheetBinder binder = new SpecSheetBinder(document, layer);
            UiElement anchor = instance.requireElement("near-left");
            binder.bind(instance.requireElement("screen"), SpecSheetDocuments.sheet("system"));
            binder.bind(anchor, SpecSheetDocuments.sheet("armor"));

            hover(document, anchor);
            binder.update();

            assertSame(anchor, binder.openTarget());
        }
    }

    @Test
    void clearingTheBindingsClosesWhateverWasOpen() {
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            SpecSheetBinder binder = new SpecSheetBinder(document, layer);
            UiElement anchor = instance.requireElement("near-left");
            binder.bind(anchor, SpecSheetDocuments.sheet("armor"));
            hover(document, anchor);
            binder.update();

            binder.clear();

            assertEquals(0, binder.size());
            assertFalse(layer.visible());
        }
    }

    private static void hover(UiDocument document, UiElement target) {
        Rect box = target.box().borderBox();
        document.pointerMoved(box.x() + box.width() / 2f, box.y() + box.height() / 2f);
    }
}
