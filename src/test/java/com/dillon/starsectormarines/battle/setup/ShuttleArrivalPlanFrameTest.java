package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftRating;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** An Aeroshuttle standing in for a committed hull keeps a cradle's lift and nothing else. */
class ShuttleArrivalPlanFrameTest {

    @Test
    void aStandInForACradledBoatInheritsItsLiftRating() {
        FittedBoat cradled = new FittedBoat(ShuttleType.BUFFALO, BoatFitting.STANDARD_PLATING,
                BoatFitting.STANDARD_DRIVE, BoatFitting.LIFT_CRADLE, "buffalo-1");
        List<ShuttleAssignment> source = List.of(
                new ShuttleAssignment(cradled, 1, 6),
                new ShuttleAssignment(FittedBoat.standard(ShuttleType.BUFFALO), 1, 6));

        Airframe standIn = ShuttleArrivalPlan.frameFor(source, 0, 2, 0);

        assertEquals(LiftRating.LIGHT, standIn.liftRating());
        assertSame(ShuttleType.AEROSHUTTLE, ShuttleType.fromAirframe(standIn));
        assertEquals(LiftRating.NONE, ShuttleArrivalPlan.frameFor(source, 0, 2, 1).liftRating());
        assertSame(ShuttleType.AEROSHUTTLE, ShuttleArrivalPlan.frameFor(source, 0, 2, 5), "past the source");
    }
}
