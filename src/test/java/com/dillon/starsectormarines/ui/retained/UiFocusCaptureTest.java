package com.dillon.starsectormarines.ui.retained;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiFocusCaptureTest {

    @Test
    void tabFollowsDocumentOrderWrapsAndShiftReverses() {
        UiElement first = button("first", () -> { });
        UiElement second = button("second", () -> { });
        UiElement third = button("third", () -> { });
        UiDocument document = document(new UiElement("root")
                .child(first).child(second).child(third));

        assertTrue(document.keyPressed(UiKey.TAB, Set.of(), false));
        assertSame(first, document.focusedElement());
        assertTrue(first.focusVisible());
        document.keyPressed(UiKey.TAB, Set.of(), false);
        assertSame(second, document.focusedElement());
        document.keyPressed(UiKey.TAB, Set.of(KeyModifier.SHIFT), false);
        assertSame(first, document.focusedElement());
        document.keyPressed(UiKey.TAB, Set.of(KeyModifier.SHIFT), false);
        assertSame(third, document.focusedElement());
    }

    @Test
    void tabIndexMinusOneAcceptsProgrammaticFocusButIsSkippedByTab() {
        UiElement skipped = new UiElement("skipped").tabIndex(-1);
        UiElement button = button("button", () -> { });
        UiDocument document = document(new UiElement("root").child(skipped).child(button));

        document.requestFocus(skipped, false);
        assertSame(skipped, document.focusedElement());
        document.keyPressed(UiKey.TAB, Set.of(), false);
        assertSame(button, document.focusedElement());
        assertThrows(IllegalArgumentException.class,
                () -> new UiElement("positive").tabIndex(1));
    }

    @Test
    void enterActivatesOnFirstDownAndSpaceActivatesOnMatchingRelease() {
        AtomicInteger clicks = new AtomicInteger();
        UiElement button = button("button", clicks::incrementAndGet);
        UiDocument document = document(button);
        document.requestFocus(button, true);

        assertTrue(document.keyPressed(UiKey.ENTER, Set.of(), false));
        assertTrue(document.keyPressed(UiKey.ENTER, Set.of(), true));
        assertEquals(1, clicks.get());

        assertTrue(document.keyPressed(UiKey.SPACE, Set.of(), false));
        assertTrue(button.armed());
        assertEquals(1, clicks.get());
        assertTrue(document.keyReleased(UiKey.SPACE, Set.of()));
        assertEquals(2, clicks.get());
        assertFalse(button.armed());
    }

    @Test
    void movingFocusCancelsAnArmedSpaceAction() {
        AtomicInteger clicks = new AtomicInteger();
        UiElement first = button("first", clicks::incrementAndGet);
        UiElement second = button("second", () -> { });
        UiDocument document = document(new UiElement("root").child(first).child(second));
        document.requestFocus(first, true);

        document.keyPressed(UiKey.SPACE, Set.of(), false);
        document.keyPressed(UiKey.TAB, Set.of(), false);

        assertFalse(first.armed());
        assertFalse(document.keyReleased(UiKey.SPACE, Set.of()));
        assertEquals(0, clicks.get());
    }

    @Test
    void escapeInvokesOnlyTheDocumentsUnmodifiedCancelAction() {
        AtomicInteger cancels = new AtomicInteger();
        UiDocument document = document(new UiElement("root"))
                .onCancel(cancels::incrementAndGet);

        assertFalse(document.keyPressed(UiKey.ESCAPE, Set.of(KeyModifier.SHIFT), false));
        assertTrue(document.keyPressed(UiKey.ESCAPE, Set.of(), false));
        assertTrue(document.keyPressed(UiKey.ESCAPE, Set.of(), true));
        assertEquals(1, cancels.get());
    }

    @Test
    void pointerPressFocusesNearestFocusableAncestorWithoutKeyboardRing() {
        UiElement label = new UiElement("label");
        UiElement button = button("button", () -> { }).child(label);
        UiDocument document = document(button);

        document.pointerDown(50f, 50f);

        assertSame(button, document.focusedElement());
        assertFalse(button.focusVisible());
    }

    @Test
    void explicitCaptureDeliversMoveAndReleaseOutsideThenEnds() {
        AtomicInteger moves = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        UiElement drag = new UiElement("drag")
                .preferredSize(50f, 50f)
                .align(UiAlign.START, UiAlign.START)
                .onPointerDown(UiPointerEvent::capturePointer)
                .onPointerMove(event -> moves.incrementAndGet())
                .onPointerUp(event -> releases.incrementAndGet());
        UiElement root = new UiElement("root").layout(UiLayout.STACK).child(drag);
        UiDocument document = document(root);

        assertTrue(document.pointerDown(25f, 25f));
        assertSame(drag, document.pointerCapture());
        document.pointerMoved(90f, 90f);
        assertTrue(drag.hovered());
        assertTrue(document.pointerUp(90f, 90f));

        assertEquals(1, moves.get());
        assertEquals(1, releases.get());
        assertNull(document.pointerCapture());
        assertFalse(drag.hovered());
    }

    @Test
    void capturedButtonReleaseOutsideCompletesItsClick() {
        AtomicInteger clicks = new AtomicInteger();
        UiElement button = button("button", clicks::incrementAndGet)
                .preferredSize(50f, 50f)
                .align(UiAlign.START, UiAlign.START)
                .onPointerDown(UiPointerEvent::capturePointer);
        UiDocument document = document(new UiElement("root")
                .layout(UiLayout.STACK).child(button));

        document.pointerDown(25f, 25f);
        document.pointerUp(90f, 90f);

        assertEquals(1, clicks.get());
        assertNull(document.pointerCapture());
    }

    @Test
    void deactivationClearsGesturesAndFocusButPreservesScrollState() {
        UiElement button = button("button", () -> { })
                .onPointerDown(UiPointerEvent::capturePointer);
        UiDocument document = document(button);
        button.scrollTop(37f);
        document.pointerDown(50f, 50f);

        document.deactivateInput();

        assertNull(document.focusedElement());
        assertNull(document.pointerCapture());
        assertFalse(button.armed());
        assertFalse(button.hovered());
        assertEquals(37f, button.scrollTop());
    }

    @Test
    void detachingAnActiveElementClearsFocusCaptureAndArmedStateOnNextInput() {
        UiElement button = button("button", () -> { })
                .onPointerDown(UiPointerEvent::capturePointer);
        UiElement root = new UiElement("root").child(button);
        UiDocument document = document(root);
        document.pointerDown(50f, 20f);
        assertSame(button, document.pointerCapture());
        assertTrue(button.armed());

        root.remove(button);
        document.pointerMoved(70f, 70f);

        assertNull(document.focusedElement());
        assertNull(document.pointerCapture());
        assertFalse(button.armed());
        assertFalse(button.hovered());
    }

    private static UiElement button(String id, Runnable click) {
        return new UiElement(id)
                .tag(UiTag.BUTTON)
                .preferredHeight(40f)
                .onClick(click);
    }

    private static UiDocument document(UiElement root) {
        UiDocument document = new UiDocument(root);
        document.layout(100f, 100f);
        return document;
    }
}
