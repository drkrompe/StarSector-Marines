package com.dillon.starsectormarines.battle.ui.panel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugTogglesPanelTest {

    @Test
    void dialMapsTrackPositionToRangeAndClampsOutside() {
        assertEquals(0.0, DebugTogglesPanel.dialValueAt(90f, 100f, 200f, 0.0, 0.02), 1e-9);
        assertEquals(0.01, DebugTogglesPanel.dialValueAt(150f, 100f, 200f, 0.0, 0.02), 1e-9);
        assertEquals(0.02, DebugTogglesPanel.dialValueAt(220f, 100f, 200f, 0.0, 0.02), 1e-9);
    }
}
