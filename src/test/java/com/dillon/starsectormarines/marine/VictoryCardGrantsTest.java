package com.dillon.starsectormarines.marine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Winning teaches nothing: a card unlocks only at its Kit Ladder station. */
class VictoryCardGrantsTest {

    @Test void winningTeachesNoCard() {
        MarineArmory armory = new MarineArmory();
        var before = armory.knownLoadoutCardIds();
        for (int win = 0; win < 60; win++) armory.recordVictory(win % 3 == 0);
        assertEquals(before, armory.knownLoadoutCardIds());
    }
}
