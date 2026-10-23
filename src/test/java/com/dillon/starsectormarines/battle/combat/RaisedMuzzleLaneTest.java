package com.dillon.starsectormarines.battle.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A round from a raised muzzle passes over the bodies near the gun and meets those near its mark. */
class RaisedMuzzleLaneTest {

    /** A one-tier plinth: four metres, four cells. A marine stands about 0.9 either side of the target plane. */
    private static final float PLINTH = 4f;
    private static final float MARINE = 0.9f;

    @Test void aFriendNearTheGunIsUnderTheRoundAndOneNearTheMarkIsNot() {
        assertTrue(BallisticResolver.overhead(0f, 0f, PLINTH, 20f, 0f, 5f, 0f, MARINE), "a quarter of the way out");
        assertFalse(BallisticResolver.overhead(0f, 0f, PLINTH, 20f, 0f, 18f, 0f, MARINE), "close to the mark");
        assertTrue(BallisticResolver.overhead(0f, 0f, PLINTH, 40f, 0f, 18f, 0f, MARINE),
                "the same man under a longer shot");
    }

    @Test void aGunAtGroundLevelOverfliesNobody() {
        assertFalse(BallisticResolver.overhead(0f, 0f, 0f, 20f, 0f, 3f, 0f, MARINE));
    }
}
