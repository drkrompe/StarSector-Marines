package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.RuinsPullOut.Resolution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuinsPullOutTest {

    @Test
    void aRetreatWithThePullOutPointHeldIsAPullOut() {
        assertEquals(Resolution.PULLED_OUT, RuinsPullOut.resolve(false, true, true));
    }

    @Test
    void aRetreatWithThePullOutPointLostIsAnOrdinaryDefeat() {
        assertEquals(Resolution.LOST, RuinsPullOut.resolve(false, true, false));
    }

    @Test
    void aBattleLostWithoutRetreatingIsLostWhereverThePullOutPointStands() {
        assertEquals(Resolution.LOST, RuinsPullOut.resolve(false, false, true));
        assertEquals(Resolution.LOST, RuinsPullOut.resolve(false, false, false));
    }

    @Test
    void aBoardedHaulIsWon() {
        assertEquals(Resolution.WON, RuinsPullOut.resolve(true, false, true));
        assertEquals(Resolution.WON, RuinsPullOut.resolve(true, false, false));
    }
}
