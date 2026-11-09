package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.VaultFrameBrace.Stance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VaultFrameBraceTest {

    private static VaultFrameBrace braced() {
        VaultFrameBrace brace = new VaultFrameBrace();
        for (int i = 0; i < 25; i++) brace.tick(0.1f, false, true, 10f);
        return brace;
    }

    @Test
    void itBracesOnlyStoodStillAndEngagedForTheBraceTime() {
        VaultFrameBrace brace = new VaultFrameBrace();
        brace.tick(1f, false, true, 0f);
        assertEquals(Stance.BRACING, brace.stance());
        brace.tick(1.1f, false, true, 0f);
        assertEquals(Stance.BRACED, brace.stance());
        brace.tick(0.1f, true, true, 0f);
        assertEquals(Stance.FREE, brace.stance(), "walking pulls its legs off the walls");
        brace.tick(5f, false, false, 0f);
        assertEquals(Stance.FREE, brace.stance(), "nothing to fight, nothing to brace for");
    }

    @Test
    void bracedItsFrontIsHardAndItsRearSoftAndAHitBehindUnbracesIt() {
        VaultFrameBrace brace = braced();
        assertEquals(1f, brace.fireScale());
        assertEquals(VaultFrameBrace.FRONT_SCALE, brace.hit(-40f));
        assertEquals(1f, brace.hit(90f), "the flank is ordinary plate");
        assertEquals(Stance.BRACED, brace.stance());
        assertEquals(VaultFrameBrace.REAR_SCALE, brace.hit(170f));
        assertEquals(Stance.FREE, brace.stance());
        assertEquals(1f, brace.hit(170f), "free, it turns on ordinary plate");
        assertEquals(VaultFrameBrace.FREE_FIRE_SCALE, brace.fireScale());
    }

    @Test
    void aTargetLeavingItsFrontArcMakesItTurn() {
        VaultFrameBrace brace = braced();
        brace.tick(0.1f, false, true, 100f);
        assertEquals(Stance.FREE, brace.stance());
        assertEquals(-170f, VaultFrameBrace.wrap(190f));
    }
}
