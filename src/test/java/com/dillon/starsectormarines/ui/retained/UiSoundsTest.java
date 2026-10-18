package com.dillon.starsectormarines.ui.retained;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiSoundsTest {

    private final List<String> played = new ArrayList<>();
    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private final AtomicInteger clicks = new AtomicInteger();

    @AfterEach
    void restore() {
        UiSounds.install(new UiSounds(UiSounds.SILENT, System::nanoTime));
    }

    /** Two side-by-side 100x40 buttons, the first live and the second disabled. */
    private UiDocument page(UiElement first, UiElement second) {
        UiSounds.install(new UiSounds(played::add, now::get));
        UiElement root = new UiElement("root").layout(UiLayout.ROW).child(first).child(second);
        UiDocument document = new UiDocument(root);
        document.layout(400f, 40f);
        return document;
    }

    private UiElement button(String id) {
        return new UiElement(id).tag(UiTag.BUTTON).preferredWidth(100f).preferredHeight(40f)
                .onClick(clicks::incrementAndGet);
    }

    @Test
    void activatingAControlPlaysThePressAndRunsIt() {
        UiDocument document = page(button("a"), button("b").disabled(true));
        document.pointerDown(50f, 20f);
        document.pointerUp(50f, 20f);
        assertEquals(List.of(UiSounds.HOVER, UiSounds.PRESSED), played);
        assertEquals(1, clicks.get());
    }

    @Test
    void pressingADisabledButtonSaysItDidNothing() {
        UiDocument document = page(button("a"), button("b").disabled(true));
        document.pointerDown(150f, 20f);
        document.pointerUp(150f, 20f);
        assertEquals(List.of(UiSounds.DISABLED_PRESSED), played);
        assertEquals(0, clicks.get());
    }

    @Test
    void hoverSoundsOncePerControlAndNotFasterThanTheGap() {
        UiDocument document = page(button("a"), button("b"));
        document.pointerMoved(10f, 20f);
        document.pointerMoved(60f, 20f);   // same control: silent
        document.pointerMoved(150f, 20f);  // next control, too soon: dropped
        now.addAndGet(UiSounds.HOVER_GAP_NANOS);
        document.pointerMoved(50f, 20f);   // back to the first, after the gap
        assertEquals(List.of(UiSounds.HOVER, UiSounds.HOVER), played);
    }

    @Test
    void aControlsOwnSoundReplacesThePressAndTheKeyboardPlaysItToo() {
        UiDocument document = page(button("a").sound("ui_intel_something_posted"), button("b"));
        document.requestFocus(document.root().children().get(0), true);
        document.keyPressed(UiKey.ENTER, Set.of(), false);
        assertEquals(List.of("ui_intel_something_posted"), played);
        assertEquals(1, clicks.get());
    }
}
