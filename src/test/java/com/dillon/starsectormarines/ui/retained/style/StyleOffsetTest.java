package com.dillon.starsectormarines.ui.retained.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StyleOffsetTest {

    @Test
    void anOffsetMayBeNegative() {
        assertDoesNotThrow(() -> StyleValues.parse(StyleProperty.LEFT, "-6.0px"));
        assertDoesNotThrow(() -> StyleValues.parse(StyleProperty.TOP, "-6.0px"));
    }

    @Test
    void aSizeMayNot() {
        assertThrows(UiStyleException.class, () -> StyleValues.parse(StyleProperty.WIDTH, "-6.0px"));
    }
}
