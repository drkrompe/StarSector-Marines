package com.dillon.starsectormarines.battle.ui.highlight;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CellHighlightTest {

    @Test
    void existingConstructorRemainsOneCellAndCoarseConstructorRetainsFootprint() {
        CellHighlight existing = new CellHighlight(2, 3, Color.WHITE);
        CellHighlight coarse = new CellHighlight(8, 16, 8, 5, Color.RED);

        assertEquals(1, existing.width);
        assertEquals(1, existing.height);
        assertEquals(8, coarse.width);
        assertEquals(5, coarse.height);
    }
}
