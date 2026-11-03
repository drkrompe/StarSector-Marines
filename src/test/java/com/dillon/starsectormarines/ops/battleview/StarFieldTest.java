package com.dillon.starsectormarines.ops.battleview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarFieldTest {

    private static final float WIDTH = 100f;

    @Test
    void aStarStaysInTheFieldHoweverFarTheShipTravelsEitherWay() {
        StarField field = new StarField(1L, 50, 3);
        for (int star = 0; star < field.count(); star++) {
            for (float travel : new float[] {0f, 37f, 1e5f, -37f, -1e5f}) {
                float x = field.x(star, travel, WIDTH);
                assertTrue(x >= 0f && x < WIDTH, "star " + star + " at " + x);
            }
        }
    }

    @Test
    void aNearerStarTravelsFurtherForTheSameMovement() {
        StarField field = new StarField(1L, 200, 3);
        float smallest = Float.MAX_VALUE, largest = 0f;
        for (int star = 0; star < field.count(); star++) {
            float moved = wrapped(field.x(star, 10f, WIDTH) - field.x(star, 0f, WIDTH));
            smallest = Math.min(smallest, moved);
            largest = Math.max(largest, moved);
            // A star's size says how near it is, so the two have to agree.
            if (moved > 9f) assertTrue(field.size(star) >= 3f, "a near star is drawn large");
        }
        assertEquals(2.5f, smallest, 0.01f);
        assertEquals(10f, largest, 0.01f);
    }

    private static float wrapped(float delta) {
        return delta < 0f ? delta + WIDTH : delta;
    }
}
