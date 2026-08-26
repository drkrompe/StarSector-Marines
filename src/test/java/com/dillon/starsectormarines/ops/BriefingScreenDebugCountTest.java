package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BriefingScreenDebugCountTest {

    @Test
    void countButtonsCrossFormerLimitsAndSaturateOnlyAtIntegerBounds() {
        assertEquals(50, BriefingScreen.adjustDebugCount(40, 10));
        assertEquals(110, BriefingScreen.adjustDebugCount(100, 10));
        assertEquals(0, BriefingScreen.adjustDebugCount(0, -10));
        assertEquals(Integer.MAX_VALUE,
                BriefingScreen.adjustDebugCount(Integer.MAX_VALUE, 10));
    }
}
