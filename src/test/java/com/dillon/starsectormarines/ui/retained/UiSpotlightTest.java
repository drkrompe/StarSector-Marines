package com.dillon.starsectormarines.ui.retained;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class UiSpotlightTest {
    @Test void overlappingAndClippedHolesLeaveExactlyOneShadeLayerOutsideTheirUnion() {
        Rect viewport = new Rect(0, 0, 100, 100);
        var holes = List.of(new Rect(-10, 10, 50, 40), new Rect(20, 30, 60, 50));
        var tiles = UiSpotlight.shadeRectangles(viewport, holes);
        for (int y = 0; y < 100; y++) for (int x = 0; x < 100; x++) {
            float px = x + .5f, py = y + .5f;
            long coverage = tiles.stream().filter(tile -> tile.contains(px, py)).count();
            assertEquals(holes.stream().anyMatch(hole -> hole.contains(px, py)) ? 0 : 1, coverage);
        }
    }

    @Test void regionsSharePointerKeyboardAndCaptureBoundariesAndReleaseImmediately() {
        var clicks = new AtomicInteger();
        var first = button("first", clicks);
        var blocked = button("blocked", clicks);
        var last = button("last", clicks);
        var root = new UiElement("root").layout(UiLayout.ROW).child(first).child(blocked).child(last);
        var allowed = new AtomicReference<List<UiElement>>(List.of(first, last));
        var document = new UiDocument(root).inputRegions(allowed::get);
        document.layout(300, 100);
        assertSame(first, document.elementAt(20, 20));
        assertNull(document.elementAt(120, 20));
        assertSame(last, document.elementAt(220, 20));
        assertTrue(document.pointerDown(120, 20), "Shade consumes clicks instead of leaking to the host");
        assertTrue(document.pointerUp(120, 20));
        assertTrue(document.pointerScrolled(120, 20, 20));
        assertEquals(0, clicks.get());
        document.keyPressed(UiKey.TAB, Set.of(), false);
        assertSame(first, document.focusedElement());
        document.keyPressed(UiKey.TAB, Set.of(), false);
        assertSame(last, document.focusedElement());
        document.keyPressed(UiKey.ENTER, Set.of(), false);
        assertEquals(1, clicks.get());
        assertThrows(IllegalArgumentException.class, () -> document.requestFocus(blocked, true));
        document.pointerDown(220, 20);
        document.setPointerCapture(last);
        document.keyPressed(UiKey.SPACE, Set.of(), false);
        allowed.set(List.of(first));
        document.pointerUp(220, 20);
        document.keyReleased(UiKey.SPACE, Set.of());
        assertEquals(1, clicks.get(), "Changing lessons cancels armed/captured controls outside the new regions");
        assertNull(document.pointerCapture());
        allowed.set(null);
        document.pointerDown(120, 20);
        document.pointerUp(120, 20);
        assertEquals(2, clicks.get(), "Dismissal restores ordinary controls immediately");
    }

    @Test void allowedControlScrollsItsPaneButBlockedAreasDoNotAndEventsCannotBubbleOutside() {
        var clicks = new AtomicInteger();
        var control = button("control", clicks).preferredHeight(40);
        var pane = new UiElement("pane").overflow(Overflow.SCROLL).onClick(() -> clicks.addAndGet(100))
                .child(control).child(new UiElement("spacer").preferredHeight(300));
        var document = new UiDocument(pane).inputRegions(() -> List.of(control));
        document.layout(100, 100);
        document.pointerScrolled(10, 80, 10);
        assertEquals(0, pane.scrollTop());
        document.pointerScrolled(10, 10, 10);
        assertEquals(10, pane.scrollTop());
        document.pointerDown(10, 10); document.pointerUp(10, 10);
        assertEquals(1, clicks.get());
        assertEquals(new Rect(0, 0, 100, 30), UiSpotlight.visibleBounds(document, control));
    }

    @Test void maskTracksMovedRegionsAndDismissalRemovesItsTiles() {
        var visible = new AtomicBoolean(true);
        var control = new UiElement("control").style("position: absolute; left: 10px; top: 10px; width: 80px; height: 40px;");
        var layer = new UiElement("layer").style("position: absolute; width: 100%; height: 100%; pointer-events: none;");
        var dialogue = new UiElement("dialogue").style("position: absolute; left: 200px; top: 100px; width: 80px; height: 60px;");
        var root = new UiElement("root").child(control).child(layer).child(dialogue);
        var document = new UiDocument(root);
        var spotlight = new UiSpotlight(document, layer, dialogue, visible::get,
                () -> List.of(new UiSpotlight.Region(List.of(control))));
        document.layout(300, 200); spotlight.update(); document.advance(0);
        assertFalse(shaded(layer, 20, 20));
        assertTrue(shaded(layer, 120, 70));
        control.style("position: absolute; left: 100px; top: 60px; width: 80px; height: 40px;");
        document.layout(250, 160); spotlight.update(); document.advance(0);
        assertTrue(shaded(layer, 20, 20));
        assertFalse(shaded(layer, 120, 70));
        visible.set(false); spotlight.update(); document.advance(0);
        assertTrue(layer.children().isEmpty());
        assertSame(root, document.elementAt(20, 20));
    }

    @Test void keyboardSkipsClippedControlsAndEscapeDismissesEvenFromTextInput() {
        var visible = new AtomicBoolean(true);
        var search = new UiElement("search").tag(UiTag.INPUT).preferredHeight(30);
        var clipped = button("clipped", new AtomicInteger()).preferredHeight(30);
        var pane = new UiElement("pane").overflow(Overflow.SCROLL).child(search)
                .child(new UiElement("spacer").preferredHeight(200)).child(clipped);
        var document = new UiDocument(pane).inputRegions(() -> visible.get() ? List.of(pane) : null)
                .onCancel(() -> visible.set(false));
        document.layout(100, 100);
        document.keyPressed(UiKey.TAB, Set.of(), false);
        assertSame(search, document.focusedElement());
        document.keyPressed(UiKey.TAB, Set.of(), false);
        assertSame(search, document.focusedElement(), "The clipped button cannot activate outside the visible opening");
        document.keyPressed(UiKey.ESCAPE, Set.of(), false);
        assertFalse(visible.get());
    }

    @Test void overflowingChildrenCannotActivateOutsideTheirDeclaredOpening() {
        var clicks = new AtomicInteger();
        var child = button("overflow", clicks)
                .style("position: absolute; left: 120px; top: 0px; width: 40px; height: 40px;");
        var region = new UiElement("region").style("position: absolute; left: 0px; top: 0px; width: 100px; height: 100px;").child(child);
        var root = new UiElement("root").child(region);
        var document = new UiDocument(root).inputRegions(() -> List.of(region));
        document.layout(300, 200);
        assertNull(document.elementAt(130, 10));
        document.pointerDown(130, 10); document.pointerUp(130, 10);
        assertEquals(0, clicks.get());
        assertThrows(IllegalArgumentException.class, () -> document.requestFocus(child, true));
    }

    private static boolean shaded(UiElement layer, float x, float y) {
        return layer.children().stream().filter(child -> child.id().startsWith("spotlight-shade-"))
                .anyMatch(child -> child.box().borderBox().contains(x, y));
    }

    private static UiElement button(String id, AtomicInteger clicks) {
        return new UiElement(id).tag(UiTag.BUTTON).preferredWidth(100).onClick(clicks::incrementAndGet);
    }
}
