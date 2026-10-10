package com.dillon.starsectormarines.battle.evacuation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LastLiftSequenceTest {
    @Test void roadReservesUseSeparateTriggersAndBoundedLatestArrivals() {
        assertEquals(180*30,LastLiftSequence.releaseTick(0,-1,-1));
        assertEquals(240*30,LastLiftSequence.releaseTick(1,-1,-1));
        assertEquals(70*30,LastLiftSequence.releaseTick(0,30,-1));
        assertEquals(108*30,LastLiftSequence.releaseTick(0,100*30,-1));
        assertEquals(158*30,LastLiftSequence.releaseTick(1,100*30,150*30));
        assertEquals(240*30,LastLiftSequence.releaseTick(1,100*30,500*30));
    }
}
