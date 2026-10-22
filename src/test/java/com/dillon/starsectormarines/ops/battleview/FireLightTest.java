package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.fire.FireFuel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireLightTest {

    private static final int W = 40, H = 12;

    /** Two separate patches of burning grass, three cells square, in different gathering squares. */
    private static FireField twoPatches() {
        FireField field = new FireField(W, H, 5L, idx -> {
            int x = idx % W, y = idx / W;
            return y >= 1 && y <= 3 && (x >= 1 && x <= 3 || x >= 31 && x <= 33) ? FireFuel.GRASS.bit : 0;
        });
        for (int y = 1; y <= 3; y++) for (int x : new int[]{1, 2, 3, 31, 32, 33}) field.addHeat(x, y, 5000);
        for (int t = 0; t < FireField.STEP_TICKS * 2; t++) field.tick(t);
        return field;
    }

    @Test
    void eachBurningSquareIsOneLightAtItsFire() {
        FireLight light = new FireLight();
        light.update(twoPatches());

        assertEquals(2, light.count(), "one light per gathering square with fire in it");
        for (int i = 0; i < light.count(); i++) {
            assertTrue(light.y(i) > 1f && light.y(i) < 4f, "light sits on its patch: y " + light.y(i));
            assertTrue(light.pool(i, light.x(i), light.y(i), 0f) > 0f);
            assertEquals(0f, light.pool(i, light.x(i) + light.radius(i), light.y(i), 0f), "nothing past its radius");
        }
    }

    @Test
    void nothingBurningIsNoLight() {
        FireLight light = new FireLight();
        light.update(new FireField(W, H, 5L, idx -> FireFuel.GRASS.bit));
        assertEquals(0, light.count());
        light.update(null);
        assertEquals(0, light.count());
    }

    @Test
    void aHeldClockIsAStillPicture() {
        FireLight light = new FireLight();
        light.update(twoPatches());
        assertEquals(light.strength(0, 12.5f), light.strength(0, 12.5f));
        assertEquals(12.5f, FireLight.clock(12.5f));
    }
}
