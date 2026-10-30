package com.dillon.starsectormarines.battle.ui.panel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/** A drawn line ends at the cursor, pulled back along the line to the power's length when it reaches further. */
class CommandPowerTargetingPanelLineTest {

    @Test
    void aLineWithinTheLengthEndsAtTheCursor() {
        assertArrayEquals(new int[]{40, 25}, CommandPowerTargetingPanel.lineEnd(10, 25, 40, 25, 60f));
    }

    @Test
    void aLongerLineIsCutBackAlongItself() {
        assertArrayEquals(new int[]{70, 25}, CommandPowerTargetingPanel.lineEnd(10, 25, 130, 25, 60f));
        assertArrayEquals(new int[]{10, 85}, CommandPowerTargetingPanel.lineEnd(10, 25, 10, 200, 60f));
    }
}
