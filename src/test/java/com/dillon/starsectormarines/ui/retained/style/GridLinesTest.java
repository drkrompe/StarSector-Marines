package com.dillon.starsectormarines.ui.retained.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GridLinesTest {

    @Test
    void parsesASpacingAndAColorOrNone() {
        GridLines grid = (GridLines) StyleValues.parse(StyleProperty.GRID_LINES, "32px #46c8ff0f");
        assertEquals(32f, grid.spacing());
        assertEquals(0x46, grid.color().getRed());
        assertEquals(0x0f, grid.color().getAlpha());
        assertNull(StyleValues.parse(StyleProperty.GRID_LINES, "none"));
    }

    @Test
    void refusesASpacingBelowOnePixelOrAMissingColor() {
        assertThrows(UiStyleException.class, () -> StyleValues.parse(StyleProperty.GRID_LINES, "0px #46c8ff0f"));
        assertThrows(UiStyleException.class, () -> StyleValues.parse(StyleProperty.GRID_LINES, "32px"));
    }
}
