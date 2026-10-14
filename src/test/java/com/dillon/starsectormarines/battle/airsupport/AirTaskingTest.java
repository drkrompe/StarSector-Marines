package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.airsupport.AirTasking.Ground;
import com.dillon.starsectormarines.battle.airsupport.AirTasking.Target;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Which airframe a target calls for, and what is sent when the preferred kind is out of stock. */
class AirTaskingTest {

    @Test
    void hardenedGroundCallsForABomber() {
        assertEquals(AirPoolKind.BOMBER, AirTasking.preferred(new Target(Ground.WORKS, 1)));
        assertEquals(AirPoolKind.BOMBER, AirTasking.preferred(new Target(Ground.ARMOUR, 2)));
    }

    @Test
    void softGroundCallsForAFighterUnlessMassed() {
        assertEquals(AirPoolKind.FIGHTER, AirTasking.preferred(new Target(Ground.OPEN, AirTasking.MASSED_INFANTRY - 1)));
        assertEquals(AirPoolKind.BOMBER, AirTasking.preferred(new Target(Ground.OPEN, AirTasking.MASSED_INFANTRY)));
    }

    @Test
    void theOtherKindIsSentWhenThePreferredIsOutAndNothingWhenBothAre() {
        Target open = new Target(Ground.OPEN, 4);
        assertEquals(AirPoolKind.FIGHTER, AirTasking.choose(open, 2, 1));
        assertEquals(AirPoolKind.BOMBER, AirTasking.choose(open, 2, 0));
        assertEquals(AirPoolKind.FIGHTER, AirTasking.choose(new Target(Ground.WORKS, 1), 0, 3));
        assertNull(AirTasking.choose(open, 0, 0));
    }
}
