package com.dillon.starsectormarines.battle.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The story commanders' holder share ({@link StoryGarrisons#holderCap}). */
class StoryGarrisonsTest {

    @Test
    void aThirdOfTheStorysOwnInfantryHoldsAtOnceAndAtLeastOneFromTwoSquadsUp() {
        assertEquals(0, StoryGarrisons.holderCap(0));
        assertEquals(0, StoryGarrisons.holderCap(1), "a lone squad keeps its post");
        assertEquals(1, StoryGarrisons.holderCap(2));
        assertEquals(1, StoryGarrisons.holderCap(5));
        assertEquals(2, StoryGarrisons.holderCap(6));
        assertEquals(3, StoryGarrisons.holderCap(9));
    }
}
