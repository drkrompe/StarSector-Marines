package com.dillon.starsectormarines.ui.retained;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class UiImageTintTest {
    @Test void theCreditSignTakesAColourAndOtherArtKeepsItsOwn() {
        assertNotEquals(Color.WHITE, UiPainter.imageTint("graphics/ui/icons/fleettab/credits_24x24.png"));
        assertEquals(Color.WHITE, UiPainter.imageTint("graphics/icons/cargo/marine.png"));
        assertEquals(Color.WHITE, UiPainter.imageTint("graphics/ui/company/port.svg"));
    }
}
