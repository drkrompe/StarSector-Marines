package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.fire.FireFuel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaceFireTest {

    @Test
    void aFaceTakesFireFromItsWallOrTheGroundOutsideAndPoursItFromTheRoomBehind() {
        assertEquals(0f, FaceFire.lick(0f, 0f), 0f);
        assertEquals(.8f, FaceFire.lick(.8f, 0f), 0f, "its own wall burning");
        assertEquals(FaceFire.OUTSIDE_SHARE, FaceFire.lick(0f, 1f), 1e-6f, "the ground outside, a little less");
        assertEquals(.7f, FaceFire.pour(.7f, 0f), 0f, "the room behind");
        assertEquals(0f, FaceFire.pour(0f, 0f), 0f);
    }

    @Test
    void aLickReachesHigherAsItBurnsHarderAndNeverFarOverTheEave() {
        float low = FaceFire.reach(.1f, 12f), high = FaceFire.reach(.9f, 12f);
        assertTrue(low > 0f && high > low, low + " < " + high);
        assertEquals(FaceFire.LICK_MOST, FaceFire.reach(1f, 3f), 0f, "a low wall is overtopped, so far");
        assertEquals(0f, FaceFire.reach(0f, 12f), 0f);
    }

    @Test
    void aFaceCharsAsItsWallOrRoomBurnsAndIsBlackOnceTheyHaveBurntOut() {
        int[] fuel = {FireFuel.STRUCTURE.bit, FireFuel.FURNISHING.bit};
        FireField field = new FireField(2, 1, 1L, idx -> fuel[idx]);
        assertEquals(0f, FaceFire.charred(field, 0, 1), 0f);
        assertEquals(0f, FaceFire.charred(field, -1, -1), 0f, "no cells");
        field.addHeat(0, 0, 5000);
        field.addHeat(1, 0, 5000);
        int tick = 0;
        while (tick < 3000 && !(field.isBurning(0, 0) && field.isBurning(1, 0))) field.tick(tick++);
        assertTrue(FaceFire.heat(field, 0) > 0f, "burning");
        float first = FaceFire.charred(field, 0, 1);
        while (tick < 30_000 && !(field.isBurnt(0, 0) && field.isBurnt(1, 0))) field.tick(tick++);
        assertTrue(field.isBurnt(0, 0) && field.isBurnt(1, 0), "burnt out by tick " + tick);
        assertEquals(0f, FaceFire.heat(field, 0), 0f, "nothing left to burn");
        assertEquals(1f, FaceFire.charred(field, 0, -1), 0f, "its own wall burnt");
        assertEquals(FaceFire.ROOM_CHAR, FaceFire.charred(field, -1, 1), 0f, "its room burnt");
        assertTrue(first < 1f, "charring as it burns: " + first);
    }

    @Test
    void aFlameRunsItsLoopFadingFromFrameToFrame() {
        int seed = 17;
        int a = FaceFire.frame(0f, seed), b = FaceFire.frame(1f / FaceFire.FPS, seed);
        assertEquals((a + 1) % FaceFire.LOOP, b);
        float blend = FaceFire.blend(.5f / FaceFire.FPS, seed);
        assertTrue(blend >= 0f && blend < 1f);
        assertEquals(a, FaceFire.frame(FaceFire.LOOP / FaceFire.FPS, seed), "the loop comes round");
    }
}
