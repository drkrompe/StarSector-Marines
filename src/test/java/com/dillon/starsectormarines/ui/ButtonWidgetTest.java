package com.dillon.starsectormarines.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtonWidgetTest {

    @Test
    void nullActionIsDisabledAndDoesNotConsumeInput() {
        ButtonWidget button = new ButtonWidget(10f, 10f, 30f, 20f, null);

        assertFalse(button.onMouseDown(15, 15));
        assertFalse(button.onMouseUp(15, 15));
    }

    @Test
    void enabledButtonStillUsesArmedClickSemantics() {
        AtomicInteger clicks = new AtomicInteger();
        ButtonWidget button = new ButtonWidget(10f, 10f, 30f, 20f, clicks::incrementAndGet);

        assertTrue(button.onMouseDown(15, 15));
        assertTrue(button.onMouseUp(15, 15));
        assertEquals(1, clicks.get());
    }
}
