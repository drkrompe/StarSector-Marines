package com.dillon.starsectormarines.ui.retained;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class UiInputScopeTest {
    @Test void scrollDoesNotEscapeTheActiveScopeIntoItsParent() {
        var scope = new UiElement("scope").preferredHeight(30);
        var content = new UiElement("content").preferredHeight(300).child(scope);
        var root = new UiElement("root").overflow(Overflow.SCROLL).child(content);
        var document = new UiDocument(root).inputScope(() -> scope);
        document.layout(200, 100);
        assertTrue(root.box().maxScrollTop() > 0);
        document.pointerScrolled(10, 10, 50);
        assertEquals(0, root.scrollTop());
    }

    @Test void openingAModalCancelsBackgroundInputAndTrapsKeyboardAndPointer() {
        var clicks = new AtomicInteger();
        var background = new UiElement("background").tag(UiTag.BUTTON).preferredWidth(100).onClick(clicks::incrementAndGet);
        var first = new UiElement("first").tag(UiTag.BUTTON).preferredHeight(30).onClick(() -> {});
        var second = new UiElement("second").tag(UiTag.BUTTON).preferredHeight(30).onClick(() -> {});
        var modal = new UiElement("modal").preferredWidth(100).layout(UiLayout.COLUMN).child(first).child(second);
        var root = new UiElement("root").layout(UiLayout.ROW).child(background).child(modal);
        var active = new AtomicReference<>(background);
        var document = new UiDocument(root).inputScope(active::get);
        document.layout(200, 100);
        document.requestFocus(background, true);
        document.keyPressed(UiKey.SPACE, Set.of(), false);
        document.setPointerCapture(background);
        active.set(modal);
        document.keyReleased(UiKey.SPACE, Set.of());
        assertEquals(0, clicks.get());
        assertNull(document.pointerCapture());
        assertNull(document.focusedElement());
        assertNull(document.elementAt(20, 20));
        assertSame(first, document.elementAt(120, 20));
        document.pointerMoved(120, 20);
        assertFalse(document.hoverChain().contains(root));
        document.keyPressed(UiKey.TAB, Set.of(), false);
        assertSame(first, document.focusedElement());
        document.keyPressed(UiKey.TAB, Set.of(KeyModifier.SHIFT), false);
        assertSame(second, document.focusedElement());
        document.keyPressed(UiKey.TAB, Set.of(), false);
        assertSame(first, document.focusedElement());
        active.set(background);
        document.keyPressed(UiKey.TAB, Set.of(), false);
        assertSame(background, document.focusedElement());
        document.keyPressed(UiKey.ENTER, Set.of(), false);
        assertEquals(1, clicks.get());
    }
}
