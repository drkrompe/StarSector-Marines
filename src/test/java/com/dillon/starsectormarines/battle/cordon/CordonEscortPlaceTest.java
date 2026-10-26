package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A road hull's escort keeps beside it, never on the way it is escorting. */
class CordonEscortPlaceTest {

    private static final float EAST = -90f;
    /** A truck at (10.5, 10.5) driving east along y 10.5. */
    private static final float[] WAY_X = {10.5f, 30.5f}, WAY_Y = {10.5f, 10.5f};

    private static GroundBody truck() {
        GroundBody body = VehicleType.SUPPLY_TRUCK.createBody();
        body.teleport(10.5f, 10.5f, EAST);
        return body;
    }

    @Test
    void onOpenGroundTheEscortKeepsToTheRight() {
        // Driving east, its right is south: four cells south and two ahead.
        assertArrayEquals(new int[]{13, 7, 1}, CordonArmour.escortPlace(truck(), WAY_X, WAY_Y, place -> place, 0));
    }

    @Test
    void whereTheRightIsOnlyTheLaneAheadTheEscortKeepsToTheLeft() {
        // A building on the right: the nearest ground to the place there is the lane itself.
        int[] place = CordonArmour.escortPlace(truck(), WAY_X, WAY_Y,
                p -> p[1] < 10 ? new int[]{p[0], 10} : p, 1);
        assertArrayEquals(new int[]{13, 15, -1}, place);
    }

    @Test
    void inALaneTooNarrowForTwoAbreastTheEscortKeepsBehind() {
        int[] place = CordonArmour.escortPlace(truck(), WAY_X, WAY_Y, p -> new int[]{p[0], 10}, 1);
        assertTrue(place[0] < 10 && place[2] == 0, "behind the truck, not ahead of it in the lane: " + place[0] + "," + place[1]);
    }

    @Test
    void anEscortOnTheLeftStaysThereWhenTheRightOpensAgain() {
        assertArrayEquals(new int[]{13, 15, -1}, CordonArmour.escortPlace(truck(), WAY_X, WAY_Y, place -> place, -1));
    }

    @Test
    void anEscortOnTheLeftCrossesOnlyWhenTheLeftCloses() {
        int[] place = CordonArmour.escortPlace(truck(), WAY_X, WAY_Y, p -> p[1] > 10 ? new int[]{p[0], 10} : p, -1);
        assertArrayEquals(new int[]{13, 7, 1}, place);
    }

    @Test
    void withNoRouteYetItsHeadingIsItsWay() {
        int[] place = CordonArmour.escortPlace(truck(), null, null, p -> new int[]{p[0], 10}, 1);
        assertTrue(place[0] < 10, "behind the truck: " + place[0] + "," + place[1]);
    }
}
