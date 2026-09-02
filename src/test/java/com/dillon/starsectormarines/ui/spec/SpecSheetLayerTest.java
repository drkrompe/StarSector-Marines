package com.dillon.starsectormarines.ui.spec;

import com.dillon.starsectormarines.ui.retained.Rect;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the one overlay lands, how tall it is, and what it refuses to do to the
 * hover that opened it.
 */
class SpecSheetLayerTest {

    private static final float EPSILON = 0.01f;

    /** The ordinary case: beside the subject, on the side the reader is heading. */
    @Test
    void opensToTheRightOfAnAnchorWithRoomForIt() {
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            UiElement anchor = instance.requireElement("near-left");

            layer.show(SpecSheetDocuments.sheet("armor"), anchor);

            Rect overlay = layer.element().box().borderBox();
            assertEquals(anchor.box().borderBox().right() + 10f, overlay.x(), EPSILON);
            assertEquals(anchor.box().borderBox().y(), overlay.y(), EPSILON);
            assertTrue(overlay.right() <= SpecSheetDocuments.WIDTH,
                    "the overlay left the document at " + overlay.right());
        }
    }

    /** No room on the right is a flip, not a sheet hanging off the edge. */
    @Test
    void flipsToTheLeftWhenTheRightEdgeIsTooClose() {
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            UiElement anchor = instance.requireElement("near-right");

            layer.show(SpecSheetDocuments.sheet("weapon"), anchor);

            Rect overlay = layer.element().box().borderBox();
            assertEquals(anchor.box().borderBox().x() - 10f, overlay.right(), EPSILON);
            assertTrue(overlay.x() >= 0f, "the overlay left the document at " + overlay.x());
        }
    }

    /** Neither side fits, so it goes beneath — and still inside the panel. */
    @Test
    void dropsBeneathAnAnchorTooWideForEitherSide() {
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            UiElement anchor = instance.requireElement("wide");

            layer.show(SpecSheetDocuments.sheet("special"), anchor);

            Rect overlay = layer.element().box().borderBox();
            assertEquals(anchor.box().borderBox().bottom() + 10f, overlay.y(), EPSILON);
            assertEquals(anchor.box().borderBox().x(), overlay.x(), EPSILON);
        }
    }

    /** A subject near the floor pulls the overlay up rather than off the bottom. */
    @Test
    void clampsToTheBottomOfTheDocument() {
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            UiElement anchor = instance.requireElement("near-bottom");

            layer.show(SpecSheetDocuments.sheet("system", "A field note that has to wrap."),
                    anchor);

            Rect overlay = layer.element().box().borderBox();
            assertTrue(overlay.bottom() <= SpecSheetDocuments.HEIGHT + EPSILON,
                    "the overlay ran off the bottom at " + overlay.bottom());
            assertTrue(overlay.y() < anchor.box().borderBox().y(),
                    "a clamped overlay should sit above its own anchor's top");
        }
    }

    /** Show and hide are class toggles, so a stylesheet transition can own them. */
    @Test
    void showAndHideToggleTheHiddenClass() {
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            SpecSheetLayer layer = SpecSheetLayer.install(document);

            assertTrue(layer.element().hasClass(SpecSheetLayer.HIDDEN_CLASS));
            assertFalse(layer.visible());

            layer.show(SpecSheetDocuments.sheet("mech"), instance.requireElement("near-left"));
            assertFalse(layer.element().hasClass(SpecSheetLayer.HIDDEN_CLASS));
            assertTrue(layer.visible());

            layer.hide();
            assertTrue(layer.element().hasClass(SpecSheetLayer.HIDDEN_CLASS));
            document.layout(SpecSheetDocuments.WIDTH, SpecSheetDocuments.HEIGHT);
            assertEquals(0f, layer.element().box().borderBox().width(), EPSILON);
        }
    }

    /**
     * The overlay cannot take the hover that opened it. Without this the sheet
     * would close the moment it covered its own subject, and reopen, forever.
     */
    @Test
    void theOpenOverlayIsNotHitTestable() {
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            UiElement anchor = instance.requireElement("near-left");
            UiElement beneath = instance.requireElement("beneath");

            layer.show(SpecSheetDocuments.sheet("armor"), anchor);

            Rect overlay = layer.element().box().borderBox();
            Rect covered = beneath.box().borderBox();
            assertTrue(overlay.contains(covered.x() + 1f, covered.y() + 1f),
                    "the fixture no longer covers the element beneath the overlay");
            assertSame(beneath, document.elementAt(covered.x() + 1f, covered.y() + 1f),
                    "the overlay took a hit that belongs to the element under it");
            assertSame(instance.requireElement("screen"),
                    document.elementAt(overlay.right() - 2f, overlay.bottom() - 2f));
        }
    }

    /** One document, one overlay, however many callers ask for it. */
    @Test
    void installIsIdempotent() {
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            int before = document.root().childCount();

            SpecSheetLayer first = SpecSheetLayer.install(document);
            SpecSheetLayer second = SpecSheetLayer.install(document);

            assertSame(first, second);
            assertEquals(before + 1, document.root().childCount());
        }
    }

    /**
     * Height is measured, not counted. The overlay grows by exactly one line
     * height per wrapped line of its note, taken from the same measurer layout
     * used — which a character-count estimate cannot promise.
     */
    @Test
    void heightFollowsTheMeasuredWrapRatherThanACharacterCount() {
        String wrapping = "one two three four five six seven eight nine ten eleven twelve";
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            UiElement anchor = instance.requireElement("near-left");

            layer.show(SpecSheetDocuments.sheet("armor", "short"), anchor);
            float oneLine = layer.element().box().borderBox().height();

            layer.show(SpecSheetDocuments.sheet("armor", wrapping), anchor);
            float wrapped = layer.element().box().borderBox().height();

            UiElement note = note(layer);
            int lines = SpecSheetDocuments.FONT.wrapLines(
                    wrapping, layer.element().box().contentBox().width()).size();
            assertTrue(lines > 1, "the fixture note no longer wraps");
            assertEquals(lines * 20f, note.box().borderBox().height(), EPSILON,
                    "the note box is its measured line count");
            assertEquals(oneLine + (lines - 1) * 20f, wrapped, EPSILON,
                    "the overlay grew by exactly the lines the measurer found");
        }
    }

    /** Rows are pooled, so a smaller sheet leaves nothing of the larger behind. */
    @Test
    void refillingDropsTheRowsTheNewSheetDoesNotNeed() {
        try (MarkupInstance instance = SpecSheetDocuments.instance()) {
            UiDocument document = SpecSheetDocuments.laidOut(instance);
            SpecSheetLayer layer = SpecSheetLayer.install(document);
            UiElement anchor = instance.requireElement("near-left");

            layer.show(SpecSheetDocuments.sheet("armor", "first", "second"), anchor);
            int withTwoNotes = layer.element().childCount();

            layer.show(new SpecSheet("Bare", "", null, "item", List.of(), List.of()), anchor);

            assertEquals(withTwoNotes - 4, layer.element().childCount(),
                    "two stat rows and two notes should have gone");
            assertEquals(1, layer.element().childCount(), "only the heading remains");
        }
    }

    private static UiElement note(SpecSheetLayer layer) {
        for (UiElement child : layer.element().children()) {
            if (child.hasClass("spec-sheet-note")) return child;
        }
        throw new AssertionError("the overlay has no note element");
    }
}
