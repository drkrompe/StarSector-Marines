package com.dillon.starsectormarines.battle.ui.panel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadListViewportTest {

    @Test
    void capsPanelAtWholeRowsAndKeepsAllRowsScrollable() {
        SquadListViewport layout = SquadListViewport.fit(40, 400f, 28f, 8f, 34f);

        assertEquals(10, layout.visibleRows);
        assertEquals(376f, layout.panelHeight);
        assertEquals(340f, layout.viewportHeight);
        assertEquals(1360f, layout.contentHeight);
    }

    @Test
    void offsetMapsPointerAndRowsToLaterSquads() {
        SquadListViewport layout = SquadListViewport.fit(40, 400f, 28f, 8f, 34f);
        float bodyBottom = 100f;
        float headerBottom = bodyBottom + layout.viewportHeight;
        float offset = 12f * layout.rowHeight;

        assertEquals(12, layout.rowAt(headerBottom - 1f, bodyBottom, headerBottom, offset));
        assertEquals(21, layout.rowAt(bodyBottom, bodyBottom, headerBottom, offset));
        assertTrue(layout.rowVisible(layout.rowBottom(headerBottom, 12, offset),
                bodyBottom, headerBottom));
        assertFalse(layout.rowVisible(layout.rowBottom(headerBottom, 11, offset),
                bodyBottom, headerBottom));
    }
}
